CREATE TYPE pohjavesialueen_yhdistamisen_valiarvo AS (edellinen_pva pohjavesialue, yhdistetyt_pohjavesialueet pohjavesialue[]);

CREATE OR REPLACE FUNCTION pohjavesialue_yhdistaminen (pva_y_v pohjavesialueen_yhdistamisen_valiarvo, pva pohjavesialue)
    RETURNS pohjavesialueen_yhdistamisen_valiarvo AS $$
DECLARE
    yhdistetyt_pohjavesialueet pohjavesialue[];
    edellinen_pohjavesialue pohjavesialue;
    edellinen_yhdistetty_pohjavesialue pohjavesialue;
    tyostettava_pohjavesialue pohjavesialue;
    edellisen_pohjavesialueen_loppuetaisyys INTEGER;
    edellisen_pohjavesialueen_seuraava_osa INTEGER;
BEGIN
    yhdistetyt_pohjavesialueet = (pva_y_v).yhdistetyt_pohjavesialueet;
    edellinen_pohjavesialue = (pva_y_v).edellinen_pva;
    edellinen_yhdistetty_pohjavesialue = yhdistetyt_pohjavesialueet[array_length(yhdistetyt_pohjavesialueet, 1)];
    tyostettava_pohjavesialue = edellinen_yhdistetty_pohjavesialue;

    -- Jos on sama tien pätkä mutta eri ajorata, niin yhdistetään vain geometria
    IF (edellinen_pohjavesialue.tr_alkuosa = pva.tr_alkuosa AND
        edellinen_pohjavesialue.tr_alkuetaisyys = pva.tr_alkuetaisyys AND
        edellinen_pohjavesialue.tr_loppuosa = pva.tr_loppuosa AND
        edellinen_pohjavesialue.tr_loppuetaisyys = pva.tr_loppuetaisyys) THEN

        tyostettava_pohjavesialue.alue = ST_LineMerge(ST_CollectionExtract(ST_Collect(tyostettava_pohjavesialue.alue, pva.alue), 2));
        RETURN ROW(pva, array_append(yhdistetyt_pohjavesialueet[1:(array_length(yhdistetyt_pohjavesialueet, 1) - 1)],
                                     tyostettava_pohjavesialue))::pohjavesialueen_yhdistamisen_valiarvo;
    END IF;

    -- Tarkastetaan, onko sama pohjavesialueen osa, jota tulee jatkaa
    IF (edellinen_yhdistetty_pohjavesialue.tr_loppuosa = pva.tr_alkuosa AND
        edellinen_yhdistetty_pohjavesialue.tunnus = pva.tunnus) THEN
        -- Jos tämä on tosi, niin samaa pohjavesialuetta jatketaan
        IF (edellinen_yhdistetty_pohjavesialue.tr_loppuetaisyys = pva.tr_alkuetaisyys) THEN
            tyostettava_pohjavesialue.tr_loppuetaisyys = pva.tr_loppuetaisyys;
            tyostettava_pohjavesialue.alue = ST_LineMerge(ST_CollectionExtract(ST_Collect(tyostettava_pohjavesialue.alue, pva.alue), 2));
            RETURN ROW(pva, array_append(yhdistetyt_pohjavesialueet[1:(array_length(yhdistetyt_pohjavesialueet, 1) - 1)],
                                         tyostettava_pohjavesialue))::pohjavesialueen_yhdistamisen_valiarvo;
        -- Jos ei ollut tosi, tarkoittaa, että samassa pohjavesialueessa on "aukko"
        ELSE
            RETURN ROW(pva, array_append(yhdistetyt_pohjavesialueet, pva))::pohjavesialueen_yhdistamisen_valiarvo;
        END IF;
    ELSE
        edellisen_pohjavesialueen_loppuetaisyys = (SELECT (pituudet ->> 'pituus')::INTEGER + (pituudet ->> 'tr-alkuetaisyys')::INTEGER
                                                   FROM tr_tiedot
                                                   WHERE "tr-numero"=edellinen_yhdistetty_pohjavesialue.tr_numero AND
                                                           "tr-osa"=edellinen_yhdistetty_pohjavesialue.tr_loppuosa);
        edellisen_pohjavesialueen_seuraava_osa = (SELECT "tr-osa"
                                                  FROM tr_osoitteet
                                                  WHERE "tr-numero"=edellinen_yhdistetty_pohjavesialue.tr_numero AND
                                                          "tr-osa">edellinen_yhdistetty_pohjavesialue.tr_loppuosa
                                                  ORDER BY "tr-osa" ASC
                                                  LIMIT 1);
        -- Mahdollista, että pohjavesialue on sama, mutta osa vain vaihtuu. Tarkastetaan
        IF (edellinen_yhdistetty_pohjavesialue.tr_loppuetaisyys = edellisen_pohjavesialueen_loppuetaisyys AND
            pva.tr_alkuetaisyys = 0 AND
            edellisen_pohjavesialueen_seuraava_osa = pva.tr_alkuosa AND
            edellinen_yhdistetty_pohjavesialue.tunnus = pva.tunnus) THEN
            tyostettava_pohjavesialue.tr_loppuetaisyys = pva.tr_loppuetaisyys;
            tyostettava_pohjavesialue.tr_loppuosa = pva.tr_loppuosa;
            tyostettava_pohjavesialue.alue = ST_LineMerge(ST_CollectionExtract(ST_Collect(tyostettava_pohjavesialue.alue, pva.alue), 2));
            RETURN ROW(pva, array_append(yhdistetyt_pohjavesialueet[1:(array_length(yhdistetyt_pohjavesialueet, 1) - 1)],
                                         tyostettava_pohjavesialue))::pohjavesialueen_yhdistamisen_valiarvo;
        ELSE
            RETURN ROW(pva, array_append(yhdistetyt_pohjavesialueet, pva))::pohjavesialueen_yhdistamisen_valiarvo;
        END IF;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pohjavesialueen_yhdistamisen_tulos(viimeinen_arvo pohjavesialueen_yhdistamisen_valiarvo)
    RETURNS pohjavesialue[] AS
