-- Tehdään Hoitokausittain partitiot tauluille toteuma_tehtava ja toteuma_materiaali.
--
--
-- Toteutustapa:
--   * Deklaratiivinen partitiointi (PARTITION BY RANGE), ei INHERITS. Partition pruning,
--     rivien automaattinen ohjaus partitioihin ja partitioidut indeksit tulevat valmiina.
--   * Partitiointiavain on lähdetauluissa jo oleva hoitokauden_alkuvuosi-sarake.
--   * NULL-arvot ja orvot rivit (toteuma-viite ei osu mihinkään toteumaan; FK poistettiin
--     migraatiossa V1_832) saavat arvon -1 ja päätyvät partitioon *_ennen_2014.
--
-- Kopiointi voidaan keskeyttää milloin tahansa (Ctrl-C / yhteyden katkeaminen). Uusi
-- CALL kopioi_toteuma_hk_data() jatkaa siitä ID:stä, mihin edellinen ajo ehti.
-- Etenemistä seurataan taulusta toteuma_hk_siirto_tila.

-- Aiempien yritysten jäänteet pois. DROP ROUTINE poistaa sekä funktion että proseduurin.
DROP ROUTINE IF EXISTS partitioi_toteumataulu(TEXT, TEXT, DATE, DATE, INTEGER);
DROP ROUTINE IF EXISTS partitioi_toteumat(INTEGER);

-- Pudotetaan myös tämän tiedoston omat rutiinit, jotta parametrien uudelleennimeäminen
-- ei kaadu CREATE OR REPLACE -rajoitteeseen skriptiä uudelleen ajettaessa.
DROP ROUTINE IF EXISTS partitioi_toteumat_hoitokausittain(INTEGER);
DROP ROUTINE IF EXISTS viimeistele_toteuma_hk_taulut();
DROP ROUTINE IF EXISTS kopioi_toteuma_hk_data(INTEGER);
DROP ROUTINE IF EXISTS kopioi_toteuma_hk_taulu(TEXT, INTEGER);
DROP ROUTINE IF EXISTS luo_toteuma_hk_taulut(INTEGER, INTEGER);
DROP ROUTINE IF EXISTS luo_toteuma_hk_taulu(TEXT, INTEGER, INTEGER);
DROP ROUTINE IF EXISTS vaihda_toteuma_hk_taulut();


-- Partitiointiavaimena käytetään lähdetauluissa jo olevaa
-- hoitokauden_alkuvuosi-saraketta. NULL-arvot ohjataan kopioinnissa arvolla -1
-- vanhimpaan partitioon.


