CREATE TABLE tr_osoitteet (
  id            SERIAL PRIMARY KEY,
  tie           INTEGER,
  ajorata       INTEGER,
  kaista        INTEGER,
  osa           INTEGER,
  alkuetaisyys  INTEGER,
  loppuetaisyys INTEGER,
  tietyyppi     INTEGER
);

CREATE INDEX tr_osoitteet_idx
  ON tr_osoitteet (tie, osa);

CREATE FUNCTION laske_tr_tiedot(tie_ INTEGER, osa_ INTEGER)
  RETURNS JSONB
AS $$
DECLARE
  tulos                     JSONB;
  counter                   INTEGER;
  pituus                    INTEGER;
  loppuetaisyyksien_erotus  INTEGER;

  osan_pituus               INTEGER;
  osan_alkuetaisyys         INTEGER;

  ajorata_pointer           TEXT [];
  ajorata_objekti           JSONB;
  ajorataosion_index        INTEGER;
  ajorataosio_pointer       TEXT [];
  ajorataosio               JSONB;
  edelliset_ajoradan_tiedot RECORD;
  ajoradan_tiedot           RECORD;

  kaista_pointer            TEXT [];
  kaistan_index             INTEGER;
BEGIN
  SELECT NULL
  INTO edelliset_ajoradan_tiedot;

  osan_pituus = (SELECT sum(loppuetaisyys - alkuetaisyys) AS pituus
                 FROM tr_osoitteet
                 WHERE tie = tie_ AND osa = osa_
                       -- Tiputetaan 2 ajorata pois, jottei samalta pätkältä lasketa pituutta tuplana
                       AND ajorata IN (0, 1)
                       AND kaista IN (1, 11)
                 GROUP BY tie, osa);
  osan_alkuetaisyys = (SELECT min(alkuetaisyys)
                       FROM tr_osoitteet
                       WHERE tie = tie_ AND osa = osa_
                       GROUP BY tie, osa);
  -- Asetetaan osan pituus sekä alkuetaisyys. Alkuetäisyys ei ole aina 0.
  tulos = jsonb_build_object('pituus', osan_pituus, 'alkuetaisyys', osan_alkuetaisyys, 'ajoradat', jsonb_build_array());
  FOR ajoradan_tiedot IN (SELECT
                            ajorata,
                            kaista,
                            alkuetaisyys,
                            loppuetaisyys - alkuetaisyys AS pituus
                          FROM tr_osoitteet
                          WHERE tie = tie_ AND osa = osa_
                          ORDER BY ajorata ASC, alkuetaisyys ASC)
  LOOP
    -- Elikkä ajorataobjektin sijainti "ajoradat" vektorissa ei välttämättä vastaa ajoradan numeroa. Eli ajorata 2
    -- ei välttämättä ole indeksipaikalla 2.
    SELECT NULL
    INTO ajorata_pointer;
    counter = 0;
    FOR ajorata_objekti IN (SELECT *
                            FROM jsonb_array_elements(tulos #> '{"ajoradat"}' :: TEXT [])) LOOP
      IF (ajorata_objekti #>> '{"ajorata"}' :: TEXT []) = ajoradan_tiedot.ajorata :: TEXT
      THEN
        ajorata_pointer = ('{"ajoradat", ' || counter || '}') :: TEXT [];
      END IF;
      counter = counter + 1;
    END LOOP;
    IF ajorata_pointer IS NULL
    THEN
      ajorata_pointer = ('{"ajoradat", ' || jsonb_array_length(tulos #> '{"ajoradat"}' :: TEXT []) || '}') :: TEXT [];
    END IF;
    IF (SELECT tulos #> ajorata_pointer IS NULL)
    THEN
      -- Osioita on ajoradalla vain yksi, jos se on yksi yhtenäinen ajorata. On kumminkin mahdollista, että
      -- ajorata "katkeaa" hetkeksi useammaksi ajoradaksi ja jatkuu taas yhtenä. Silloin osiota ajoradalle merkataan
      -- useampi. Esimekrikis [{:tie 1 :osa 1 :ajorata 0 :aosa 1 :aet 1 :losa 1 :let 100}
      --                       {:tie 1 :osa 1 :ajorata 1 :aosa 1 :aet 100 :losa 1 :let 200}
      --                       {:tie 1 :osa 1 :ajorata 2 :aosa 1 :aet 100 :losa 1 :let 200}
      --                       {:tie 1 :osa 1 :ajorata 0 :aosa 1 :aet 200 :losa 1 :let 300}]
      -- pätkä sisältää kaksi osaa ajoradalle 0.
      tulos = jsonb_set(tulos, ajorata_pointer, jsonb_build_object('ajorata', ajoradan_tiedot.ajorata,
                                                                   'osiot', jsonb_build_array()));
    END IF;
    IF (tie_ = 1 AND osa_ = 4)
    THEN
      RAISE NOTICE 'TULOS: %', tulos;
      RAISE NOTICE 'AJORATA POINTER: %', ajorata_pointer;
      RAISE NOTICE 'AJORADAN TIEDOT: %', ajoradan_tiedot;
    END IF;

    ajorataosion_index = jsonb_array_length(tulos #> (ajorata_pointer || '{"osiot"}' :: TEXT []));

    -- Jos edellisellä kierroksella vaihtui vain kaista, mutta ajorata pysyy samana ja jatkuu samasta kohdasta
    -- mihin edellinen jäi, niin edellisen kierroksen ajoratatietoja tulee päivittää.

    IF edelliset_ajoradan_tiedot IS NOT NULL
    THEN
      CASE
      -- Jos sama ajorata kuin edellinen ja jatkuu heti edellisen jälkeen, liitetän tämä edelliseen tietoon
        WHEN (edelliset_ajoradan_tiedot.ajorata = ajoradan_tiedot.ajorata AND
              (edelliset_ajoradan_tiedot.alkuetaisyys + edelliset_ajoradan_tiedot.pituus) =
              ajoradan_tiedot.alkuetaisyys)
        THEN
          ajorataosion_index = ajorataosion_index - 1;
          ajorataosio_pointer = ajorata_pointer || ('{"osiot", ' || ajorataosion_index || '}') :: TEXT [];
          pituus = (tulos #>> (ajorataosio_pointer || '{pituus}' :: TEXT [])) :: INTEGER + ajoradan_tiedot.pituus;
          ajorataosio = jsonb_build_object('pituus', pituus,
                                           'alkuetaisyys', (tulos #>> (ajorataosio_pointer ||
                                                                       '{"alkuetaisyys"}' :: TEXT [])) :: INTEGER,
                                           'kaistat', tulos #> (ajorataosio_pointer || '{"kaistat"}' :: TEXT []));
          -- Jos sama ajorata kuin edellinen ja alkuetaisyys on pienempi kuin edellisen loppuetäisyys, on kaista vaihtunut.
          -- Tällöin ei tehdä vielä mitään, sillä kaista lisätään myöhemmin.
        WHEN (edelliset_ajoradan_tiedot.ajorata = ajoradan_tiedot.ajorata AND
              (edelliset_ajoradan_tiedot.alkuetaisyys + edelliset_ajoradan_tiedot.pituus) >
              ajoradan_tiedot.alkuetaisyys)
        THEN
          ajorataosion_index = ajorataosion_index - 1;
          loppuetaisyyksien_erotus = (ajoradan_tiedot.alkuetaisyys + ajoradan_tiedot.pituus) -
                                     (edelliset_ajoradan_tiedot.alkuetaisyys + edelliset_ajoradan_tiedot.pituus);
          ajorataosio_pointer = ajorata_pointer || ('{"osiot", ' || ajorataosion_index || '}') :: TEXT [];
          -- Jos tämän loppuetäisyys on pidempi kuin aikaisemman loppuetäisyys
          IF loppuetaisyyksien_erotus > 0
          THEN
            pituus = (tulos #>> (ajorataosio_pointer || '{pituus}' :: TEXT [])) :: INTEGER + loppuetaisyyksien_erotus;
          ELSE
            pituus = (tulos #>> (ajorataosio_pointer || '{pituus}' :: TEXT [])) :: INTEGER;
          END IF;
          ajorataosio = jsonb_build_object('pituus', pituus,
                                           'alkuetaisyys', (tulos #>> (ajorataosio_pointer ||
                                                                       '{"alkuetaisyys"}' :: TEXT [])) :: INTEGER,
                                           'kaistat', tulos #> (ajorataosio_pointer || '{"kaistat"}' :: TEXT []));
      ELSE
        ajorataosio = jsonb_build_object('pituus', ajoradan_tiedot.pituus,
                                         'alkuetaisyys', ajoradan_tiedot.alkuetaisyys,
                                         'kaistat', jsonb_build_array());
      END CASE;
    ELSE
      ajorataosio = jsonb_build_object('pituus', ajoradan_tiedot.pituus,
                                       'alkuetaisyys', ajoradan_tiedot.alkuetaisyys,
                                       'kaistat', jsonb_build_array());
    END IF;
    tulos = jsonb_set(tulos,
                      ajorata_pointer || ('{"osiot",' || ajorataosion_index || '}') :: TEXT [],
                      ajorataosio);
    -- Aseta kaistan tiedot
    kaista_pointer = ajorata_pointer || ('{"osiot", ' || ajorataosion_index || ', "kaistat"}') :: TEXT [];
    kaistan_index = jsonb_array_length(tulos #> kaista_pointer);
    tulos = jsonb_set(tulos,
                      kaista_pointer || ('{' || kaistan_index || '}') :: TEXT [],
                      jsonb_build_object('kaista', ajoradan_tiedot.kaista,
                                         'pituus', ajoradan_tiedot.pituus,
                                         'alkuetaisyys', ajoradan_tiedot.alkuetaisyys));
    edelliset_ajoradan_tiedot = ajoradan_tiedot;
  END LOOP;
  RETURN tulos;
END;
$$ LANGUAGE plpgsql;

CREATE MATERIALIZED VIEW tr_tiedot AS
  SELECT
    tie,
    osa,
    laske_tr_tiedot(tie, osa) AS pituudet
  FROM (SELECT DISTINCT
          tie,
          osa
        FROM tr_osoitteet) AS tien_osat;

CREATE INDEX tr_tiedot_idx
  ON tr_tiedot (tie, osa);

CREATE FUNCTION paivita_tr_tiedot()
  RETURNS VOID
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW tr_tiedot;
  RETURN;
END;
$$ LANGUAGE plpgsql;