$$
DECLARE
    pva pohjavesialue[];
BEGIN
    pva = (viimeinen_arvo).yhdistetyt_pohjavesialueet;
    RETURN pva;
END;
$$ LANGUAGE plpgsql;

CREATE AGGREGATE yhtenaiset_pohjavesialueet (pohjavesialue) (
    SFUNC = pohjavesialue_yhdistaminen,
    FINALFUNC = pohjavesialueen_yhdistamisen_tulos,
    STYPE = pohjavesialueen_yhdistamisen_valiarvo,
    INITCOND = '(,{})'
    );

CREATE OR REPLACE FUNCTION pohjavesialue_factory(kentta_arvo jsonb)
    RETURNS pohjavesialue AS $$
DECLARE
    pva pohjavesialue%ROWTYPE;
BEGIN
    pva = '(,,,,,,,,,,,,,,,,,)'::pohjavesialue;
    IF kentta_arvo ? 'id'
    THEN
        pva.id = (kentta_arvo ->> 'id')::INTEGER;
    END IF;
    IF kentta_arvo ? 'nimi'
    THEN
        pva.nimi = (kentta_arvo ->> 'nimi')::VARCHAR(128);
    END IF;
    IF kentta_arvo ? 'tunnus'
    THEN
        pva.tunnus = (kentta_arvo ->> 'tunnus')::VARCHAR(16);
    END IF;
    IF kentta_arvo ? 'alue'
    THEN
        pva.alue = (kentta_arvo ->> 'alue')::GEOMETRY;
    END IF;
    IF kentta_arvo ? 'muuttunut_pvm'
    THEN
        pva.muuttunut_pvm = (kentta_arvo ->> 'muuttunut_pvm')::TIMESTAMP;
    END IF;
    IF kentta_arvo ? 'suolarajoitus'
    THEN
        pva.suolarajoitus = (kentta_arvo ->> 'suolarajoitus')::BOOLEAN;
    END IF;
    IF kentta_arvo ? 'tr_numero'
    THEN
        pva.tr_numero = (kentta_arvo ->> 'tr_numero')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_alkuosa'
    THEN
        pva.tr_alkuosa = (kentta_arvo ->> 'tr_alkuosa')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_alkuetaisyys'
    THEN
        pva.tr_alkuetaisyys = (kentta_arvo ->> 'tr_alkuetaisyys')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_loppuosa'
    THEN
        pva.tr_loppuosa = (kentta_arvo ->> 'tr_loppuosa')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_loppuetaisyys'
    THEN
        pva.tr_loppuetaisyys = (kentta_arvo ->> 'tr_loppuetaisyys')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_ajorata'
    THEN
        pva.tr_ajorata = (kentta_arvo ->> 'tr_ajorata')::INTEGER;
    END IF;
    IF kentta_arvo ? 'tr_kaista'
    THEN
        pva.tr_kaista = (kentta_arvo ->> 'tr_kaista')::INTEGER;
    END IF;
    IF kentta_arvo ? 'luotu'
    THEN
        pva.luotu = (kentta_arvo ->> 'luotu')::TIMESTAMP;
    END IF;
    IF kentta_arvo ? 'luoja'
    THEN
        pva.luoja = (kentta_arvo ->> 'luoja')::INTEGER;
    END IF;
    IF kentta_arvo ? 'muokattu'
    THEN
        pva.muokattu = (kentta_arvo ->> 'muokattu')::TIMESTAMP;
    END IF;
    IF kentta_arvo ? 'muokkaaja'
    THEN
        pva.muokkaaja = (kentta_arvo ->> 'muokkaaja')::INTEGER;
    END IF;
    IF kentta_arvo ? 'aineisto_id'
    THEN
        pva.aineisto_id = (kentta_arvo ->> 'aineisto_id')::TEXT;
    END IF;
    RETURN pva;