-- Siirron tila. Yksi rivi per lähdetaulu. Päivitetään samassa transaktiossa erän kanssa,
-- joten tila vastaa aina kohdetaulun sisältöä myös keskeytyksen jälkeen.
CREATE TABLE IF NOT EXISTS toteuma_hk_siirto_tila
(
    lahde_taulu  TEXT PRIMARY KEY,
    kohde_taulu  TEXT      NOT NULL,
    viimeinen_id BIGINT    NOT NULL DEFAULT -1,
    siirretty    BIGINT    NOT NULL DEFAULT 0,
    valmis       BOOLEAN   NOT NULL DEFAULT FALSE,
    luotu        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokattu     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION estä_kirjoitukset()
    RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Kirjoitukset tilapäisesti estetty ylläpidon ajan';
END;
$$ LANGUAGE plpgsql;

-- Luo yhden hoitokausipartitioidun taulun ja sen partitiot.
-- Taulun nimi on lähdetaulun nimi + _hk. Partitiointiavain on hoitokauden_alkuvuosi.
-- Partitiointi alkaa *_hk_ennen_2014 ja päättyy *_hk_tulevat. Partitioiden nimet ovat muotoa *_YYYY_YYYY.
-- Eli perustaulun nimi on *_hk_2014_2015, seuraava *_hk_2015_2016 jne. Viimeinen partitiot on *_tulevat.
-- Tässä tiedostossa partitioidaan toteuma_tehtavat ja toteuma_materiaalit, joten
-- partitioitujen taulujen nimet on tyyliin toteuma_tehtava_hk_2014_2015, toteuma_materiaali_hk_2014_2015 jne.
-- Tätä voi tosin kohtuu helposti laajentaa myös muistin tauluihin. Siksi tässä käytetään lähdetaulua parametrina.
CREATE OR REPLACE PROCEDURE luo_toteuma_hk_taulu(lahdetaulu TEXT,
                                                 ensimmainen_hoitokausi INTEGER,
                                                 viimeinen_hoitokausi INTEGER)
    LANGUAGE plpgsql AS
$$
DECLARE
    kohdetaulu TEXT := lahdetaulu || '_hk';
    hk         INTEGER;
    partitio   TEXT;
BEGIN
    IF to_regclass(format('public.%I', kohdetaulu)) IS NULL THEN
        EXECUTE format(
            'CREATE TABLE public.%I (LIKE public.%I INCLUDING DEFAULTS)
            PARTITION BY RANGE (hoitokauden_alkuvuosi)',
            kohdetaulu, lahdetaulu);
        RAISE NOTICE 'Luotiin partitioitu taulu public.%', kohdetaulu;
    ELSE
        RAISE NOTICE 'Taulu public.% on jo olemassa, ohitetaan luonti', kohdetaulu;
    END IF;

    partitio := kohdetaulu || '_ennen_' || ensimmainen_hoitokausi;
    IF to_regclass(format('public.%I', partitio)) IS NULL THEN
        EXECUTE format(
            'CREATE TABLE public.%I PARTITION OF public.%I
             FOR VALUES FROM (MINVALUE) TO (%s)',
            partitio, kohdetaulu, ensimmainen_hoitokausi);
    END IF;

    FOR hk IN ensimmainen_hoitokausi..viimeinen_hoitokausi
        LOOP
            partitio := format('%s_%s_%s', kohdetaulu, hk, hk + 1);
            IF to_regclass(format('public.%I', partitio)) IS NULL THEN
                EXECUTE format(
                    'CREATE TABLE public.%I PARTITION OF public.%I
                     FOR VALUES FROM (%s) TO (%s)',
                    partitio, kohdetaulu, hk, hk + 1);
            END IF;
        END LOOP;

    partitio := kohdetaulu || '_tulevat';
    IF to_regclass(format('public.%I', partitio)) IS NULL THEN
        EXECUTE format(
            'CREATE TABLE public.%I PARTITION OF public.%I
             FOR VALUES FROM (%s) TO (MAXVALUE)',
            partitio, kohdetaulu, viimeinen_hoitokausi + 1);
    END IF;

    INSERT INTO toteuma_hk_siirto_tila (lahde_taulu, kohde_taulu)
    VALUES (lahdetaulu, kohdetaulu)
    ON CONFLICT (lahde_taulu) DO NOTHING;

    RAISE NOTICE 'Taulun % partitiot valmiina (hoitokaudet % - %)',
        kohdetaulu, ensimmainen_hoitokausi, viimeinen_hoitokausi;
END;
$$;


-- Luodaan uudet partitioitavat taulut. Viimeinen hoitokausi päätellään toteuma-taulun
-- suurimmasta järkevästä alkanut-arvosta, ellei sitä anneta.
CREATE OR REPLACE PROCEDURE luo_toteuma_hk_taulut(ensimmainen_hoitokausi INTEGER DEFAULT 2014,
                                                  viimeinen_hoitokausi INTEGER DEFAULT NULL)
    LANGUAGE plpgsql AS
$$
DECLARE
    viimeinen INTEGER := viimeinen_hoitokausi;
BEGIN
    IF viimeinen IS NULL THEN
        SELECT GREATEST(hoitokauden_alkuvuosi(max(alkanut)),
                        hoitokauden_alkuvuosi(CURRENT_TIMESTAMP::TIMESTAMP))
        INTO viimeinen
        FROM toteuma
        WHERE alkanut < CURRENT_TIMESTAMP + INTERVAL '2 years';
        viimeinen := GREATEST(COALESCE(viimeinen, ensimmainen_hoitokausi),
                              ensimmainen_hoitokausi);
    END IF;

    CALL luo_toteuma_hk_taulu('toteuma_tehtava', ensimmainen_hoitokausi, viimeinen);
    CALL luo_toteuma_hk_taulu('toteuma_materiaali', ensimmainen_hoitokausi, viimeinen);
    -- Hox. Tämä on laajennettavissa periatteessa mille tahansa taululle, jolla on hotokauden_alkuvuosi-sarake.
    -- Esim jos toteuman_reittipisteet taululle lisäisi hoitokauden_alkuvuosi-sarakkeen, voisi senkin partitioida samalla tavalla.
END;
$$;


-- Kopioi yhden lähdetaulun datan partitioituun tauluun erissä.
-- Jatkaa aina taulusta toteuma_hk_siirto_tila luetusta ID:stä, joten keskeytys on turvallinen.
CREATE OR REPLACE PROCEDURE kopioi_toteuma_hk_taulu(lahdetaulu TEXT,
                                                    eran_koko INTEGER DEFAULT 500000)
    LANGUAGE plpgsql AS
$$
DECLARE
    kohdetaulu      TEXT;
    edellinen_id    BIGINT;
    rivit_yhteensa  BIGINT;
    onko_valmis     BOOLEAN;
    sarakkeet       TEXT;
    sarakkeet_alias TEXT;
    eran_ylaraja    BIGINT;
    erassa          BIGINT;
    arvio_yhteensa  BIGINT;
    aloitus_aika    TIMESTAMP := clock_timestamp();
    alku_siirretty  BIGINT;
    kulunut_s       NUMERIC;
    jaljella_s      NUMERIC;
    prosentti       NUMERIC;
BEGIN
    IF eran_koko <= 0 THEN
        RAISE EXCEPTION 'Erän koon on oltava positiivinen, annettu: %', eran_koko;
    END IF;

    SELECT t.kohde_taulu, t.viimeinen_id, t.siirretty, t.valmis
    INTO kohdetaulu, edellinen_id, rivit_yhteensa, onko_valmis
    FROM toteuma_hk_siirto_tila t
    WHERE t.lahde_taulu = lahdetaulu;

    IF kohdetaulu IS NULL THEN
        RAISE EXCEPTION 'Taululle % ei löydy siirron tilaa. Aja ensin CALL luo_toteuma_hk_taulut()',
            lahdetaulu;
    END IF;

    IF onko_valmis THEN
        RAISE NOTICE 'Taulu % on jo kopioitu (% riviä), ohitetaan', lahdetaulu, rivit_yhteensa;
        RETURN;
    END IF;

    alku_siirretty := rivit_yhteensa;

    SELECT string_agg(format('%I', column_name), ', ' ORDER BY ordinal_position),
           string_agg(
               CASE
                   WHEN column_name = 'hoitokauden_alkuvuosi'
                       THEN format('COALESCE(s.%I, -1) AS %I', column_name, column_name)
                   ELSE format('s.%I', column_name)
                   END,
               ', ' ORDER BY ordinal_position)
    INTO sarakkeet, sarakkeet_alias
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = lahdetaulu;

    -- Karkea kokonaismäärä arviota varten. Tilastoarvio riittää eikä vaadi täyttä laskentaa. (rentouttaa sielua, kun näkee etenemisen)
    SELECT GREATEST(reltuples, 0)::BIGINT
    INTO arvio_yhteensa
    FROM pg_class
    WHERE oid = format('public.%I', lahdetaulu)::REGCLASS;

    RAISE NOTICE 'Kopiointi % -> % alkaa. Arvio lähteessä % riviä, valmiina % riviä, jatketaan ID:stä %',
        lahdetaulu, kohdetaulu, arvio_yhteensa, rivit_yhteensa, edellinen_id + 1;

    LOOP
        -- Erän yläraja päätellään etukäteen, jotta INSERT on deterministinen.
        EXECUTE format(
            'SELECT max(id) FROM (SELECT id FROM public.%I WHERE id > %s ORDER BY id LIMIT %s) e',
            lahdetaulu, edellinen_id, eran_koko)
            INTO eran_ylaraja;

        EXIT WHEN eran_ylaraja IS NULL;

        EXECUTE format(
            'INSERT INTO public.%I (%s)
             SELECT %s
               FROM public.%I s
              WHERE s.id > %s
                AND s.id <= %s',
            kohdetaulu, sarakkeet, sarakkeet_alias, lahdetaulu,
            edellinen_id, eran_ylaraja);
        GET DIAGNOSTICS erassa = ROW_COUNT;

        edellinen_id := eran_ylaraja;
        rivit_yhteensa := rivit_yhteensa + erassa;

        UPDATE toteuma_hk_siirto_tila t
        SET viimeinen_id = edellinen_id,
            siirretty    = rivit_yhteensa,
            muokattu     = CURRENT_TIMESTAMP
        WHERE t.lahde_taulu = lahdetaulu;

        COMMIT;

        kulunut_s := EXTRACT(EPOCH FROM clock_timestamp() - aloitus_aika);
        prosentti := CASE
                         WHEN arvio_yhteensa > 0
                             THEN LEAST(100, 100.0 * rivit_yhteensa / arvio_yhteensa)
                         ELSE NULL END;
        jaljella_s := CASE
                          WHEN rivit_yhteensa > alku_siirretty AND arvio_yhteensa > rivit_yhteensa
                              THEN kulunut_s * (arvio_yhteensa - rivit_yhteensa)
                              / (rivit_yhteensa - alku_siirretty)
                          ELSE NULL END;

        RAISE NOTICE '% -> %: erä % riviä, yhteensä % / % (% %%), ID <= %, kulunut % s, arvio jäljellä % s',
            lahdetaulu, kohdetaulu, erassa, rivit_yhteensa, arvio_yhteensa,
            round(COALESCE(prosentti, 0), 2), edellinen_id,
            round(kulunut_s), round(COALESCE(jaljella_s, 0));
    END LOOP;

    UPDATE toteuma_hk_siirto_tila t
    SET valmis   = TRUE,
        muokattu = CURRENT_TIMESTAMP
    WHERE t.lahde_taulu = lahdetaulu;
    COMMIT;

    RAISE NOTICE 'Kopiointi % -> % valmis, yhteensä % riviä, kesto % s',
        lahdetaulu, kohdetaulu, rivit_yhteensa,
        round(EXTRACT(EPOCH FROM clock_timestamp() - aloitus_aika));
END;
$$;


CREATE OR REPLACE PROCEDURE kopioi_toteuma_hk_data(eran_koko INTEGER DEFAULT 500000)
    LANGUAGE plpgsql AS
$$
BEGIN
    CALL kopioi_toteuma_hk_taulu('toteuma_tehtava', eran_koko);
    CALL kopioi_toteuma_hk_taulu('toteuma_materiaali', eran_koko);
    -- Jos tätä laajennetaan uusille tauluille, niin tänne myös se uusi taulu muistaa lisätä
END;
$$;


-- Indeksit, foreignkeyt ja tilastot luodaan vasta kopioinnin jälkeen: se on selvästi
-- nopeampaa kuin ylläpitää niitä jokaisen erän ajan.
CREATE OR REPLACE PROCEDURE viimeistele_toteuma_hk_taulut()
    LANGUAGE plpgsql AS
$$
DECLARE
    kesken TEXT;
BEGIN
    SELECT string_agg(lahde_taulu, ', ')
    INTO kesken
    FROM toteuma_hk_siirto_tila
    WHERE NOT valmis;

    IF kesken IS NOT NULL THEN
        RAISE EXCEPTION 'Kopiointi on kesken tauluille: %. Aja ensin CALL kopioi_toteuma_hk_data()',
            kesken;
    END IF;

    -- Partitiointiavaimen on oltava mukana perusavaimessa.
    IF NOT EXISTS (SELECT 1
                   FROM pg_constraint
                   WHERE conrelid = 'public.toteuma_tehtava_hk'::REGCLASS
                     AND contype = 'p') THEN
        ALTER TABLE public.toteuma_tehtava_hk
            ADD CONSTRAINT toteuma_tehtava_hk_pkey PRIMARY KEY (id, hoitokauden_alkuvuosi);
    END IF;
    IF NOT EXISTS (SELECT 1
                   FROM pg_constraint
                   WHERE conrelid = 'public.toteuma_materiaali_hk'::REGCLASS
                     AND contype = 'p') THEN
        ALTER TABLE public.toteuma_materiaali_hk
            ADD CONSTRAINT toteuma_materiaali_hk_pkey PRIMARY KEY (id, hoitokauden_alkuvuosi);
    END IF;
    RAISE NOTICE 'Perusavaimet luotu';

    -- Vastaavat indeksit kuin alkuperäisissä tauluissa
    CREATE INDEX IF NOT EXISTS idx_toteuma_tehtava_hk_toteuma_tpk_poistettu
        ON public.toteuma_tehtava_hk (toteuma, toimenpidekoodi, poistettu);
    CREATE INDEX IF NOT EXISTS idx_toteuma_tehtava_hk_urakka_hk
        ON public.toteuma_tehtava_hk (urakka_id, hoitokauden_alkuvuosi, toimenpidekoodi)
        INCLUDE (maara, toteuma)
        WHERE poistettu = FALSE;
    CREATE INDEX IF NOT EXISTS idx_toteuma_tehtava_hk_toimenpidekoodi
        ON public.toteuma_tehtava_hk (toimenpidekoodi);
    CREATE INDEX IF NOT EXISTS idx_toteuma_tehtava_hk_toteuma
        ON public.toteuma_tehtava_hk (toteuma);
    CREATE INDEX IF NOT EXISTS idx_toteuma_tehtava_hk_urakka_poistettu
        ON public.toteuma_tehtava_hk (urakka_id, poistettu);

    CREATE INDEX IF NOT EXISTS idx_toteuma_materiaali_hk_toteuma_urakka_hk
        ON public.toteuma_materiaali_hk (toteuma, urakka_id, hoitokauden_alkuvuosi)
        INCLUDE (maara)
        WHERE poistettu = FALSE;
    CREATE INDEX IF NOT EXISTS idx_toteuma_materiaali_hk_urakka_hk
        ON public.toteuma_materiaali_hk (materiaalikoodi, urakka_id, hoitokauden_alkuvuosi)
        INCLUDE (maara, toteuma)
        WHERE poistettu = FALSE;
    CREATE INDEX IF NOT EXISTS idx_toteuma_materiaali_hk_toteuma
        ON public.toteuma_materiaali_hk (toteuma);
    CREATE INDEX IF NOT EXISTS idx_toteuma_materiaali_hk_urakka_poistettu
        ON public.toteuma_materiaali_hk (urakka_id, poistettu);
    -- Listään yksi testattu indeksi, joka parantaa vielä materiaalien hakua
    CREATE INDEX IF NOT EXISTS idx_toteuma_materiaali_hk_urakka_hoitokausi
        ON public.toteuma_materiaali_hk
            (urakka_id, hoitokauden_alkuvuosi)
        INCLUDE (toteuma, maara)
        WHERE poistettu = FALSE;
    ANALYZE public.toteuma_materiaali_hk;
    RAISE NOTICE 'Indeksit luotu';

    -- Viiteavaimet vain tauluihin, joihin alkuperäisissäkin tauluissa viitataan.
    -- toteuma-sarakkeelle ei voi tehdä viiteavainta, koska toteuma on INHERITS-partitioitu.
    IF NOT EXISTS (SELECT 1
                   FROM pg_constraint
                   WHERE conname = 'toteuma_tehtava_hk_luoja_fkey') THEN
        ALTER TABLE public.toteuma_tehtava_hk
            ADD CONSTRAINT toteuma_tehtava_hk_luoja_fkey
                FOREIGN KEY (luoja) REFERENCES public.kayttaja (id);
    END IF;
    IF NOT EXISTS (SELECT 1
                   FROM pg_constraint
                   WHERE conname = 'toteuma_tehtava_hk_toimenpidekoodi_fkey') THEN
        ALTER TABLE public.toteuma_tehtava_hk
            ADD CONSTRAINT toteuma_tehtava_hk_toimenpidekoodi_fkey
                FOREIGN KEY (toimenpidekoodi) REFERENCES public.tehtava (id);
    END IF;
    IF NOT EXISTS (SELECT 1
                   FROM pg_constraint
                   WHERE conname = 'toteuma_materiaali_hk_materiaalikoodi_fkey') THEN
        ALTER TABLE public.toteuma_materiaali_hk
            ADD CONSTRAINT toteuma_materiaali_hk_materiaalikoodi_fkey
                FOREIGN KEY (materiaalikoodi) REFERENCES public.materiaalikoodi (id);
    END IF;
    RAISE NOTICE 'Viiteavaimet luotu';

    ANALYZE public.toteuma_tehtava_hk;
    ANALYZE public.toteuma_materiaali_hk;
    RAISE NOTICE 'Tilastot päivitetty, vertailutaulut ovat käyttövalmiit';

    -- Luodaan triggerit
    -- Jos toteuma_tehtava muuttuu, poista laskutusyhteenvedot cachesta. koska
    -- esim määrän muutoksella on vaikutus yksikköhintaisten töiden kustannuksiin
    CREATE TRIGGER tg_poista_muistetut_laskutusyht_toteuma_tehtava_hk
        AFTER INSERT OR UPDATE
        ON toteuma_tehtava_hk
        FOR EACH ROW
    EXECUTE PROCEDURE poista_muistetut_laskutusyht_toteuma_tehtava();

END;
$$;


-- Vaihdetaan hoitokausittain partitioidut toteumataulut alkuperäisille nimille.
-- Vaihtaa nimet vasta kun kopiointi ja viimeistely ovat onnistuneet.
-- Tämä CALL suoritetaan erillisenä, koska kopiointiproseduuri tekee COMMITit.
CREATE OR REPLACE PROCEDURE vaihda_toteuma_hk_taulut()
    LANGUAGE plpgsql AS
$$
BEGIN

    -- Varmistellaan, että taulut todellakin ovat olemassa
    IF to_regclass('public.toteuma_tehtava_hk') IS NULL
        OR to_regclass('public.toteuma_materiaali_hk') IS NULL THEN
        RAISE EXCEPTION 'Partitioitu kohdetaulu puuttuu ennen nimeämistä';
    END IF;

    -- Varmuuskopioidaan vanhat taulut!
    -- Nimeämiset ovat nopeita. ACCESS EXCLUSIVE -lukot syntyvät ALTER TABLE
    -- -lauseiden ajaksi ja estävät samanaikaiset SELECTit ja muutokset.
    ALTER TABLE public.toteuma_tehtava
        RENAME TO toteuma_tehtava_vanha;
    ALTER TABLE public.toteuma_materiaali
        RENAME TO toteuma_materiaali_vanha;

    -- Otetaan uudet partitioidut taulut käyttöön.
    ALTER TABLE public.toteuma_tehtava_hk
        RENAME TO toteuma_tehtava;
    ALTER TABLE public.toteuma_materiaali_hk
        RENAME TO toteuma_materiaali;

    -- Siirron tila kuvaa nimeämisen jälkeen käytössä olevia kohdetauluja.
    UPDATE toteuma_hk_siirto_tila
    SET kohde_taulu = CASE lahde_taulu
                          WHEN 'toteuma_tehtava' THEN 'toteuma_tehtava'
                          WHEN 'toteuma_materiaali' THEN 'toteuma_materiaali'
                          ELSE kohde_taulu
        END,
        muokattu = CURRENT_TIMESTAMP
    WHERE lahde_taulu IN ('toteuma_tehtava', 'toteuma_materiaali');

    UPDATE toteuma_hk_siirto_tila
    SET muokattu = CURRENT_TIMESTAMP
    WHERE lahde_taulu IN ('toteuma_tehtava', 'toteuma_materiaali');

    RAISE NOTICE 'Toteumataulut vaihdettu partitioituihin tauluihin; vanhat taulut ovat *_vanha-nimillä';
END;
$$;

-- Koko putki kerralla.
-- Jos ajo keskeytyy, niin riittää, että kutsuu nuo kaksi alinta uusiksi.
-- Tuo 500 000 on hyvä oletusarvo, mutta voi olla tarpeen suurentaa, jos koneessa on vääntöä. Isompi koko voi olla nopeampi
CREATE OR REPLACE PROCEDURE partitioi_toteumat_hoitokausittain(eran_koko INTEGER DEFAULT 500000)
    LANGUAGE plpgsql AS
$$
BEGIN
    -- Lukitaan toteuma_tehtava ja toteuma_materiaali, jotta kukaan ei voi kirjoittaa niihin kopioinnin aikana.
    IF NOT EXISTS (SELECT 1
                   FROM pg_trigger
                   WHERE tgrelid = 'public.toteuma_tehtava'::REGCLASS
                     AND tgname = 'tg_estaa_toteuma_hk_kirjoitus'
                     AND NOT tgisinternal) THEN
        CREATE TRIGGER tg_estaa_toteuma_hk_kirjoitus
            BEFORE INSERT OR UPDATE OR DELETE
            ON public.toteuma_tehtava
            FOR EACH ROW
        EXECUTE FUNCTION estä_kirjoitukset();
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM pg_trigger
                   WHERE tgrelid = 'public.toteuma_materiaali'::REGCLASS
                     AND tgname = 'tg_estaa_toteuma_hk_kirjoitus'
                     AND NOT tgisinternal) THEN
        CREATE TRIGGER tg_estaa_toteuma_hk_kirjoitus
            BEFORE INSERT OR UPDATE OR DELETE
            ON public.toteuma_materiaali
            FOR EACH ROW
        EXECUTE FUNCTION estä_kirjoitukset();
    END IF;


    CALL luo_toteuma_hk_taulut();
    CALL kopioi_toteuma_hk_data(eran_koko);
    CALL viimeistele_toteuma_hk_taulut();
    CALL vaihda_toteuma_hk_taulut();

