-- Tiedosto nimetty väärällä numerolla, koska migraatioon on pitkä aika ja näin vältytään konflikteilta
-- Lisätään urakka -taulun käyttämään sopimustyyppi tietueeseen tiedot mhu ja mhu+ vaativien hoitourakoiden erottelemiseksi.
ALTER TYPE sopimustyyppi ADD VALUE 'mhu';
ALTER TYPE sopimustyyppi ADD VALUE 'mhu+';


-- Lisätään välikatselmuksen päätöksiin liittyvät taulut
CREATE TABLE paatos_lupaus
(
    id                    SERIAL PRIMARY KEY,
    urakkaid              INTEGER        NOT NULL,
    hoitokauden_alkuvuosi INTEGER        NOT NULL,
    tyyppi                VARCHAR(255)   NOT NULL, -- bonus, sanktio, taytetty
    tavoitehinta          NUMERIC(10, 2) NOT NULL,
    tarjous_tavoitehinta  NUMERIC(10, 2) NOT NULL,
    luvatut_pisteet       INTEGER        NOT NULL,
    toteutuneet_pisteet   INTEGER        NOT NULL,
    lupausbonus           NUMERIC(10, 2),
    lupaussanktio         NUMERIC(10, 2),
    bonusprosentti        NUMERIC(4, 2),
    sanktioprosentti      NUMERIC(4, 2),
    erilliskustannus_id   INTEGER,
    sanktio_id            INTEGER,
    luotu                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                 INTEGER        NOT NULL,
    poistettu             BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja              INTEGER,
    FOREIGN KEY (erilliskustannus_id) REFERENCES erilliskustannus (id),
    FOREIGN KEY (sanktio_id) REFERENCES sanktio (id),
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_tavoitehinnan_muutos
(
    id                    SERIAL PRIMARY KEY,
    urakkaid              INTEGER        NOT NULL,
    hoitokauden_alkuvuosi INTEGER        NOT NULL,
    versio                VARCHAR(255)   NOT NULL, -- 1/2
    tavoitehinta          NUMERIC(10, 2) NOT NULL,
    kattohinta            NUMERIC(10, 2),
    luotu                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                 INTEGER        NOT NULL,
    poistettu             BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja              INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_tavoitehinta_ylitys
(
    id                       SERIAL PRIMARY KEY,
    urakkaid                 INTEGER        NOT NULL,
    hoitokauden_alkuvuosi    INTEGER        NOT NULL,
    versio                   VARCHAR(255)   NOT NULL, -- 1/2/3
    tavoitehinta             NUMERIC(10, 2) NOT NULL,
    toteutuneet_kustannukset NUMERIC(10, 2) NOT NULL,
    ylityksen_maara          NUMERIC(10, 2),
    tilaajan_prosentti       INTEGER,
    urakoitsijan_prosentti   INTEGER,
    tilaaja_maksaa           NUMERIC(10, 2),
    urakoitsija_maksaa       NUMERIC(10, 2),
    siirto                   NUMERIC(10, 2),
    kulu_id                  INTEGER,
    luotu                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                    INTEGER        NOT NULL,
    poistettu                BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                 INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (kulu_id) REFERENCES kulu (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_tavoitehinta_alitus
(
    id                             SERIAL PRIMARY KEY,
    urakkaid                       INTEGER        NOT NULL,
    hoitokauden_alkuvuosi          INTEGER        NOT NULL,
    tavoitehinta                   NUMERIC(10, 2) NOT NULL,
    toteutuneet_kustannukset       NUMERIC(10, 2) NOT NULL,
    alituksen_maara                NUMERIC(10, 2),
    siirron_maara                  NUMERIC(10, 2), -- Jos siirretään seuraavalle vuodelle niin tähän se summa. Viimeisenä vuotena ei voida enää siirtää
    tavoitepalkkio                 NUMERIC(10, 2),
    tavoitepalkkion_maksuprosentti NUMERIC(4, 2),
    kulu_id                        INTEGER,
    luotu                          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                          INTEGER        NOT NULL,
    poistettu                      BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                       INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (kulu_id) REFERENCES kulu (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_kattohinta
(
    id                       SERIAL PRIMARY KEY,
    urakkaid                 INTEGER        NOT NULL,
    hoitokauden_alkuvuosi    INTEGER        NOT NULL,
    kattohinta               NUMERIC(10, 2) NOT NULL,
    toteutuneet_kustannukset NUMERIC(10, 2) NOT NULL,
    ylityksen_maara          NUMERIC(10, 2),
    urakoitsija_maksaa       NUMERIC(10, 2),
    siirrettava_maara        NUMERIC(10, 2),
    kulu_id                  INTEGER,
    luotu                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                    INTEGER        NOT NULL,
    poistettu                BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                 INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (kulu_id) REFERENCES kulu (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_hoitokauden_lopun_hinta
(
    id                               SERIAL PRIMARY KEY,
    urakkaid                         INTEGER        NOT NULL,
    hoitokauden_alkuvuosi            INTEGER        NOT NULL,
    tavoitehinta_ennen               NUMERIC(10, 2) NOT NULL,
    tavoitehinta_jalkeen             NUMERIC(10, 2) NOT NULL,
    tavoitehinnan_muutokset          NUMERIC(10, 2) NOT NULL,
    hoitokauden_lopun_indeksikorjaus NUMERIC(10, 2),
    kattohinta                       NUMERIC(10, 2),
    luotu                            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                            INTEGER        NOT NULL,
    poistettu                        BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                         INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TYPE indeksikorjauskuukausi AS
(
    kuukausi    TEXT,
    indeksiluku NUMERIC(10, 2)
);

CREATE TABLE paatos_hoitokauden_indeksikorjaus
(
    id                               SERIAL PRIMARY KEY,
    urakkaid                         INTEGER        NOT NULL,
    hoitokauden_alkuvuosi            INTEGER        NOT NULL,
    tavoitehinta                     NUMERIC(10, 2) NOT NULL,  -- Hoitokauden lopun indeksikorjattu tavoitehinta
    tavoitehinnan_muutokset          NUMERIC(10, 2) NOT NULL,  -- Summa tavoitehinnan muutosten kokonaisuudesta
    tavoitehinta_ennen               NUMERIC(10, 2) NOT NULL,  --Hoitokauden lopun tavoitehinta ennen hoitokauden lopun indeksikorjausta. Eli tavoitehinta + tavoitehinnan muutokset
    hoitokauden_kuukaudet            indeksikorjauskuukausi[], -- Kuukauden nimi ja kuukauden pisteluku
    kuukausien_keskiarvo             NUMERIC(10, 2),           -- Kuukausien keskiarvo
    alkuperaisen_pisteluvun_kuukausi TEXT,                     -- Edellisen hoitovuoden elokuu, eli laitetaan muotoon "elokuu 2021"
    alkuperainen_pisteluku           NUMERIC(10, 1),           -- Edellisen hoitovuoden syyskuun pisteluku. Esim 105.6
    pistelukujen_muutos              NUMERIC(10, 1),
    indeksikorotuksen_prosenttiosuus NUMERIC(10, 1),           -- 2% ylittävä osa
    hoitokauden_lopun_indeksikorjaus NUMERIC(10, 2),           -- Kokonaissumma indeksikorjauksesta
    luotu                            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                            INTEGER        NOT NULL,
    poistettu                        BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                         INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_hoidonjohtopalkkio
(
    id                        SERIAL PRIMARY KEY,
    urakkaid                  INTEGER        NOT NULL,
    hoitokauden_alkuvuosi     INTEGER        NOT NULL,
    tavoitehinta              NUMERIC(10, 2) NOT NULL, -- Hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia
    tarjouksen_tavoitehinta   NUMERIC(10, 2) NOT NULL, -- Tavoitehinta, joka tulee tarjousdokumenteista eikä esim kustiksesta
    hoidonjohtopalkkio        NUMERIC(10, 2),          -- Hoitokauden indeksikorjattu hoidonjohtopalkkio
    muutosprosentti           NUMERIC(10, 1) NOT NULL, -- Kuinka monta prosenttia tavoitehinta on suurempi, kuin tarjouksen_tavoitehinta
    hoidonjohtopalkkio_muutos NUMERIC(10, 2) NOT NULL, -- Kuinka monta euroa hoidonjohtopalkkion pitää muuttua verrattuna alkuperäiseen suunnitelmaan
    kulu_id                   INTEGER,                 -- Muuttuneesta hoidonjohtopalkkiosta tehdään kulu
    luotu                     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                     INTEGER        NOT NULL,
    poistettu                 BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                  INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_poytakirjan_raportti
(
    id                    SERIAL PRIMARY KEY,
    urakkaid              INTEGER   NOT NULL,
    hoitokauden_alkuvuosi INTEGER   NOT NULL,
    tarkistettu           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Raportit tarkistettu päivämäärä, eli päätöksen luontihetki
    luotu                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                 INTEGER   NOT NULL,
    poistettu             BOOLEAN   NOT NULL DEFAULT FALSE,
    poistaja              INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

-- Urakoilla on paljon parametreja, joilla mm. päätöksien tietoja muutellaan.
-- Tehdään alustava taulu, jota on helppo laajentaa eri parametrien suhteen
CREATE TABLE urakka_parametrit
(
    id                                                 SERIAL PRIMARY KEY,
    urakkaid                                           INTEGER   NOT NULL,
    --toimenkuvat                                        TEXT[],        -- Sopimusvastaava, Vastuunalainen työnjohtaja, Päätöiminen apulainen (talvikausi) jne.
    --hoitokauden_alun_indeksin_kaava                    TEXT,          -- Indeksikorjauksen kaava hoitokauden alussa
    --hoitokauden_lopun_indeksikorjaus_kaytossa          BOOLEAN,       -- Onko hoitokauden lopun indeksikorjaus käytössä
    --hoitokauden_lopun_indeksin_kaava                   TEXT,          -- Indeksikorjauksen kaava hoitokauden lopussa
    --indeksin_kustannukset                              TEXT[],        -- Mitkä kustannukset indeksikorjataan, esim: sanktiot, bonukset, tavoitehinnan muutokset
    lupauspaatoksen_bonusprosentti                     DECIMAL(4, 2), -- Luvatun pistemäärän ylittävää pistettä kohden maksettava bonusprosentti tarjouksen tavoitehinnasta
    lupauspaatoksen_sanktioprosentti                   DECIMAL(4, 2), -- Luvatun pistemäärän alittavaa pistettä kohden maksettava sanktioprosentti tarjouksen tavoitehinnasta
    tavoitehinnan_ylityksen_kustannusten_jakoprosentti INTEGER,       -- Kuinka monta prosenttia urakoitsija maksaa ylityksen kustannuksista
    --hoitokauden_lopun_tavoitehinta_kaava               TEXT,          -- Kaava hoitokauden lopun tavoitehinnan laskemiseen
    --laskutusraja_kaytossa                              BOOLEAN,       -- Onko laskutusraja käytössä
    --laskutusrajan_ylitys_kaava                         TEXT,          -- Kaava laskutusrajan sanktion laskemiseen
    --kattohinta_laskukaava                              TEXT,          -- Kaava kattohinnan laskemiseen, onko 10% vai lasketaanko käsin
    --kattohintaylityksen_siirron_maaran_rajoitus        BOOLEAN,       -- Onko kattohintaylityksen siirron määrälle rajoitus
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti    DECIMAL(4, 2), -- Tavoitehinnan ylityksen maksuprosentti tilaajalle (kattohintaan asti)
    tavoitepalkkion_maksuprosentti                     DECIMAL(4, 2), -- Tavoitepalkkion maksuprosentti. Voi olla esim 30% tavoitehinnan alituksesta tai 75% alennuksesta
    tavoitepalkkion_maksimi                            DECIMAL(4, 2), -- Tavoitepalkkion maksimi määrä prosentteina
    maaratyt_sanktiot                                  TEXT[],        -- Mitkä sanktiot on määrätty käyttöön
    maaratyt_bonukset                                  TEXT[],        -- Mitkä bonukset on määrätty käyttöön
    sanktion_kaava                                     TEXT,          -- Kaava sanktioiden laskemiseen
    luotu                                              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokattu                                           TIMESTAMP          DEFAULT CURRENT_TIMESTAMP,
    luoja                                              INTEGER   NOT NULL,
    muokkaaja                                          INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (muokkaaja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

-- Pyritään täyttämään taulu mahdollisimman hyvin alkuun ja hallintapaneelista sitten loput
CREATE OR REPLACE FUNCTION aseta_tai_paivita_urakka_parametrit_urakalle(urakkaid_ INT) RETURNS VOID AS
$$
DECLARE
    urakan_tiedot                                                RECORD;
    lupauspaatoksen_bonusprosentti_2019_2024                     DECIMAL(10, 2) := 0.13;
    lupauspaatoksen_bonusprosentti_2025_                         DECIMAL(10, 2) := 0.08;
    lupauspaatoksen_sanktioprosentti_2019_2024                   DECIMAL(10, 2) := 0.33;
    lupauspaatoksen_sanktioprosentti_2025_                       DECIMAL(10, 2) := 0.18;
    bonusprosentti                                               DECIMAL(4, 2);
    sanktioprosentti                                             DECIMAL(4, 2);
    tavoitepalkkion_maksuprosentti_2019_2024                     DECIMAL(4, 2)  := 30;
    tavoitepalkkion_maksuprosentti_2025_                         DECIMAL(4, 2)  := 75;
    tavoitepalkkioprosentti                                      DECIMAL(4, 2);
    tavoitepalkkionmaxprosentti                                  DECIMAL(4, 2)  := 3; -- Tällä hetkellä kaikilla on 3%
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2019_2024    DECIMAL(4, 2)  := 70;
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2024_vaativa DECIMAL(4, 2)  := 50;
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2025_        DECIMAL(4, 2)  := 25;
    tavoitehinnan_ylityksen_maksuprosentti                       DECIMAL(4, 2);
    luojaid                                                      INTEGER        := (SELECT id
                                                                                    FROM kayttaja
                                                                                    WHERE kayttajanimi = 'Integraatio');
BEGIN
    -- Haetaan kaikki urakat ja lisätään niiden perustiedot urakka_parametrit tauluun
    for urakan_tiedot in (SELECT * FROM urakka WHERE id = urakkaid_)
        LOOP
            bonusprosentti := (CASE
                                   WHEN urakan_tiedot.alkupvm < '2024-10-02'
                                       THEN lupauspaatoksen_bonusprosentti_2019_2024
                                   ELSE lupauspaatoksen_bonusprosentti_2025_ END);
            sanktioprosentti := (CASE
                                     WHEN urakan_tiedot.alkupvm < '2024-10-02'
                                         THEN lupauspaatoksen_sanktioprosentti_2019_2024
                                     ELSE lupauspaatoksen_sanktioprosentti_2025_ END);
            tavoitepalkkioprosentti := (CASE
                                            WHEN urakan_tiedot.alkupvm < '2024-10-02'
                                                THEN tavoitepalkkion_maksuprosentti_2019_2024
                                            ELSE tavoitepalkkion_maksuprosentti_2025_ END);
            tavoitehinnan_ylityksen_maksuprosentti := (CASE
                                                           WHEN urakan_tiedot.alkupvm < '2024-10-02' AND
                                                                urakan_tiedot.sopimustyyppi != 'mhu+'
                                                               THEN tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2019_2024
                                                           WHEN urakan_tiedot.alkupvm > '2024-10-02' AND
                                                                urakan_tiedot.sopimustyyppi != 'mhu+'
                                                               THEN tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2025_
                                                           WHEN urakan_tiedot.alkupvm > '2024-10-02' AND
                                                                urakan_tiedot.sopimustyyppi = 'mhu'
                                                               THEN tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2024_vaativa
                -- Kaikille muille defaulttina 70%
                                                           ELSE tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2019_2024 END);

            -- Tarkistetaan, että löytyykö rivi jo taulusta
            IF EXISTS(SELECT 1 FROM urakka_parametrit WHERE urakkaid = urakan_tiedot.id)
            THEN
                UPDATE urakka_parametrit
                SET lupauspaatoksen_bonusprosentti                  = bonusprosentti,
                    lupauspaatoksen_sanktioprosentti                = sanktioprosentti,
                    tavoitepalkkion_maksuprosentti                  = tavoitepalkkioprosentti,
                    tavoitepalkkion_maksimi                         = tavoitepalkkionmaxprosentti,
                    tavoitehinnan_ylityksen_tilaajan_maksuprosentti = tavoitehinnan_ylityksen_maksuprosentti,
                    muokattu                                        = NOW(),
                    muokkaaja                                       = luojaid
                WHERE urakkaid = urakan_tiedot.id;
            ELSE
                INSERT INTO urakka_parametrit (urakkaid, lupauspaatoksen_bonusprosentti,
                                               lupauspaatoksen_sanktioprosentti, tavoitepalkkion_maksuprosentti,
                                               tavoitepalkkion_maksimi,
                                               tavoitehinnan_ylityksen_tilaajan_maksuprosentti, luoja, luotu)
                VALUES (urakan_tiedot.id, bonusprosentti, sanktioprosentti,
                        tavoitepalkkioprosentti, tavoitepalkkionmaxprosentti,
                        tavoitehinnan_ylityksen_maksuprosentti, luojaid, NOW());
            END IF;

            INSERT INTO urakka_parametrit (urakkaid, lupauspaatoksen_bonusprosentti,
                                           lupauspaatoksen_sanktioprosentti, tavoitepalkkion_maksuprosentti,
                                           tavoitepalkkion_maksimi,
                                           tavoitehinnan_ylityksen_tilaajan_maksuprosentti, luoja, luotu)
            VALUES (urakan_tiedot.id, bonusprosentti, sanktioprosentti,
                    tavoitepalkkioprosentti, tavoitepalkkionmaxprosentti,
                    tavoitehinnan_ylityksen_maksuprosentti, luojaid, NOW());
        end LOOP;
END
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION aseta_urakka_parametrit() RETURNS VOID AS
$$
DECLARE
    urakkaid INT;

BEGIN
    for urakkaid in (SELECT id FROM urakka WHERE tyyppi IN ('hoito', 'teiden-hoito'))
        LOOP
            PERFORM aseta_tai_paivita_urakka_parametrit_urakalle(urakkaid);
        end loop;
END
$$ LANGUAGE plpgsql;


select aseta_urakka_parametrit();