END;
$$ LANGUAGE plpgsql;

DROP MATERIALIZED VIEW pohjavesialue_kooste;
CREATE MATERIALIZED VIEW pohjavesialue_kooste AS (
    SELECT (yhtenainen_pva).nimi AS nimi,
           (yhtenainen_pva).tunnus AS tunnus,
           (yhtenainen_pva).alue AS alue,
           (yhtenainen_pva).suolarajoitus AS suolarajoitus,
           pva_ts.talvisuolaraja AS talvisuolaraja,
           st_length((yhtenainen_pva).alue) AS pituus,
           (yhtenainen_pva).tr_numero AS tie,
           (yhtenainen_pva).tr_alkuosa AS alkuosa,
           (yhtenainen_pva).tr_alkuetaisyys AS alkuet,
           (yhtenainen_pva).tr_loppuosa AS loppuosa,
           (yhtenainen_pva).tr_loppuetaisyys AS loppuet
    FROM (SELECT unnest(ypva) AS yhtenainen_pva
          FROM (SELECT yhtenaiset_pohjavesialueet(pva) AS ypva
                FROM (SELECT pohjavesialue_factory((('{"tr_numero": ' || tr_numero || '}')::jsonb ||
                                                    ('{"tr_alkuosa": ' || tr_alkuosa || '}')::jsonb ||
                                                    ('{"tr_alkuetaisyys": ' || tr_alkuetaisyys || '}')::jsonb ||
                                                    ('{"tr_loppuosa": ' || tr_loppuosa || '}')::jsonb ||
                                                    ('{"tr_loppuetaisyys": ' || tr_loppuetaisyys || '}')::jsonb ||
                                                    ('{"tunnus": "' || tunnus || '"}')::jsonb ||
                                                    ('{"nimi": "' || nimi || '"}')::jsonb ||
                                                    ('{"suolarajoitus": "' || suolarajoitus || '"}')::jsonb ||
                                                    ('{"alue": "' || alue::TEXT || '"}')::jsonb)) AS pva,
                             tr_numero
                      FROM pohjavesialue
                      ORDER BY tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_ajorata) AS pohjavesialueet
                GROUP BY tr_numero) AS tien_osat_taulu) AS yhtenaiset_pvat
    LEFT JOIN pohjavesialue_talvisuola pva_ts ON (pva_ts.pohjavesialue = (yhtenainen_pva).tunnus AND
                                                 pva_ts.tie = (yhtenainen_pva).tr_numero)
);

