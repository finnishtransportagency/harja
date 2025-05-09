DROP TYPE IF EXISTS suolausalueen_osuus CASCADE;
CREATE TYPE suolausalueen_osuus AS
(
    tyyppi VARCHAR, -- rajoitusalue, muu
    rajoitusalue_id INTEGER,
    osuus        FLOAT
);

-- Funktio: Kohdista_suolapiste_ajoradalle
-- Poistetaan vanha versio. Uudessa viilattu koodia ja täsmennetty nimi muotoon: kohdista_suolapiste_ajoradalle
DROP FUNCTION IF EXISTS lahin_piste_suolattavalla_tiella(piste point);

-- Kohdista_suolapiste_ajoradalle siirtää ajoradalta sivuun osuneen suolapisteen ajoradalle näissä tapauksissa:
-- 1. Suolapiste on epätarkkuuden vuoksi kohdstunut käpyväylälle.
-- Käpyväyliä ei suolata, joten nämä pisteet kuuluvat ajoradalle.
-- 2. Suolapiste on talvihoitoluokitellulla tiestöllä, mutta sivussa ajoradasta.

-- Toteutukseen jäävät heikkoudet:
-- 1. Kohdista_suolapiste_ajoradalle ei täsmennä pisteen sijaintia, jos piste on talvihoitoluokittelemattomalla tiestöllä ajoradasta sivussa.
-- Periaatteessa tällainen täsmennys voitaisiin koodiin lisätä, mutta käytännössä silloin on kysymys kaikkien suolapisteiden
-- kohdistuksen tarkennuksesta, ja se kuuluisi pikemminkin urakoitsijajärjestelmälle.
-- Toki myös kaksi täsmennystä, joka Harja tekee, olisi parempi hoitaa urakoitsijajärjestelmässä.
-- 2. Alkuperäisen pisteen siirtäminen max 25 metriä sen kohdistamiseksi talvihoitoluokitellulle tielle
-- voi vääristää rajoitusalueiden suolapäättelyä. Ei olisi hyvä täsmentää pisteiden sijaintia Harjassa ollenkaan.
-- Parempi ratkaisu olisi kohdistaa piste täsmällisesti tieverkolle jo urakoitsijajärjestelmässä.

-- Miksi sitten Harjassa täsmennetään pisteiden sijaintia?
-- Pisteen käsittely on todettu tässä tarpeelliseksi, koska urakoitsijajärjestelmistä tulee joskus tien sivuun osuneita pisteitä.
-- On mahdollista että piste on syrjässä ajoradalta, ja se vaikuttaa rajoitusalueen hakuun tai rajoitusalueen suolamäärän laskentaan. Tätä ei voi sallia.

CREATE OR REPLACE FUNCTION kohdista_suolapiste_ajoradalle(piste point)
    RETURNS POINT AS
$$
DECLARE
    tasmennetty_piste POINT;
BEGIN
    -- Haetaan talvihoitoluokitelluilta teiltä pisteet
    WITH geometriat AS (SELECT hoitoluokka                                                      AS talvihoitoluokka,
                               st_distance84(geometria, piste::geometry)          AS etaisyys,
                               st_closestpoint(geometria, piste::geometry)::POINT AS lahin_piste
                        FROM hoitoluokka
                        WHERE tietolajitunniste = 'talvihoito'
                          AND st_dwithin(geometria, piste::geometry, 25)
                        ORDER BY etaisyys)
    SELECT lahin_piste FROM geometriat WHERE talvihoitoluokka NOT IN (9,10,11) ORDER BY etaisyys ASC LIMIT 1 INTO tasmennetty_piste;

    IF tasmennetty_piste IS NOT NULL THEN
        RETURN tasmennetty_piste;
    ELSE
        RETURN piste; -- Jos täsmällisempää pistettä ei löytynyt, palautetaan alkuperäinen piste
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Funktiolle välitetään kaksi suolaustoteuman pistettä: alkupiste ja loppupiste, jossa suolankäyttö raportoidaan.
-- Funktio muodostaa pisteiden perusteella tieosoitevälin ja jakaa sen osiin sen mukaan miten välille osuu suolankäytön rajoitusalueita ja rajoittamatonta aluetta.
-- Rajoitusalueet ovat urakka- ja hoitovuosikohtaisia.
-- Funktio palauttaa tiedot erilaisista alueista tieosoitevälillä ja kertoimet, joiden avulla loppupisteessä raportoitu suola voidaan jakaa eri alueiden kesken.
CREATE OR REPLACE FUNCTION pistevalin_suolausalueet(piste1 POINT, piste2 POINT, urakka_id_ INTEGER, hk_alkuvuosi INTEGER)
    RETURNS SETOF suolausalueen_osuus AS