END;
$$;

-- Tätä tarvitaan vain jos prosessi keskeytyy ja ei päästä suoraan vaihtamaan uusiin partitioituihin tauluihin.
-- Jos tätä käytetään sen jälkeen, kun lukotukset on poistettu ja dataa on jo tallennettu uusiin toteuma_tehtava ja toteuma_materiaalitauluihin,
-- niin se data menetetään. MEille jää siis toteuma -tauluun dataa, joista ei ole toteuma_tehtava ja toteuma_materiaali -tauluissa vastaavia rivejä.
-- Eli tätä toivottavasti ei koskaan tarvita. Eikä tarvitakaan, jos kaikki menee kuten on harjoiteltu
CREATE OR REPLACE PROCEDURE poista_toteuma_hk_kirjoitusestot()
    LANGUAGE plpgsql AS
$$
BEGIN
    DROP TRIGGER IF EXISTS tg_estaa_toteuma_hk_kirjoitus
        ON public.toteuma_tehtava;

    DROP TRIGGER IF EXISTS tg_estaa_toteuma_hk_kirjoitus
        ON public.toteuma_materiaali;

    RAISE NOTICE 'Toteumataulujen kirjoitusestot poistettu';
END;
$$;


-- Palauttaa testikannan onnistuneen tauluvaihdon jälkeisestä tilasta takaisin
-- migraatiota edeltävään tilaan. Tämä on tarkoitettu vain tilanteeseen, jossa
-- toteuma_tehtava_vanha ja toteuma_materiaali_vanha ovat olemassa ja nykyiset
-- toteuma_tehtava- ja toteuma_materiaali-taulut ovat tämän migraation luomia
-- partitioituja tauluja.
--
-- *_hk-nimet ovat onnistuneen vaihdon jälkeen näkymiä, joten niitä ei voi poistaa
-- DROP TABLE -komennolla. Alkuperäisissä tauluissa olevat kirjoituseston triggerit
-- poistetaan ennen kuin taulut nimetään takaisin alkuperäisille nimilleen.
CREATE OR REPLACE FUNCTION palauta_toteuma_hk_taulut()
    RETURNS VOID
    LANGUAGE plpgsql AS
