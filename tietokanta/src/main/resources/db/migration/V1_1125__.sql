CREATE TYPE pohjavesialueen_osuus AS
(
    tunnus VARCHAR(16),
    pituus FLOAT,
    osuus  FLOAT
);

CREATE TYPE rajoitusalueen_osuus AS
(
    rajoitusalue INTEGER,
    pituus       FLOAT,
    osuus        FLOAT
);

CREATE OR REPLACE FUNCTION pistevalin_pohjavesialueet(piste1 point, piste2 point)
    RETURNS SETOF pohjavesialueen_osuus AS
$$
DECLARE
    pa            pohjavesialue;
    tieosoitevali tr_osoite;
    pituus        FLOAT;
    osuus         FLOAT;
BEGIN
    IF (piste1 IS NULL OR piste2 IS NULL) THEN
        RETURN;
    END IF;

    SELECT * FROM tierekisteriosoite_pisteille(piste1::geometry, piste2::geometry, 1) INTO tieosoitevali;

    FOR pa IN
        SELECT *
        FROM pohjavesialue
        WHERE pohjavesialue.tr_numero = tieosoitevali.tie
          AND st_dwithin(tieosoitevali.geometria, pohjavesialue.alue, 1)

        LOOP

            RAISE NOTICE 'pohjavesialueen leikattu geom %', st_asgeojson(st_intersection(
                st_buffer(pa.alue, 1, 'endcap=flat'), tieosoitevali.geometria));
            RAISE NOTICE '%', (st_length(st_intersection(st_buffer(pa.alue, 1, 'endcap=flat'),
                                                         tieosoitevali.geometria)));
            RAISE NOTICE '%', (st_length(tieosoitevali.geometria));
            SELECT st_length(pa.alue) INTO pituus;
            /*
             Halutaan tietää, kuinka iso osuus tieosoitevälistä osuu pohjavesialueelle.
             */
            SELECT st_length(st_intersection(st_buffer(pa.alue, 1, 'endcap=flat'), tieosoitevali.geometria)) /
                   st_length(tieosoitevali.geometria)

            INTO osuus;
            RETURN NEXT (pa.tunnus, pituus, osuus)::pohjavesialueen_osuus;
        END LOOP;
    RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pistevalin_rajoitusalueet(piste1 point, piste2 point, urakka_id_ INTEGER)
    RETURNS SETOF rajoitusalueen_osuus AS
$$
DECLARE
    ra            rajoitusalue;
    tieosoitevali tr_osoite;
    pituus        FLOAT;
    osuus         FLOAT;
BEGIN
    IF (piste1 IS NULL OR piste2 IS NULL) THEN
        RETURN;
    END IF;

    SELECT * FROM tierekisteriosoite_pisteille(piste1::geometry, piste2::geometry, 1) INTO tieosoitevali;

    RAISE NOTICE '101';
    FOR ra IN
        SELECT *
        FROM rajoitusalue
        WHERE (rajoitusalue.tierekisteriosoite).tie = tieosoitevali.tie
          AND st_dwithin(tieosoitevali.geometria, rajoitusalue.sijainti, 1)
          AND rajoitusalue.urakka_id = urakka_id_

        LOOP
            RAISE NOTICE '110';
            SELECT st_length(ra.sijainti) INTO pituus;

            RAISE NOTICE 'rajoitusalueen leikattu geom %', st_asgeojson(st_intersection(
                st_buffer(ra.sijainti, 1, 'endcap=flat'), tieosoitevali.geometria));
            RAISE NOTICE '%', (st_length(st_intersection(st_buffer(ra.sijainti, 1, 'endcap=flat'),
                                                         tieosoitevali.geometria)));
            RAISE NOTICE '%', (st_length(tieosoitevali.geometria));

            SELECT st_length(st_intersection(st_buffer(ra.sijainti, 1, 'endcap=flat'), tieosoitevali.geometria)) /
                   st_length(tieosoitevali.geometria)
            INTO osuus;
            RETURN NEXT (ra.id, pituus, osuus)::rajoitusalueen_osuus;
        END LOOP;
    RETURN;
END;
$$ LANGUAGE plpgsql;


-- Otetaan triggerissä huomioon myös poistettava reittipiste.
-- Käytännössä toteuman_reittipisteet ei koskaan päivitetä, toteuman päivittyessä vanhat reittipisteet poistetaan
-- ja uudet luodaan tilalle.
CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS
$$
DECLARE
    m                     reittipiste_materiaali;
    rp                    reittipistedata;
    suolamateriaalikoodit INTEGER[];
    pohjavesialue_tunnus  VARCHAR;
    rajoitusalue_id       INTEGER;
    edellinen_rp          reittipistedata;
    pvo                   pohjavesialueen_osuus;
    ra                    rajoitusalueen_osuus;
BEGIN
    SELECT ARRAY_AGG(id)
    FROM materiaalikoodi
    WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'formiaatti')
    INTO suolamateriaalikoodit;

    -- Muutos edelliseen versioon: Lisätty DELETE-operaation tarkistus
    IF (TG_OP = 'UPDATE' OR TG_OP = 'DELETE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma = OLD.toteuma;
    END IF;

    -- Muutos edelliseen versioon: Lisätty DELETE-operaation tarkistus
    IF (TG_OP != 'DELETE') THEN
        FOREACH rp IN ARRAY NEW.reittipisteet
            LOOP
                FOREACH m IN ARRAY rp.materiaalit
                    LOOP
                        IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN

                            -- Muutos edelliseen versioon: suolatoteuma_reittipiste tauluun laitetaan kahden reittipisteen välinen toteuma,
                            -- josta lasketaan osuudet jotka osuvat pohjavesi- tai rajoitusalueille.

                            raise notice 'test %', edellinen_rp;

                            IF edellinen_rp IS distinct from NULL THEN
                                raise notice 'test2';
                                FOR pvo IN (SELECT tunnus, SUM(pituus), SUM(osuus)
                                            FROM pistevalin_pohjavesialueet(edellinen_rp.sijainti, rp.sijainti)
                                            GROUP BY tunnus)
                                    LOOP
                                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi,
                                                                              maara,
                                                                              pohjavesialue, rajoitusalue_id)
                                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                m.maara * pvo.osuus, pvo.tunnus,
                                                NULL);
                                    END LOOP;

                                FOR ra IN (SELECT rajoitusalue, SUM(pituus), SUM(osuus)
                                           FROM pistevalin_rajoitusalueet(edellinen_rp.sijainti, rp.sijainti,
                                                                          (SELECT urakka FROM toteuma WHERE id = new.toteuma))
                                           GROUP BY rajoitusalue)
                                    LOOP
                                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi,
                                                                              maara,
                                                                              pohjavesialue, rajoitusalue_id)
                                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                m.maara * ra.osuus, NULL,
                                                ra.rajoitusalue);
                                    END LOOP;
                            END IF;
                            edellinen_rp := rp;
                        END IF;
                    END LOOP;
            END LOOP;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS toteuman_reittipisteet_trigger ON toteuman_reittipisteet;

-- Muutos aiempaan: Trigger laukeaa myös deletestä, ennen vain insertistä ja updatesta.
CREATE TRIGGER toteuman_reittipisteet_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON toteuman_reittipisteet
    FOR EACH ROW
EXECUTE PROCEDURE toteuman_reittipisteet_trigger_fn();