$$
DECLARE
    ra                   rajoitusalue;
    tieosoitevali        tr_osoite;
    osuus                FLOAT;
    jaljella_osuutta     FLOAT;
    piste1_              POINT;
    piste2_              POINT;
BEGIN
    -- Täsmennä pisteen sijaintia siltä varalta, että GPS-piste on osunut osuisivat pyörätielle tai rajoitusalueesta sivuun.
    SELECT kohdista_suolapiste_ajoradalle(piste1) INTO piste1_;
    SELECT kohdista_suolapiste_ajoradalle(piste2) INTO piste2_;

    jaljella_osuutta := 1; -- Vähennetään kokonaismäärästä rajoitusalueet, loput suolasta on rajoittamattomien alueiden suolaa.

    -- Pisteen ei kuuluisi palautua nullina funktiolta kohdista_suolapiste_ajoradalle. Joko se kohdistuu tarkemmin tieverkolle ja siirtyy hieman
    -- tai saadaan takaisin sama piste. Jos SUOLATOTEUMA_REITTIPISTE-taulusta puuttuu suolapisteitä, jotka ovat
    -- toteuman TOTEUMAN_REITTIPISTEET-tiedoissa, kyseessä on virhetilanne, joka kannattaa selvittää.
    IF (piste1_ IS NULL OR piste2_ IS NULL) THEN
        RETURN;
    END IF;

    SELECT * FROM yrita_tierekisteriosoite_pisteille2(piste1_::geometry, piste2_::geometry, 1) INTO tieosoitevali;

    IF (tieosoitevali IS DISTINCT FROM NULL AND tieosoitevali.geometria IS DISTINCT FROM NULL)THEN
        -- Käsitellään ensin toteuman suoritusajankohdan aikana voimassa olevat rajoitusalueet ja niille osuva suola.
        -- Jos rajoitusalueet ovat päällekkäin, sama suola tulee lasketuksi suolatoteuman reittipisteisiin kahdesti.
        -- Päällekkäisiä rajoituksia ei siis saisi olla voimassa.
        FOR ra IN
            SELECT *
            FROM rajoitusalue alue
                     JOIN rajoitusalue_rajoitus rajoitus ON alue.id = rajoitus.rajoitusalue_id
            WHERE (alue.tierekisteriosoite).tie = tieosoitevali.tie
              AND st_dwithin(tieosoitevali.geometria, alue.sijainti, 1)
              AND alue.urakka_id = urakka_id_
              AND rajoitus.hoitokauden_alkuvuosi = hk_alkuvuosi
              AND alue.poistettu = FALSE
              AND rajoitus.poistettu = FALSE
            LOOP
                IF st_geometrytype(tieosoitevali.geometria) = 'ST_Point' THEN
                    -- Tievälin ja päällekkäisen osuuden pituus on nolla, jos tienväli on piste - sekin on periaatteessa mahdollista.
                    -- Jos piste on rajoitusalueella, rajoitusalueen osuus on 1, koska piste on kokonaan rajoitusalueella.
                    -- Jos piste ei ole rajoitusalueella, osuus on 0.
                    IF st_intersects(st_buffer(ra.sijainti, 1, 'endcap=flat'), tieosoitevali.geometria) THEN
                        osuus := 1;
                    ELSE
                        osuus:= 0;
                    END IF;
                ELSE
                    SELECT st_length(st_intersection(st_buffer(ra.sijainti, 1, 'endcap=flat'), tieosoitevali.geometria)) /
                           st_length(tieosoitevali.geometria)
                    INTO osuus;
                END IF;
                jaljella_osuutta := jaljella_osuutta - osuus;
                RETURN NEXT ('rajoitusalue', ra.id, osuus)::suolausalueen_osuus;
            END LOOP;
    END IF;

    -- Jäljelle jää se osuus, joka ei kuulu rajoitetuille alueille. Palautetaan jäljelle jäävä osuus myös silloin, kun tieosoitteella ei ole geometriaa.
    IF (jaljella_osuutta > 0) THEN
        RETURN NEXT ('muu', NULL, jaljella_osuutta)::suolausalueen_osuus;
    END IF;

    RETURN;