$$
DECLARE
    vanha_tehtava_tyyppi      "char";
    vanha_materiaali_tyyppi   "char";
    nykyinen_tehtava_tyyppi  "char";
    nykyinen_materiaali_tyyppi "char";
    hk_tehtava_tyyppi        "char";
    hk_materiaali_tyyppi     "char";
BEGIN
    IF to_regclass('public.toteuma_tehtava_vanha') IS NULL
        OR to_regclass('public.toteuma_materiaali_vanha') IS NULL THEN
        RAISE EXCEPTION 'Alkuperäisiä *_vanha-tauluja ei löydy; palautusta ei suoriteta';
    END IF;

    SELECT c.relkind
    INTO vanha_tehtava_tyyppi
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname = 'toteuma_tehtava_vanha';

    SELECT c.relkind
    INTO vanha_materiaali_tyyppi
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname = 'toteuma_materiaali_vanha';

    IF vanha_tehtava_tyyppi IS DISTINCT FROM 'r'
        OR vanha_materiaali_tyyppi IS DISTINCT FROM 'r' THEN
        RAISE EXCEPTION 'Alkuperäiset *_vanha-objektit eivät ole tavallisia tauluja';
    END IF;

    SELECT c.relkind
    INTO nykyinen_tehtava_tyyppi
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname = 'toteuma_tehtava';

    SELECT c.relkind
    INTO nykyinen_materiaali_tyyppi
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname = 'toteuma_materiaali';

    IF nykyinen_tehtava_tyyppi IS DISTINCT FROM 'p'
        OR nykyinen_materiaali_tyyppi IS DISTINCT FROM 'p' THEN
        RAISE EXCEPTION 'Nykyiset toteumataulut eivät ole tämän migraation luomia partitioituja tauluja';
    END IF;

    SELECT c.relkind
    INTO hk_tehtava_tyyppi
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname = 'toteuma_tehtava_hk';

    SELECT c.relkind
    INTO hk_materiaali_tyyppi
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname = 'toteuma_materiaali_hk';

    IF hk_tehtava_tyyppi IS NOT NULL AND hk_tehtava_tyyppi <> 'v' THEN
        RAISE EXCEPTION 'toteuma_tehtava_hk ei ole näkymä; palautusta ei suoriteta';
    END IF;
    IF hk_materiaali_tyyppi IS NOT NULL AND hk_materiaali_tyyppi <> 'v' THEN
        RAISE EXCEPTION 'toteuma_materiaali_hk ei ole näkymä; palautusta ei suoriteta';
    END IF;

    DROP TRIGGER IF EXISTS tg_estaa_toteuma_hk_kirjoitus
        ON public.toteuma_tehtava_vanha;
    DROP TRIGGER IF EXISTS tg_estaa_toteuma_hk_kirjoitus
        ON public.toteuma_materiaali_vanha;

    DROP VIEW IF EXISTS public.toteuma_tehtava_hk CASCADE;
    DROP VIEW IF EXISTS public.toteuma_materiaali_hk CASCADE;

    -- Partitioitu päätaulu ja siihen kuuluvat partitiot, indeksit sekä
    -- migraatiossa lisätyt avaimet poistetaan yhtenä kokonaisuutena.
    DROP TABLE public.toteuma_tehtava CASCADE;
    DROP TABLE public.toteuma_materiaali CASCADE;

    -- Palautetaan *_vanha taulut, joita käytettiin varmuuskopiona, alkuperäisiksi, ja taas homma pelaa.
    ALTER TABLE public.toteuma_tehtava_vanha
        RENAME TO toteuma_tehtava;
    ALTER TABLE public.toteuma_materiaali_vanha
        RENAME TO toteuma_materiaali;

    DROP TABLE IF EXISTS public.toteuma_hk_siirto_tila CASCADE;

    RAISE NOTICE 'Toteumataulut palautettu migraatiota edeltävään tilaan';
