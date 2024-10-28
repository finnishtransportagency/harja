-- Suolatoteuma_reittipisteet taulun sisällön päivitys vie kauan. Näillä triggereillä pyritään optimoimaan sitä.
CREATE INDEX pohjavesialue_tie_idx ON pohjavesialue (tr_numero);
CREATE INDEX rajoitusalue_urakka_poistettu_idx ON rajoitusalue (urakka_id, poistettu);

-- Suolatoteumareittipiste taulusta puuttuu sellaiset rivit, joiden piste päättyy juuri rajoitusalueen/pohjavesialueen reunalle.
-- Eli jos piste on juuri ja juuri reunalla, niin se on tunnistettu olevan rajoitusalueella, mutta määräksi tulee tosi vähäinen arvo.
-- Ja se loppu, mikä ei kohdistunut rajoitus/pohjavesialueelle menetettiin kokonaan.
-- Tämän seurauksena toteuma_reittipiste taulun määrä yhteenveto ei täsmännyt suolatoteuma_reittipiste -taulun määräyhteenvetoon.
-- Sieltä aina vähän puuttui, koska siitä yhdestä pätkästä otettiin vain osa mukaan.
-- Tässä on korjaukset siihen
CREATE TYPE suolausalueen_osuus AS
(
    tyyppi VARCHAR, -- rajoitusalue, pohjavesialue, muu
    rajoitusalue_id INTEGER,
    pohjavesialue_tunnus  VARCHAR,
    osuus        FLOAT
);

CREATE OR REPLACE FUNCTION pistevalin_suolausalueet(piste1 POINT, piste2 POINT, urakka_id_ INTEGER)
    RETURNS SETOF suolausalueen_osuus AS
$$
DECLARE
    ra            rajoitusalue;
    pa            pohjavesialue;
    tieosoitevali tr_osoite;
    osuus         FLOAT;
    jaljella_osuutta FLOAT;
    piste1_        POINT;
    piste2_        POINT;
BEGIN
    -- Varmistetaan että haetaan piste tielä, jota suolataan. Tällä varmistetaan, ettei saada rajoitusaluetta tien geometriaa,
    -- jota ei suolata. Tällä varmistetaan, ettei virheellisesti jätetä suolattua rajoitusaluetta merkitsemättä
    -- vaikka gps-pisteet osuisivat pyörätielle.
    SELECT lahin_piste_suolattavalla_tiella(piste1) INTO piste1_;
    SELECT lahin_piste_suolattavalla_tiella(piste2) INTO piste2_;
    jaljella_osuutta := 1;

    IF (piste1_ IS NULL OR piste2_ IS NULL) THEN
        RETURN;
    END IF;

    SELECT * FROM yrita_tierekisteriosoite_pisteille2(piste1_::geometry, piste2_::geometry, 1) INTO tieosoitevali;

    IF tieosoitevali IS DISTINCT FROM NULL THEN
        -- Ensin rajoitusalueet
        FOR ra IN
            SELECT *
              FROM rajoitusalue
             WHERE (rajoitusalue.tierekisteriosoite).tie = tieosoitevali.tie
               AND st_dwithin(tieosoitevali.geometria, rajoitusalue.sijainti, 1)
               AND rajoitusalue.urakka_id = urakka_id_
               AND rajoitusalue.poistettu = FALSE
            LOOP
                SELECT st_length(st_intersection(st_buffer(ra.sijainti, 1, 'endcap=flat'), tieosoitevali.geometria)) /
                       st_length(tieosoitevali.geometria)
                  INTO osuus;

                jaljella_osuutta := jaljella_osuutta - osuus;
                RETURN NEXT ('rajoitusalue', ra.id, NULL, osuus)::suolausalueen_osuus;
            END LOOP;

        -- Sitten pohjavesialueet
        FOR pa IN
            SELECT *
              FROM pohjavesialue
             WHERE pohjavesialue.tr_numero = tieosoitevali.tie
               AND st_dwithin(tieosoitevali.geometria, pohjavesialue.alue, 1)
            LOOP
                -- Halutaan tietää, kuinka iso osuus tieosoitevälistä osuu pohjavesialueelle.
                SELECT st_length(st_intersection(st_buffer(pa.alue, 1, 'endcap=flat'), tieosoitevali.geometria)) /
                       st_length(tieosoitevali.geometria)

                  INTO osuus;
                RAISE NOTICE 'Pohjavesialue: %, osuus: %', pa.tunnus, osuus;
                jaljella_osuutta := jaljella_osuutta - osuus;
                RETURN NEXT ('pohjavesialue', NULL, pa.tunnus, osuus)::suolausalueen_osuus;
            END LOOP;

        -- Ja lopuksi se osa, joka ei kuulu mihinkään
        RETURN NEXT ('muu', NULL, NULL, jaljella_osuutta)::suolausalueen_osuus;
    END IF;
    RETURN;
