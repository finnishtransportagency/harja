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

CREATE FUNCTION laske_tr_pituudet(tie_ INTEGER, osa_ INTEGER)
  RETURNS JSONB
AS $$
DECLARE
  tulos           JSONB;

  osan_pituus     INTEGER;
  ajoradan_tiedot RECORD;
  kaistan_tiedot  RECORD;
BEGIN
  osan_pituus = (SELECT sum(loppuetaisyys - alkuetaisyys) AS pituus
                 FROM tr_osoitteet
                 WHERE tie = tie_ AND osa = osa_
                 GROUP BY tie, osa);
  tulos = jsonb_build_object('pituus', osan_pituus, 'ajoradat', jsonb_build_array());
  FOR ajoradan_tiedot IN (SELECT
                            ajorata,
                            sum(loppuetaisyys - alkuetaisyys) AS pituus
                          FROM tr_osoitteet
                          WHERE tie = tie_ AND osa = osa_
                          GROUP BY tie, osa, ajorata)
  LOOP
    tulos = jsonb_set(tulos, ('{"ajoradat", ' || ajoradan_tiedot.ajorata || '}')::TEXT[],
                      jsonb_build_object('ajorata', ajoradan_tiedot.ajorata,
                                         'pituus', ajoradan_tiedot.pituus,
                                         'kaistat', jsonb_build_array()));
    FOR kaistan_tiedot IN (SELECT
                             kaista,
                             loppuetaisyys - alkuetaisyys AS pituus
                           FROM tr_osoitteet
                           WHERE tie = tie_ AND osa = osa_ AND ajorata = ajoradan_tiedot.ajorata)
    LOOP
      tulos = jsonb_set(tulos, ('{"ajoradat", ' || ajoradan_tiedot.ajorata || ', "kaistat", ' ||
                               kaistan_tiedot.kaista || '}')::TEXT[],
                        jsonb_build_object('kaista', kaistan_tiedot.kaista,
                                           'pituus', kaistan_tiedot.pituus));
    END LOOP;
  END LOOP;
  RETURN tulos;
END;
$$ LANGUAGE plpgsql;

CREATE MATERIALIZED VIEW tr_pituudet AS
  SELECT
    tie,
    osa,
    laske_tr_pituudet(tie, osa) AS pituudet
  FROM (SELECT DISTINCT
          tie,
          osa
        FROM tr_osoitteet) AS tien_osat;

CREATE FUNCTION paivita_tr_pituudet()
  RETURNS VOID
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW tr_pituudet;
  RETURN;
END;
$$ LANGUAGE plpgsql;