END;
$$;


-- Tilanteen tarkastelu:
--   SELECT * FROM toteuma_hk_siirto_tila;
--   SELECT relname, n_live_tup
--     FROM pg_stat_user_tables
--    WHERE relname LIKE 'toteuma%\_hk\_%'
--    ORDER BY relname;
--
-- Ajo (jokainen omana lauseenaan, ei transaktiolohkon sisällä):
--   CALL partitioi_toteumat_hoitokausittain();
-- Jos ajo keskeytyy ja on tarpeen aloittaa se kokonan alusta, niin poista kirjoitusestot.
-- Tätä ei tarvita, jos kaikki menee hienosti. Ne taulut, jotka on lukittu on nimetty uudelleen ja niitä ei enää käytetä.
-- Mutta jos homma menee pieleen ja partitioituja tauluja ei saadakaan käyttöön, niin on tärkeää avata lukot ja päästää liikenne taas läpi.
--   CALL poista_toteuma_hk_kirjoitusestot();


-- Testikannan palautus onnistuneen vaihdon jälkeen:
--
--   SELECT palauta_toteuma_hk_taulut();
--
-- Funktio tekee seuraavat toimet:
--   1. Poistaa *_hk-näkymät.
--   2. Poistaa uudet partitioidut toteuma_tehtava- ja toteuma_materiaali-taulut
--      sekä niiden partitiot, indeksit ja avaimet CASCADE-optiolla.
--   3. Poistaa vanhoista tauluista migraation kirjoituseston triggerit.
--   4. Nimeää toteuma_tehtava_vanha- ja toteuma_materiaali_vanha-taulut takaisin
--      nimille toteuma_tehtava ja toteuma_materiaali.
--   5. Poistaa migraation siirron tilataulun.
--
-- Funktio keskeyttää palautuksen, jos *_vanha-tauluja ei löydy, nykyiset
-- toteumataulut eivät ole partitioituja tauluja tai *_hk-objektit eivät ole
-- näkymiä. Älä aja tätä tuotantokannassa.
--
-- Palautuksen jälkeen tilan voi tarkistaa esimerkiksi näin: (palauttaa tyhjää, jos palautus onnistui)
--   SELECT to_regclass('public.toteuma_tehtava_vanha'),
--          to_regclass('public.toteuma_materiaali_vanha'),
--          to_regclass('public.toteuma_tehtava_hk'),
--          to_regclass('public.toteuma_materiaali_hk'),
--          to_regclass('public.toteuma_hk_siirto_tila');
--
-- Jos *_hk-tauluja pitää poistaa erikseen ennen onnistunutta tauluvaihtoa,
-- tarkista sen tyyppi ensin. DROP TABLE ei poista näkymää eikä DROP VIEW poista
-- taulua, vaikka komennossa käytettäisiin IF EXISTS -optiota.
--   SELECT c.relname, c.relkind
--     FROM pg_class c
--     JOIN pg_namespace n ON n.oid = c.relnamespace
--    WHERE n.nspname = 'public'
--      AND c.relname IN ('toteuma_tehtava_hk', 'toteuma_materiaali_hk');