END;
$$ LANGUAGE plpgsql;

-- Päivitetty triggeri. Ajetaan aina, kun toteuma_reittipiste -tauluun tehdään muutos (update, delete, insert)
CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS
$$
DECLARE
    m                               reittipiste_materiaali;
    rp                              reittipistedata;
    suolamateriaalikoodit           INTEGER[];
    edellinen_rp                    reittipistedata;
    suolausalue                     suolausalueen_osuus;
    urakkaid                        INTEGER;
BEGIN
    -- Haetaan materiaalikoodit talteen
    SELECT ARRAY_AGG(id)
      FROM materiaalikoodi
     WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'formiaatti')
      INTO suolamateriaalikoodit;

    -- Haetaan urakkaid vain kerran
    urakkaid := (SELECT urakka FROM toteuma WHERE id = new.toteuma);

    IF (TG_OP = 'UPDATE' OR TG_OP = 'DELETE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma = OLD.toteuma;
    END IF;

    IF (TG_OP != 'DELETE') THEN
        FOREACH rp IN ARRAY NEW.reittipisteet
            LOOP
                FOREACH m IN ARRAY rp.materiaalit
                    LOOP
                        IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                            IF edellinen_rp IS DISTINCT FROM NULL THEN
                                FOR suolausalue IN SELECT tyyppi, rajoitusalue_id, pohjavesialue_tunnus, osuus FROM pistevalin_suolausalueet(edellinen_rp.sijainti, rp.sijainti, urakkaid)
                                    LOOP
                                       -- Lisätään ensimmäisenä rajoitusalueeseen liittyvät määrät
                                        IF suolausalue.rajoitusalue_id IS DISTINCT FROM NULL AND
                                           suolausalue.tyyppi = 'rajoitusalue' THEN
                                            INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, rajoitusalue_id)
                                            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,m.maara * suolausalue.osuus, NULL, suolausalue.rajoitusalue_id);
                                        END IF;

                                       -- Toisenä pohjavesialueeseen liittyvät määrät
                                        IF suolausalue.pohjavesialue_tunnus IS DISTINCT FROM NULL AND
                                           suolausalue.tyyppi = 'pohjavesialue' THEN
                                            INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, rajoitusalue_id)
                                            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,m.maara * suolausalue.osuus, suolausalue.pohjavesialue_tunnus,NULL);
                                        END IF;

                                       -- Ja loput, jotka ei kuulu rajoitusalueille eikä pohjavesialueille
                                        IF suolausalue.tyyppi = 'muu' THEN
                                            INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue,rajoitusalue_id)
                                            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,m.maara * suolausalue.osuus, NULL, NULL);
                                        END IF;
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

-- Ajetaan aina, kun urakan yksikin rajoitusalue päivitetään.
-- Ajetaan ajastetusti yöllä klo 00:45, ei siis välittömästi, koska tämän ajaminen vie aika kauan.
DROP FUNCTION paivita_suolatoteumat_urakalle(INTEGER, DATE, DATE);

CREATE OR REPLACE FUNCTION paivita_suolatoteumat_urakalle(urakkaid INTEGER, alkaa DATE, loppuu DATE)
    RETURNS VOID AS
