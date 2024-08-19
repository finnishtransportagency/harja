CREATE TYPE pohjavesialueen_osuus AS
(
    tunnus VARCHAR(16),
    osuus  FLOAT
);

CREATE TYPE rajoitusalueen_osuus AS
(
    rajoitusalue INTEGER,
    osuus        FLOAT
);

CREATE FUNCTION lahin_piste_suolattavalla_tiella(piste point)
    RETURNS POINT AS
$$
BEGIN
    -- Talvihoitoluokkia 9, 10 ja 11 ei pitäisi suolata. Täten hoitoluokka-taulua käytten voidaan hakea lähin piste
    -- tieltä, jolle suolausta tehdään.
    RETURN (SELECT st_closestpoint(geometria, piste::geometry)::POINT
            FROM hoitoluokka
            WHERE hoitoluokka NOT IN (9, 10, 11)
              AND tietolajitunniste = 'talvihoito'
              AND st_dwithin(geometria, piste::geometry, 25)
            ORDER BY st_distance84(geometria, piste::geometry)
            LIMIT 1);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pistevalin_pohjavesialueet(piste1 point, piste2 point)
    RETURNS SETOF pohjavesialueen_osuus AS
$$
DECLARE
    pa            pohjavesialue;
    tieosoitevali tr_osoite;
    osuus         FLOAT;
BEGIN
    -- Varmistetaan että haetaan piste tielä, jota suolataan. Tällä varmistetaan, ettei saada sellaisen tien geometriaa,
    -- jota ei suolata. Tällä varmistetaan, ettei virheellisesti jätetä suolattua pohjavesialuetta merkitsemättä
    -- vaikka gps-pisteet osuisivat pyörätielle.
    SELECT lahin_piste_suolattavalla_tiella(piste1) INTO piste1;
    SELECT lahin_piste_suolattavalla_tiella(piste2) INTO piste2;

    IF (piste1 IS NULL OR piste2 IS NULL) THEN
        RETURN;
    END IF;

    SELECT * FROM tierekisteriosoite_pisteille(piste1::geometry, piste2::geometry, 1) INTO tieosoitevali;

    FOR pa IN
        SELECT *
        FROM pohjavesialue
        WHERE pohjavesialue.tr_numero = tieosoitevali.tie
          AND st_dwithin(tieosoitevali.geometria, pohjavesialue.alue, 20)
        LOOP
            -- Halutaan tietää, kuinka iso osuus tieosoitevälistä osuu pohjavesialueelle.
            SELECT st_length(st_intersection(st_buffer(pa.alue, 1, 'endcap=flat'), tieosoitevali.geometria)) /
                   st_length(tieosoitevali.geometria)

            INTO osuus;
            RETURN NEXT (pa.tunnus, osuus)::pohjavesialueen_osuus;
        END LOOP;
    RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pistevalin_rajoitusalueet(piste1 point, piste2 point, urakka_id_ INTEGER)
    RETURNS SETOF rajoitusalueen_osuus AS
$$
DECLARE
    ra            rajoitusalue;
    tieosoitevali tr_osoite;
    osuus         FLOAT;
BEGIN
    -- Varmistetaan että haetaan piste tielä, jota suolataan. Tällä varmistetaan, ettei saada rajoitusaluetta tien geometriaa,
    -- jota ei suolata. Tällä varmistetaan, ettei virheellisesti jätetä suolattua rajoitusaluetta merkitsemättä
    -- vaikka gps-pisteet osuisivat pyörätielle.
    SELECT lahin_piste_suolattavalla_tiella(piste1) INTO piste1;
    SELECT lahin_piste_suolattavalla_tiella(piste2) INTO piste2;

    IF (piste1 IS NULL OR piste2 IS NULL) THEN
        RETURN;
    END IF;

    SELECT * FROM tierekisteriosoite_pisteille(piste1::geometry, piste2::geometry, 1) INTO tieosoitevali;

    FOR ra IN
        SELECT *
        FROM rajoitusalue
        WHERE (rajoitusalue.tierekisteriosoite).tie = tieosoitevali.tie
          AND st_dwithin(tieosoitevali.geometria, rajoitusalue.sijainti, 20)
          AND rajoitusalue.urakka_id = urakka_id_
          AND rajoitusalue.poistettu = FALSE
        LOOP
            SELECT st_length(st_intersection(st_buffer(ra.sijainti, 20, 'endcap=flat'), tieosoitevali.geometria)) /
                   st_length(tieosoitevali.geometria)
            INTO osuus;
            RETURN NEXT (ra.id, osuus)::rajoitusalueen_osuus;
        END LOOP;
    RETURN;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS
$$
DECLARE
    m                     reittipiste_materiaali;
    rp                    reittipistedata;
    suolamateriaalikoodit INTEGER[];
    edellinen_rp          reittipistedata;
    pvo                   pohjavesialueen_osuus;
    ra                    rajoitusalueen_osuus;
BEGIN
    SELECT ARRAY_AGG(id)
    FROM materiaalikoodi
    WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'formiaatti')
    INTO suolamateriaalikoodit;

    IF (TG_OP = 'UPDATE' OR TG_OP = 'DELETE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma = OLD.toteuma;
    END IF;

    IF (TG_OP != 'DELETE') THEN
        FOREACH rp IN ARRAY NEW.reittipisteet
            LOOP
                FOREACH m IN ARRAY rp.materiaalit
                    LOOP
                        IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                            -- Muutos edelliseen versioon: suolatoteuma_reittipiste tauluun laitetaan kahden reittipisteen välinen toteuma,
                            -- josta lasketaan osuudet jotka osuvat pohjavesi- tai rajoitusalueille.
                            IF edellinen_rp IS DISTINCT FROM NULL THEN
                                FOR pvo IN (SELECT tunnus, SUM(osuus)
                                            FROM pistevalin_pohjavesialueet(edellinen_rp.sijainti, rp.sijainti)
                                            GROUP BY tunnus)
                                    LOOP
                                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi,
                                                                              maara, pohjavesialue, rajoitusalue_id)
                                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                m.maara * pvo.osuus, pvo.tunnus,
                                                NULL);
                                    END LOOP;

                                FOR ra IN (SELECT rajoitusalue, SUM(osuus)
                                           FROM pistevalin_rajoitusalueet(edellinen_rp.sijainti, rp.sijainti,
                                                                          (SELECT urakka FROM toteuma WHERE id = new.toteuma))
                                           GROUP BY rajoitusalue)
                                    LOOP
                                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi,
                                                                              maara, pohjavesialue, rajoitusalue_id)
                                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                m.maara * ra.osuus, NULL,
                                                ra.rajoitusalue);
                                    END LOOP;
                            END IF;
                        END IF;
                    END LOOP;
                edellinen_rp := rp;
            END LOOP;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP FUNCTION pisteen_rajoitusalue(piste POINT, threshold INTEGER, toteuma_id INTEGER);
DROP FUNCTION pisteen_pohjavesialue(piste POINT, threshold INTEGER);