END;
$$ LANGUAGE plpgsql;

-- Päivitetty triggeri. Ajetaan aina, kun toteuma_reittipiste -tauluun tehdään muutos (update, delete, insert).
-- Huom. Toteuman poistaminen ei laukaise tätä triggeriä. Kun toteuma poistetaan, siihen liittyvät pisteet jäävät suolatoteuman reittipisteet-tauluun.
CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS
$$
DECLARE
    m                               reittipiste_materiaali;
    rp                              reittipistedata;
    suolamateriaalikoodit           INTEGER[];
    edellinen_rp                    reittipistedata;
    suolausalue                     suolausalueen_osuus;

    reittitoteuma                   RECORD;
    urakkaid                        INTEGER;
    aloitusaika                     TIMESTAMP;
    toteuma_poistettu               BOOLEAN;
    hoitokauden_alkuvuosi           INTEGER;
BEGIN
    -- Haetaan materiaalikoodit talteen
    SELECT ARRAY_AGG(id)
    FROM materiaalikoodi
    WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'formiaatti')
    INTO suolamateriaalikoodit;

    -- Haetaan tarvittavat tiedot toteumasta
    SELECT urakka, alkanut, poistettu FROM toteuma WHERE id = new.toteuma INTO reittitoteuma;
    urakkaid := reittitoteuma.urakka;
    aloitusaika := reittitoteuma.alkanut;
    toteuma_poistettu := reittitoteuma.poistettu;

    hoitokauden_alkuvuosi := hoitokauden_alkuvuosi(aloitusaika);

    -- Putsataan aiemmin lasketut suolatoteuman reittipisteet pois ennen uudelleenlaskentaa.
    IF ((TG_OP = 'UPDATE' OR TG_OP = 'DELETE') OR toteuma_poistettu = TRUE) THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma = OLD.toteuma;
    END IF;

    -- Jos toteuman_reittipisteet-taulun rivi poistettiin, ei lasketa rivin tiedoista enää suolapisteitä.
    -- Jos toteuma, johon rivi liittyy, on merkitty poistetuksi, ei lasketa toteuman_reittipisteet-taulun tiedoista enää suolapisteitä.
    IF (TG_OP != 'DELETE' AND toteuma_poistettu = FALSE) THEN
        FOREACH rp IN ARRAY NEW.reittipisteet
            LOOP
                FOREACH m IN ARRAY rp.materiaalit
                    LOOP
                        IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                            IF edellinen_rp IS NULL THEN
                                edellinen_rp.sijainti = rp.sijainti;
                            END IF;
                            FOR suolausalue IN SELECT tyyppi, rajoitusalue_id, osuus
                                               FROM pistevalin_suolausalueet(edellinen_rp.sijainti, rp.sijainti,
                                                                             urakkaid, hoitokauden_alkuvuosi)
                                LOOP
                                    -- Lisätään rajoitusalueisiin liittyvät määrät
                                    IF suolausalue.rajoitusalue_id IS DISTINCT FROM NULL AND
                                       suolausalue.tyyppi = 'rajoitusalue' THEN
                                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi,
                                                                              maara, rajoitusalue_id, luotu)
                                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                m.maara * suolausalue.osuus, suolausalue.rajoitusalue_id,
                                                current_timestamp);
                                    END IF;

                                    -- Lisätään rajoittamattomien alueiden määrät
                                    IF suolausalue.tyyppi = 'muu' THEN
                                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi,
                                                                              maara, rajoitusalue_id, luotu)
                                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi,
                                                m.maara * suolausalue.osuus, NULL, current_timestamp);
                                    END IF;
                                END LOOP;
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