$$
DECLARE
    loydetyt_toteuman_reittipisteet RECORD;
    m                               reittipiste_materiaali;
    rp                              reittipistedata;
    suolamateriaalikoodit           INTEGER[];
    edellinen_rp                    reittipistedata;
    suolausalue                     suolausalueen_osuus;
BEGIN

    -- Kaikki materiaalikoodit, joita käytetään suolamateriaaleissa - eli ne materiaalit joita lisätään suolatoteuma_reittipiste -tauluun
    SELECT ARRAY_AGG(id)
      FROM materiaalikoodi
     WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'formiaatti')
      INTO suolamateriaalikoodit;

    -- Poista kaikki suolatoteuma_reittipisteet, jotka ovat päivitysparametrien piirissä
    DELETE
      FROM suolatoteuma_reittipiste
     WHERE toteuma IN (SELECT id FROM toteuma WHERE urakka = urakkaid AND luotu BETWEEN alkaa AND loppuu);

    -- Haetaan toteumat ja niiden reittipisteet urakan ja aikavälin puitteissa. Käsitellään vain ei poistettuja
    FOR loydetyt_toteuman_reittipisteet IN
        SELECT tr.reittipisteet, t.id AS toteuma
          FROM toteuma t
                   JOIN toteuman_reittipisteet tr ON t.id = tr.toteuma
         WHERE t.urakka = urakkaid
           AND t.luotu BETWEEN alkaa AND loppuu
           AND t.poistettu IS FALSE

        LOOP
        -- Loopataan löydetyt reittipisteet läpi
        -- Ja jokaiselle löydetylle reittipiste arraylle pyöritetään oma looppi, jossa itse lisääminen suolatoteuma_reittpiste - tauluun tapahtuu
            FOREACH rp IN ARRAY loydetyt_toteuman_reittipisteet.reittipisteet
                LOOP
                    FOREACH m IN ARRAY rp.materiaalit
                        LOOP
                            IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                                IF edellinen_rp IS DISTINCT FROM NULL THEN
                                    FOR suolausalue IN SELECT tyyppi, rajoitusalue_id, pohjavesialue_tunnus, osuus
                                                         FROM pistevalin_suolausalueet(edellinen_rp.sijainti, rp.sijainti, urakkaid)
                                        LOOP
                                            IF suolausalue.rajoitusalue_id IS DISTINCT FROM NULL AND
                                               suolausalue.tyyppi = 'rajoitusalue' THEN
                                                INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti,
                                                                                      materiaalikoodi,
                                                                                      maara, pohjavesialue,
                                                                                      rajoitusalue_id)
                                                VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti,
                                                        m.materiaalikoodi,m.maara * suolausalue.osuus, NULL, suolausalue.rajoitusalue_id);
                                            END IF;

                                            IF suolausalue.pohjavesialue_tunnus IS DISTINCT FROM NULL AND
                                               suolausalue.tyyppi = 'pohjavesialue' THEN
                                                INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti,
                                                                                      materiaalikoodi,
                                                                                      maara, pohjavesialue,
                                                                                      rajoitusalue_id)
                                                VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti,
                                                        m.materiaalikoodi,m.maara * suolausalue.osuus, suolausalue.pohjavesialue_tunnus,
                                                        NULL);
                                            END IF;

                                            IF suolausalue.tyyppi = 'muu' THEN
                                                INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti,
                                                                                      materiaalikoodi,
                                                                                      maara, pohjavesialue,
                                                                                      rajoitusalue_id)
                                                VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti,
                                                        m.materiaalikoodi,m.maara * suolausalue.osuus, NULL, NULL);
                                            END IF;
                                        END LOOP;
                                END IF;
                            END IF;
                        END LOOP;
                    edellinen_rp := rp;
                END LOOP;
        END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Varmistetaan, että trigger on olemassa
DROP TRIGGER IF EXISTS toteuman_reittipisteet_trigger ON toteuman_reittipisteet;

CREATE TRIGGER toteuman_reittipisteet_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON toteuman_reittipisteet
    FOR EACH ROW
EXECUTE PROCEDURE toteuman_reittipisteet_trigger_fn();