-- Suolatoteuman reittipisteessä ei tarvitse säilyttää tietoa vesiväylän tr osoitteesta
ALTER TABLE suolatoteuma_reittipiste DROP COLUMN tie;
ALTER TABLE suolatoteuma_reittipiste DROP COLUMN alkuosa;
ALTER TABLE suolatoteuma_reittipiste DROP COLUMN alkuet;
ALTER TABLE suolatoteuma_reittipiste DROP COLUMN loppuosa;
ALTER TABLE suolatoteuma_reittipiste DROP COLUMN loppuet;

UPDATE suolatoteuma_reittipiste
SET pohjavesialue=pisteen_pohjavesialue(sijainti, 50)
WHERE pohjavesialue IS NULL;

-- Tätä funktiota ei käytetä missään
DROP FUNCTION konvertoi_urakan_suolatoteumat(urakkaid INTEGER, threshold INTEGER);

DROP FUNCTION pisteen_pohjavesialue_ja_tie(piste POINT, threshold INTEGER);
DROP TYPE pisteen_pohjavesialue_tie;


CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS $$
DECLARE
    m reittipiste_materiaali;
    rp reittipistedata;
    suolamateriaalikoodit INTEGER[];
    pohjavesialue_tunnus VARCHAR;
BEGIN
    SELECT array_agg(id) FROM materiaalikoodi
    WHERE materiaalityyppi='talvisuola' INTO suolamateriaalikoodit;

    IF (TG_OP = 'UPDATE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma=NEW.toteuma;
    END IF;

    FOREACH rp IN ARRAY NEW.reittipisteet LOOP
        FOREACH m IN ARRAY rp.materiaalit LOOP
            IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
                pohjavesialue_tunnus := pisteen_pohjavesialue(rp.sijainti, 50);
                INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue)
                VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tunnus);
            END IF;
        END LOOP;
    END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE INDEX ON pohjavesialue_kooste(tunnus);
CREATE INDEX ON suolatoteuma_reittipiste(pohjavesialue);

CREATE MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat AS
    SELECT t.urakka                             AS "urakka-id",
           date_trunc('day', rp.aika)           AS paiva,
           SUM(rp.maara)                        AS yhteensa,
           pva_k.tie,
           pva_k.alkuosa,
           pva_k.alkuet,
           pva_k.loppuosa,
           pva_k.loppuet,
           (array_agg(pva_k.pituus))[1]         AS pituus, -- Tuossa pituudessa on vain yksi arvo
           (array_agg(pva_k.tunnus))[1]         AS tunnus,
           (array_agg(pva_k.talvisuolaraja))[1] AS kayttoraja
    FROM suolatoteuma_reittipiste rp
             LEFT JOIN toteuma t ON t.id = rp.toteuma
             LEFT JOIN pohjavesialue_kooste pva_k ON (pva_k.tunnus = rp.pohjavesialue)
    WHERE rp.pohjavesialue IS NOT NULL
    GROUP BY t.urakka, paiva, pva_k.tie, pva_k.alkuosa, pva_k.alkuet, pva_k.loppuosa, pva_k.loppuet;


CREATE OR REPLACE FUNCTION paivita_raportti_cachet()
  RETURNS VOID
SECURITY DEFINER
AS $$
BEGIN
  REFRESH MATERIALIZED VIEW raportti_toteutuneet_materiaalit;
  REFRESH MATERIALIZED VIEW raportti_pohjavesialueiden_suolatoteumat;
  RETURN;
END;
$$ LANGUAGE plpgsql;