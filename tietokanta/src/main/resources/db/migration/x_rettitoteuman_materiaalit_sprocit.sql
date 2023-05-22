
    CREATE OR REPLACE FUNCTION reittitoteuman_materiaalit()
        RETURNS VOID AS
    $$
    DECLARE
        reitti      RECORD;
        i           INTEGER = 0;
        cnt         INTEGER = 0;
        tehtavat    VARCHAR[];
        materiaalit INT[];
    BEGIN
        RAISE NOTICE '**** ALKU *****';
        FOR reitti IN (SELECT t2.nimi, tt.toteuma, tr.reittipisteet
                       FROM toteuma t
                                join toteuma_tehtava tt on t.id = tt.toteuma
                           -- join toteuma_materiaali tm on t.id = tm.toteuma
                                join toteuman_reittipisteet tr on tt.toteuma = tr.toteuma
                                join toimenpide t2 on tt.toimenpidekoodi = t2.id and t2.id in
                           /*(17299, 17359, 1367, 1368, 19910,
                            1369, 17365, 1412, 1413, 17365,
                            1412, 1413, 7067) */
                                                                                          (select id from toimenpide where emo = 13720)
                       WHERE t.alkanut > '2019-01-01')
            LOOP
                WHILE (i < array_length(reitti.reittipisteet, 1))
                    LOOP
                        i := i + 1;
                        cnt := 1;
                        IF (reitti.nimi = ANY (tehtavat)) THEN
                        ELSE
                            RAISE NOTICE 'Lisätään tehtävä: %.', reitti.nimi;
                            tehtavat := array_append(tehtavat, reitti.nimi);
                        END IF;
                        WHILE (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi IS NOT NULL)
                            LOOP
                                RAISE NOTICE 'i %, cnt %, materiaalikoodi %', i, cnt, reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi;

                                IF (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = ANY (materiaalit)) THEN
                                ELSE
                                    RAISE NOTICE 'Lisätään materiaali: %.', reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi;
                                    materiaalit := array_append(materiaalit,
                                                                reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi);
                                END IF;
                                cnt := cnt + 1;
                            END LOOP;
                    END LOOP;
            END LOOP;
        RAISE NOTICE '**** LOPPU *****';
    END;
    $$ LANGUAGE plpgsql;


    -- Ottaa vastaan toteuma-id:n ja materiaalikoodin.
    -- Kaivaa toteuman reittipisteistä niiden pisteiden koordinaatit, joissa on käytetty kyseistä materiaalia.
    -- Palauttaa pisteet geometria-arrayna, jota voi hyödyntää esim. QGISissä.
    CREATE OR REPLACE FUNCTION toteuman_materiaalipisteet(toteumatunnus INTEGER, materiaalikoodi INTEGER)
        RETURNS GEOMETRY[] AS
    $$
    DECLARE
        reitti  RECORD;
        i       INTEGER = 0;
        cnt     INTEGER = 0;
        pisteet GEOMETRY[];
    BEGIN
        FOR reitti IN (SELECT t.id, tr.reittipisteet
                       FROM toteuma t
                                join toteuman_reittipisteet tr on t.id = tr.toteuma
                       WHERE t.id = toteumatunnus)
            LOOP
                WHILE (i < array_length(reitti.reittipisteet, 1))
                    LOOP
                        i := i + 1;
                        cnt := 1;
                        WHILE (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi IS NOT NULL)
                            LOOP
                                IF (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = materiaalikoodi) THEN
                                    pisteet := array_append(pisteet, reitti.reittipisteet[i].sijainti ::GEOMETRY);
                                    RAISE NOTICE 'i %, cnt %, materiaalikoodi %, koordinaatti %, talvihoitoluokka %', i, cnt, reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi, reitti.reittipisteet[i].sijainti, reitti.reittipisteet[i].talvihoitoluokka;
                                END IF;
                                cnt := cnt + 1;
                            END LOOP;
                    END LOOP;
            END LOOP;
        RETURN pisteet;
    END;
    $$ LANGUAGE plpgsql;

    -- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella pisteet, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
    -- Palauttaa geometria-arrayn, jota voi hyödyntää esim QGISissa.
    CREATE OR REPLACE FUNCTION hae_materiaalipisteet(urakkaid INTEGER, alkupvm DATE, loppupvm DATE,
                                                     materiaali INTEGER, hoitoluokkanro INTEGER,
                                                     hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
        RETURNS SETOF GEOMETRY AS
    $piste$
    DECLARE
        reitti                     GEOMETRY;
        toteuma                    RECORD;
        pistelaskuri               INT;
        toteuma_idt                INTEGER[];
        piste                      GEOMETRY;
        toteuman_materiaalipisteet GEOMETRY[];
        materiaalipisteet          GEOMETRY[];
    BEGIN
        RAISE NOTICE '**** ALKU. Urakka: %. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', urakkaid, alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
        pistelaskuri := 0;
        FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                        from toteuma t
                                 join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                        where t.urakka = urakkaid
                          and t.alkanut between alkupvm and loppupvm
                          and t.reitti is not null)
            LOOP
                FOR reitti IN (select geometria::GEOMETRY
                               from hoitoluokka
                               where hoitoluokka = hoitoluokkanro
                                 and tietolajitunniste = hoitoluokkatyyppi)
                    LOOP
                        IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                            toteuman_materiaalipisteet := (SELECT toteuman_materiaalipisteet(toteuma.toteuma, materiaali));
                            IF (array_length(toteuman_materiaalipisteet, 1) > 0) THEN
                                materiaalipisteet := concat(materiaalipisteet || toteuman_materiaalipisteet);
                                pistelaskuri := pistelaskuri + 1;
                                toteuma_idt := array_append(toteuma_idt, toteuma.toteuma);
                            END IF;
                        END IF;
                    END LOOP;
            END LOOP;
        RAISE NOTICE '**** LOPPU. Hoitoluokkaan % osuneita pisteitä löytyi % kpl. Toteumat ovat: % *****', hoitoluokkanro,pistelaskuri, toteuma_idt;

        FOREACH piste IN ARRAY materiaalipisteet
            LOOP
                RETURN NEXT piste;
            END LOOP;

    END;
    $piste$ LANGUAGE plpgsql;


    -- Ottaa vastaan toteuma-id:n ja materiaalikoodin.
    -- Kaivaa toteuman reittipisteistä niiden pisteiden materiaalimäärät, joissa on käytetty kyseistä materiaalia.
    -- Palauttaa määrän yhteenlaskettuna.
    CREATE OR REPLACE FUNCTION toteuman_materiaalimaarat(toteumatunnus INTEGER, materiaalikoodi INTEGER)
        RETURNS NUMERIC AS
    $$
    DECLARE
        reitti RECORD;
        i      INTEGER = 0;
        cnt    INTEGER = 0;
        maara  NUMERIC = 0;
    BEGIN
        FOR reitti IN (SELECT t.id, tr.reittipisteet
                       FROM toteuma t
                                join toteuman_reittipisteet tr on t.id = tr.toteuma
                       WHERE t.id = toteumatunnus)
            LOOP
                WHILE (i < array_length(reitti.reittipisteet, 1))
                    LOOP
                        i := i + 1;
                        cnt := 1;
                        WHILE (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = materiaalikoodi)
                            LOOP
                                IF (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = materiaalikoodi) THEN
                                    maara := maara + reitti.reittipisteet[i].materiaalit[cnt].maara;
                                    -- RAISE NOTICE 'i %, cnt %, materiaalikoodi %, maara %, talvihoitoluokka %', i, cnt, reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi, reitti.reittipisteet[i].materiaalit[cnt].maara, reitti.reittipisteet[i].talvihoitoluokka;
                                END IF;
                                cnt := cnt + 1;
                            END LOOP;
                    END LOOP;
            END LOOP;
        -- RAISE NOTICE 'Palautetaan määrä %', maara;
        RETURN maara;
    END;
    $$ LANGUAGE plpgsql;


    -- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella materiaalimäärät, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
    -- Palauttaa materiaalin yhteenlasketun määrän, jota voi hyödyntää esim QGISissa.
    CREATE OR REPLACE FUNCTION hae_materiaalimaara(urakkaid INTEGER, alkupvm DATE, loppupvm DATE,
                                                   materiaali INTEGER, hoitoluokkanro INTEGER,
                                                   hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
        RETURNS NUMERIC AS
    $$
    DECLARE
        reitti          GEOMETRY;
        toteuma         RECORD;
        materiaalimaara NUMERIC = 0;
    BEGIN
        RAISE NOTICE '**** ALKU. Urakka: %. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', urakkaid, alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
        FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                        from toteuma t
                                 join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                        where t.urakka = urakkaid
                          and t.alkanut between alkupvm and loppupvm
                          and t.reitti is not null)
            LOOP
                FOR reitti IN (select geometria::GEOMETRY
                               from hoitoluokka
                               where hoitoluokka = hoitoluokkanro
                                 and tietolajitunniste = hoitoluokkatyyppi)
                    LOOP
                        IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                            materiaalimaara := materiaalimaara +
                                               (SELECT toteuman_materiaalimaarat(toteuma.toteuma, materiaali))::NUMERIC;
                        END IF;
                    END LOOP;
            END LOOP;
        RAISE NOTICE '**** LOPPU. Käytetty materiaalimäärä on: % *****', materiaalimaara;

        RETURN materiaalimaara;
    END;
    $$ LANGUAGE plpgsql;



    -- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella materiaalimäärät, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
    -- Palauttaa materiaalin yhteenlasketun määrän, jota voi hyödyntää esim QGISissa.
    CREATE OR REPLACE FUNCTION hae_materiaalimaara_ely(ely INTEGER, alkupvm DATE, loppupvm DATE,
                                                       materiaali INTEGER, hoitoluokkanro INTEGER,
                                                       hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
        RETURNS NUMERIC AS
    $$
    DECLARE
        reitti          GEOMETRY;
        toteuma         RECORD;
        materiaalimaara NUMERIC = 0;
    BEGIN
        RAISE NOTICE '**** ALKU. Urakka: %. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', urakkaid, alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
        FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                        from toteuma t
                                 join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                        where t.urakka in (select id from urakka where hallintayksikko = ely)
                          and t.alkanut between alkupvm and loppupvm
                          and t.reitti is not null)
            LOOP
                FOR reitti IN (select geometria::GEOMETRY
                               from hoitoluokka
                               where hoitoluokka = hoitoluokkanro
                                 and tietolajitunniste = hoitoluokkatyyppi)
                    LOOP
                        IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                            materiaalimaara := materiaalimaara +
                                               (SELECT toteuman_materiaalimaarat(toteuma.toteuma, materiaali))::NUMERIC;
                        END IF;
                    END LOOP;
            END LOOP;
        RAISE NOTICE '**** LOPPU. Käytetty materiaalimäärä on: % *****', materiaalimaara;

        RETURN materiaalimaara;
    END;
    $$ LANGUAGE plpgsql;



    -- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella materiaalimäärät, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
    -- Palauttaa materiaalin yhteenlasketun määrän, jota voi hyödyntää esim QGISissa.
    CREATE OR REPLACE FUNCTION hae_materiaalimaara_suomi(alkupvm DATE, loppupvm DATE,
                                                         materiaali INTEGER, hoitoluokkanro INTEGER,
                                                         hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
        RETURNS NUMERIC AS
    $$
    DECLARE
        reitti          GEOMETRY;
        toteuma         RECORD;
        materiaalimaara NUMERIC = 0;
    BEGIN
        RAISE NOTICE '**** ALKU. Koko maa. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
        FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                        from toteuma t
                                 join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                        where t.urakka in (select id from urakka where tyyppi in ('hoito', 'teiden-hoito'))
                          and t.alkanut between alkupvm and loppupvm
                          and t.reitti is not null)
            LOOP
                FOR reitti IN (select geometria::GEOMETRY
                               from hoitoluokka
                               where hoitoluokka = hoitoluokkanro
                                 and tietolajitunniste = hoitoluokkatyyppi)
                    LOOP
                        IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                            materiaalimaara := materiaalimaara +
                                               (SELECT toteuman_materiaalimaarat(toteuma.toteuma, materiaali))::NUMERIC;
                        END IF;
                    END LOOP;
            END LOOP;
        RAISE NOTICE '**** LOPPU. Käytetty materiaalimäärä on: % *****', materiaalimaara;

        RETURN materiaalimaara;
    END;
    $$ LANGUAGE plpgsql;