-- Funktiota käytetään päivittämään kuluvan hoitokauden suolatoteumien pisteet silloin, kun rajoitusaluetta muokataan.
-- Käytä aikarajauksessa alkanut-saraketta, älä luotu-saraketta. Kutsuja rajaa korjauksen hoitokauden aikana ajettuihin toteumiin.
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
    WHERE toteuma IN (SELECT id FROM toteuma WHERE urakka = urakkaid AND alkanut BETWEEN alkaa AND loppuu);

    -- Haetaan toteumat ja niiden reittipisteet urakan ja aikavälin puitteissa. Käsitellään vain ei poistettuja
    FOR loydetyt_toteuman_reittipisteet IN
        SELECT tr.reittipisteet, t.id AS toteuma, hoitokauden_alkuvuosi(t.alkanut) AS hoitokauden_alkuvuosi
        FROM toteuma t
                 JOIN toteuman_reittipisteet tr ON t.id = tr.toteuma
        WHERE t.urakka = urakkaid
          AND t.alkanut BETWEEN alkaa AND loppuu
          AND t.poistettu IS FALSE

        LOOP
        -- Loopataan löydetyt reittipisteet läpi
        -- Ja jokaiselle löydetylle reittipiste arraylle pyöritetään oma looppi, jossa itse lisääminen suolatoteuma_reittpiste - tauluun tapahtuu
            edellinen_rp := NULL; -- Nollaa edellinen reittipiste, kun aloitetaan käsittelemään uutta toteumaa.
            FOREACH rp IN ARRAY loydetyt_toteuman_reittipisteet.reittipisteet
                LOOP
                    FOREACH m IN ARRAY rp.materiaalit
                        LOOP
                            IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                                -- Jos suolapiste on reitin ensimmäinen ja siihen on raportoitu suolaa, on koko suolamäärä laskettava samaan pisteeseen,
                                -- joten ensimmäisen pisteen kohdalla edellinen sijainti on sama kuin pisteen sijainti.
                                IF edellinen_rp IS NULL THEN
                                    edellinen_rp.sijainti = rp.sijainti;
                                END IF;
                                FOR suolausalue IN SELECT tyyppi, rajoitusalue_id, osuus
                                                   FROM pistevalin_suolausalueet(edellinen_rp.sijainti, rp.sijainti,
                                                                                 urakkaid,
                                                                                 loydetyt_toteuman_reittipisteet.hoitokauden_alkuvuosi)
                                    LOOP
                                        IF suolausalue.rajoitusalue_id IS DISTINCT FROM NULL AND
                                           suolausalue.tyyppi = 'rajoitusalue' THEN
                                            INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti,
                                                                                  materiaalikoodi,
                                                                                  maara,
                                                                                  rajoitusalue_id)
                                            VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti,
                                                    m.materiaalikoodi, m.maara * suolausalue.osuus,
                                                    suolausalue.rajoitusalue_id);
                                        END IF;

                                        IF suolausalue.tyyppi = 'muu' THEN
                                            INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti,
                                                                                  materiaalikoodi,
                                                                                  maara,
                                                                                  rajoitusalue_id)
                                            VALUES (loydetyt_toteuman_reittipisteet.toteuma, rp.aika, rp.sijainti,
                                                    m.materiaalikoodi, m.maara * suolausalue.osuus, NULL);
                                        END IF;
                                    END LOOP;
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
