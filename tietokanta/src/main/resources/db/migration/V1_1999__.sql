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
    tavoitehinta          NUMERIC(12, 2) NOT NULL,
    tarjous_tavoitehinta  NUMERIC(12, 2) NOT NULL,
    luvatut_pisteet       INTEGER        NOT NULL,
    toteutuneet_pisteet   INTEGER        NOT NULL,
    lupausbonus           NUMERIC(10, 2),
    lupaussanktio         NUMERIC(10, 2),
    bonusprosentti        NUMERIC(4, 2),
    sanktioprosentti      NUMERIC(4, 2),
    indeksi               TEXT,
    indeksikorotus        NUMERIC(10, 2),
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
    tavoitehinta          NUMERIC(12, 2) NOT NULL,
    kattohinta            NUMERIC(12, 2),
    muokkaa_kattohinta    BOOLEAN        NOT NULL DEFAULT FALSE,
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
    tavoitehinta             NUMERIC(12, 2) NOT NULL,
    toteutuneet_kustannukset NUMERIC(12, 2) NOT NULL,
    ylityksen_maara          NUMERIC(10, 2),
    tilaajan_prosentti       INTEGER,
    urakoitsijan_prosentti   INTEGER,
    tilaaja_maksaa           NUMERIC(10, 2),
    urakoitsija_maksaa       NUMERIC(10, 2),
    siirto                   NUMERIC(10, 2),
    kulu_id                  INTEGER,
    viimeinen_hoitokausi     BOOLEAN,
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
    hoitokauden_alun_tavoitehinta  NUMERIC(12, 2) NOT NULL,
    hoitokauden_lopun_tavoitehinta NUMERIC(12, 2) NOT NULL,
    toteutuneet_kustannukset       NUMERIC(12, 2) NOT NULL,
    alituksen_maara                NUMERIC(10, 2),
    siirron_maara                  NUMERIC(10, 2), -- Jos siirretään seuraavalle vuodelle niin tähän se summa. Viimeisenä vuotena ei voida enää siirtää
    tavoitepalkkio                 NUMERIC(10, 2),
    tavoitepalkkion_maksuprosentti NUMERIC(4, 2),
    kulu_id                        INTEGER,
    viimeinen_hoitokausi           BOOLEAN,
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
    id                        SERIAL PRIMARY KEY,
    urakkaid                  INTEGER        NOT NULL,
    hoitokauden_alkuvuosi     INTEGER        NOT NULL,
    kattohinta                NUMERIC(12, 2) NOT NULL,
    toteutuneet_kustannukset  NUMERIC(12, 2) NOT NULL,
    ylityksen_maara           NUMERIC(10, 2),
    urakoitsija_maksaa        NUMERIC(10, 2),
    siirrettava_maara         NUMERIC(10, 2),
    maksimi_siirrettava_maara NUMERIC(10, 2), -- -25 alkaen enintään 3% kattohinnasta voidaan siirtää. Loput on maksettava kuluna.
    siirtorajoitus_prosentti  NUMERIC(4, 2),  -- Esim. jos tarkoitetaan, että rajoitus on 3%, niin tänne tallentuu 0.03
    kulu_id                   INTEGER,
    viimeinen_hoitokausi      BOOLEAN,
    luotu                     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                     INTEGER        NOT NULL,
    poistettu                 BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                  INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (kulu_id) REFERENCES kulu (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);

CREATE TABLE paatos_hoitokauden_lopun_hinta
(
    id                                       SERIAL PRIMARY KEY,
    urakkaid                                 INTEGER        NOT NULL,
    hoitokauden_alkuvuosi                    INTEGER        NOT NULL,
    tavoitehinta_ennen                       NUMERIC(12, 2) NOT NULL,
    tavoitehinta_jalkeen                     NUMERIC(12, 2) NOT NULL,
    tavoitehinnan_muutokset                  NUMERIC(10, 2) NOT NULL,
    hoitokauden_lopun_indeksikorjaus         NUMERIC(10, 2),
    kattohinta                               NUMERIC(10, 2),
    kattohintakerroin                        NUMERIC(4, 2),
    lisaa_tavoitehintaan_lopunindeksikorjaus BOOLEAN,
    luotu                                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                                    INTEGER        NOT NULL,
    poistettu                                BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                                 INTEGER,
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
    tavoitehinta                     NUMERIC(12, 2) NOT NULL,  -- Hoitokauden lopun indeksikorjattu tavoitehinta
    tavoitehinnan_muutokset          NUMERIC(10, 2) NOT NULL,  -- Summa tavoitehinnan muutosten kokonaisuudesta
    tavoitehinta_ennen               NUMERIC(12, 2) NOT NULL,  --Hoitokauden lopun tavoitehinta ennen hoitokauden lopun indeksikorjausta. Eli tavoitehinta + tavoitehinnan muutokset
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
    tavoitehinta              NUMERIC(12, 2) NOT NULL, -- Hoitovuoden lopun tavoitehinta ilman indeksitarkistuksia
    tarjouksen_tavoitehinta   NUMERIC(12, 2) NOT NULL, -- Tavoitehinta, joka tulee tarjousdokumenteista eikä esim kustiksesta
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
    id                                                  SERIAL PRIMARY KEY,
    urakkaid                                            INTEGER   NOT NULL,
    indeksi_kaytossa_sanktiolla                         BOOLEAN,       -- Onko indeksikorjaus käytössä sanktioilla. -19/20 alkavilla urakoilla käytössä, muilla ei
    indeksi_kaytossa_bonuksella                         BOOLEAN,       -- Onko indeksikorjaus käytössä bonuksella. -19/20 alkavilla urakoilla käytössä asiakastyytyväisyysbonuksella, muilla ei
    --toimenkuvat                                        TEXT[],        -- Sopimusvastaava, Vastuunalainen työnjohtaja, Päätöiminen apulainen (talvikausi) jne.
    --hoitokauden_alun_indeksin_kaava                    TEXT,          -- Indeksikorjauksen kaava hoitokauden alussa
    --hoitokauden_lopun_indeksikorjaus_kaytossa          BOOLEAN,       -- Onko hoitokauden lopun indeksikorjaus käytössä
    --hoitokauden_lopun_indeksin_kaava                   TEXT,          -- Indeksikorjauksen kaava hoitokauden lopussa
    --indeksin_kustannukset                              TEXT[],        -- Mitkä kustannukset indeksikorjataan, esim: sanktiot, bonukset, tavoitehinnan muutokset
    lupauspaatoksen_bonusprosentti                      DECIMAL(4, 2), -- Luvatun pistemäärän ylittävää pistettä kohden maksettava bonusprosentti tarjouksen tavoitehinnasta
    lupauspaatoksen_sanktioprosentti                    DECIMAL(4, 2), -- Luvatun pistemäärän alittavaa pistettä kohden maksettava sanktioprosentti tarjouksen tavoitehinnasta

    --hoitokauden_lopun_tavoitehinta_kaava               TEXT,          -- Kaava hoitokauden lopun tavoitehinnan laskemiseen
    --laskutusraja_kaytossa                              BOOLEAN,       -- Onko laskutusraja käytössä
    --laskutusrajan_ylitys_kaava                         TEXT,          -- Kaava laskutusrajan sanktion laskemiseen
    --kattohinta_laskukaava                              TEXT,          -- Kaava kattohinnan laskemiseen, onko 10% vai lasketaanko käsin
    lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus BOOLEAN, -- -24 alkaen hoitovuoden lopun tavoitehintaan lisätään myös hiotovuoden lopun indeksikorjaus
    hoitokauden_lopun_kattohinta_kerroin                DECIMAL(4, 2), -- Kaava kattohinnan laskemiseen, voi olla 1.1 tai 1.2 kertaa hoitovuoden lopun tavoitehinta, joka sekin lasketaan eri tavalla eri vuosina
    muokkaa_kattohinta_kasin                            BOOLEAN,       -- -19/20 alkavilla urakoilla kattohinta annetaan käsin, muilla 10% tavoitehinnasta
    kattohintaylityksen_siirron_prosenttirajoitus       DECIMAL(4, 2), -- Esim 0.03 (prosenttia) vuonna -25 alkavilla urakoilla
    tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti INTEGER,       -- Kuinka monta prosenttia urakoitsija maksaa ylityksen kustannuksista
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti     DECIMAL(4, 2), -- Tavoitehinnan ylityksen maksuprosentti tilaajalle (kattohintaan asti)
    tavoitepalkkion_maksuprosentti                      DECIMAL(4, 2), -- Tavoitepalkkion maksuprosentti. Voi olla esim 30% tavoitehinnan alituksesta tai 75% alennuksesta
    tavoitepalkkion_maksimi                             DECIMAL(4, 2), -- Tavoitepalkkion maksimi määrä prosentteina
    --maaratyt_sanktiot                                   TEXT[],        -- Mitkä sanktiot on määrätty käyttöön
    --maaratyt_bonukset                                   TEXT[],        -- Mitkä bonukset on määrätty käyttöön
    --sanktion_kaava                                      TEXT,          -- Kaava sanktioiden laskemiseen
    luotu                                               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokattu                                            TIMESTAMP          DEFAULT CURRENT_TIMESTAMP,
    luoja                                               INTEGER   NOT NULL,
    muokkaaja                                           INTEGER,
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
    indeksi_kaytossa                                             BOOLEAN;
    kattohintaylityksen_siirron_prosenttirajoitus                DECIMAL(4, 2);
    muokkaa_kattohinta_kasin                                     BOOLEAN;
    hoitokauden_lopun_kattohinta_kerroin                         DECIMAL(4, 2);
    lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus          BOOLEAN;
BEGIN
    -- Haetaan kaikki MHU-urakat ja lisätään niiden perustiedot urakka_parametrit tauluun
    for urakan_tiedot in (SELECT * FROM urakka WHERE id = urakkaid_ and tyyppi IN ('teiden-hoito'))
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
            -- Jos indeksi on käytössä sanktiolla, niin se on myös käytössä bonuksella
            indeksi_kaytossa := (CASE
                                     WHEN urakan_tiedot.alkupvm < '2020-10-02' THEN TRUE
                                     ELSE FALSE END);

            muokkaa_kattohinta_kasin := (CASE
                                             WHEN urakan_tiedot.alkupvm < '2020-10-02' THEN TRUE
                                             ELSE FALSE END);

            -- -25 vuodesta alkaen kattohintaylityksen siirron määrälle rajoitus on voimassa
            kattohintaylityksen_siirron_prosenttirajoitus := (CASE
                                                                  WHEN urakan_tiedot.alkupvm < '2024-10-02'
                                                                      THEN NULL
                                                                  ELSE 0.03 END);

            hoitokauden_lopun_kattohinta_kerroin := (CASE
                                                         WHEN urakan_tiedot.alkupvm < '2024-10-02' THEN 1.1
                                                         ELSE 1.2 END);

            lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus := (CASE
                                                                        WHEN urakan_tiedot.alkupvm < '2023-10-02'
                                                                            THEN FALSE
                                                                        ELSE TRUE END);

            -- Tarkistetaan, että löytyykö rivi jo taulusta
            IF EXISTS(SELECT 1 FROM urakka_parametrit WHERE urakkaid = urakan_tiedot.id)
            THEN
                UPDATE urakka_parametrit
                SET indeksi_kaytossa_sanktiolla                         = indeksi_kaytossa,
                    indeksi_kaytossa_bonuksella                         = indeksi_kaytossa,
                    lupauspaatoksen_bonusprosentti                      = bonusprosentti,
                    lupauspaatoksen_sanktioprosentti                    = sanktioprosentti,
                    tavoitepalkkion_maksuprosentti                      = tavoitepalkkioprosentti,
                    tavoitepalkkion_maksimi                             = tavoitepalkkionmaxprosentti,
                    tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti = (100 - tavoitehinnan_ylityksen_maksuprosentti),
                    tavoitehinnan_ylityksen_tilaajan_maksuprosentti     = tavoitehinnan_ylityksen_maksuprosentti,
                    kattohintaylityksen_siirron_prosenttirajoitus       = kattohintaylityksen_siirron_prosenttirajoitus,
                    muokkaa_kattohinta_kasin                            = muokkaa_kattohinta_kasin,
                    hoitokauden_lopun_kattohinta_kerroin                = hoitokauden_lopun_kattohinta_kerroin,
                    lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus = lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus,
                    muokattu                                            = NOW(),
                    muokkaaja                                           = luojaid
                WHERE urakkaid = urakan_tiedot.id;
            ELSE
                -- Jos ei löydy, niin lisätään
                INSERT INTO urakka_parametrit (urakkaid, indeksi_kaytossa_sanktiolla, indeksi_kaytossa_bonuksella,
                                               lupauspaatoksen_bonusprosentti,
                                               lupauspaatoksen_sanktioprosentti, tavoitepalkkion_maksuprosentti,
                                               tavoitepalkkion_maksimi,
                                               tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti,
                                               tavoitehinnan_ylityksen_tilaajan_maksuprosentti,
                                               kattohintaylityksen_siirron_prosenttirajoitus,
                                               muokkaa_kattohinta_kasin,
                                               hoitokauden_lopun_kattohinta_kerroin,
                                               lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus, luoja, luotu)
                VALUES (urakan_tiedot.id, indeksi_kaytossa, indeksi_kaytossa, bonusprosentti, sanktioprosentti,
                        tavoitepalkkioprosentti, tavoitepalkkionmaxprosentti,
                        (100 - tavoitehinnan_ylityksen_maksuprosentti), tavoitehinnan_ylityksen_maksuprosentti,
                        kattohintaylityksen_siirron_prosenttirajoitus, muokkaa_kattohinta_kasin,
                        hoitokauden_lopun_kattohinta_kerroin,
                        lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus, luojaid, NOW());
            END IF;
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


-- Funktio, joka laskee toteutuneet kustannukset annetulle urakalle ja hoitokaudelle
-- Ottaa huomioon kuukausittain siirtävyt suunnitellut kustannukset, kulut ja edellisen vuoden siirrot.
CREATE OR REPLACE FUNCTION laske_toteutuneet_kustannukset(_urakkaid INTEGER, _hoitokauden_alkuvuosi INTEGER) RETURNS NUMERIC(10, 2) AS
$$
DECLARE
    summa NUMERIC(12, 2);
BEGIN
    SELECT COALESCE(SUM(x.summa), 0) AS s
    FROM (
             -- Mahdolliset siirrot edelliseltä vuodelta
             SELECT coalesce(pta.siirron_maara, 0) as summa
             FROM paatos_tavoitehinta_alitus pta
             WHERE pta.urakkaid = _urakkaid
               AND pta.hoitokauden_alkuvuosi + 1 = _hoitokauden_alkuvuosi -- Haetaan edellisen vuoden päätöksestä
               AND pta.siirron_maara != 0
               AND pta.poistettu = FALSE
             -- paatos_kattohinta haetaan seuraavalle vuodelle mahdollisesti siirretyt kattohinnan ylitykset
             UNION ALL
             SELECT coalesce(pk.siirrettava_maara, 0) AS summa
             FROM paatos_kattohinta pk
             WHERE pk.urakkaid = _urakkaid
               AND pk.hoitokauden_alkuvuosi + 1 = _hoitokauden_alkuvuosi -- Haetaan edellisen vuoden päätöksestä
               AND pk.siirrettava_maara != 0
               AND pk.poistettu = FALSE
             UNION ALL
             -- Kulut taulusta
             SELECT kk.summa AS summa
             FROM kulu k
                      join kulu_kohdistus kk on k.id = kk.kulu AND kk.tavoitehintainen = true and kk.poistettu = FALSE
             WHERE k.urakka = _urakkaid
               AND k.poistettu = FALSE
               AND k.erapaiva BETWEEN concat(_hoitokauden_alkuvuosi, '-10-01')::DATE AND concat(_hoitokauden_alkuvuosi + 1, '-09-30')::DATE
             UNION ALL
             -- Toteutuneet kustannkset taulusta
             SELECT SUM(COALESCE(tk.summa_indeksikorjattu, tk.summa)) AS summa
             FROM toteutuneet_kustannukset tk
             WHERE tk.urakka_id = _urakkaid
               AND ((tk.vuosi = _hoitokauden_alkuvuosi AND tk.kuukausi >= 10 AND tk.kuukausi <= 12)
                 OR
                    (tk.vuosi = _hoitokauden_alkuvuosi + 1 AND tk.kuukausi >= 1 AND tk.kuukausi <= 9))) as x
    INTO summa;

    RETURN summa;
END;
$$ LANGUAGE plpgsql;

-- Konvertoidaan vanhat päätökset uusiin tauluihin
CREATE OR REPLACE FUNCTION konvertoi_vanhat_paatokset() RETURNS VOID AS
$$
DECLARE
    paatos                            RECORD;
    urakan_hinnat                     RECORD; -- sisältää urakan tavoitehinnan, kattohinnan ja tarjouksen tavoitehinnan
    hoitokauden_jarjestysluku         INTEGER;
    viimeinen_hoitokausi              BOOLEAN;
    urakka_parametrit                 RECORD;
    urakan_tiedot                     RECORD;
    toteutuneet_kustannukset_urakalle NUMERIC(12, 2);
    alituksen_maara_urakalle          NUMERIC(10, 2);
    tavoitehinnan_ylitys              NUMERIC(10, 2);
    indeksikorotus                    RECORD;
    maksimi_siirrettava_maara         NUMERIC(10, 2);
BEGIN

    FOR paatos in (SELECT * from urakka_paatos)
        LOOP
            RAISE NOTICE 'Päätöksen tiedot: %', paatos;
            -- Tulostetaan päätöksen tiedot
            -- HAetaan urakan parametrit
            SELECT * FROM urakka_parametrit WHERE urakkaid = paatos."urakka-id" into urakka_parametrit;

            -- Haetaan urakan alku ja loppupvm
            SELECT alkupvm, loppupvm, indeksi FROM urakka WHERE id = paatos."urakka-id" into urakan_tiedot;

            -- Monesko hoitokausi
            SELECT *
            FROM monesko_hoitokausi((SELECT alkupvm FROM urakka where id = paatos."urakka-id"),
                                    (SELECT loppupvm FROM urakka where id = paatos."urakka-id"),
                                    paatos."hoitokauden-alkuvuosi")
            into hoitokauden_jarjestysluku;

            -- Onko viimeinen hoitokausi
            IF paatos."hoitokauden-alkuvuosi" = EXTRACT(YEAR from urakan_tiedot.loppupvm) - 1 THEN
                viimeinen_hoitokausi := TRUE;
            ELSE
                viimeinen_hoitokausi := FALSE;
            END IF;

            RAISE NOTICE 'Hoitokauden järjestysluku: %, Viimeinen hoitokausi: %, urakan loppupvm: %', hoitokauden_jarjestysluku, viimeinen_hoitokausi, urakan_tiedot.loppupvm;

            -- Hae tavoitehinta, kattohinta ja tarjouksen_tavoitehinta
            WITH tavoitehinnan_oikaisut AS (SELECT COALESCE(SUM(tav.summa), 0) as summa
                                            FROM tavoitehinnan_oikaisu tav
                                            WHERE tav."urakka-id" = paatos."urakka-id"
                                              AND tav."hoitokauden-alkuvuosi" = paatos."hoitokauden-alkuvuosi"
                                              AND tav.poistettu = FALSE)
            SELECT paatos."hoitokauden-alkuvuosi"                AS hoitokauden_alkuvuosi,
                   ut.tavoitehinta_indeksikorjattu               AS hoitokauden_alun_tavoitehinta,
                   (ut.tavoitehinta_indeksikorjattu + tav.summa) AS tavoitehinta,
                   COALESCE(ko."uusi-kattohinta",
                            (ut.kattohinta_indeksikorjattu + (tav.summa * 1.1))) -- Katottihinta kasvaa 10% myös tavoitehinnan oikaisuista.
                                                                 AS kattohinta,
                   ut.tarjous_tavoitehinta                       AS tarjous_tavoitehinta
            FROM urakka_tavoite ut
                     JOIN urakka u ON ut.urakka = u.id AND u.id = paatos."urakka-id"
                     LEFT JOIN kattohinnan_oikaisu ko ON ko."urakka-id" = paatos."urakka-id" AND
                                                         ko."hoitokauden-alkuvuosi" = paatos."hoitokauden-alkuvuosi" AND
                                                         ko.poistettu = FALSE,
                 tavoitehinnan_oikaisut tav
            WHERE ut.urakka = paatos."urakka-id"
              AND ut.hoitokausi = hoitokauden_jarjestysluku
            INTO urakan_hinnat;

            toteutuneet_kustannukset_urakalle :=
                laske_toteutuneet_kustannukset(paatos."urakka-id", paatos."hoitokauden-alkuvuosi");

            RAISE NOTICE 'Hoitokauden alkuvuosi: %, Hoitokauden alun tavoitehinta: %, Hoitokauden lopun tavoitehinta: %s, kattohinta: %, tarjous_tavoitehinta: %, hoitokauden_jarjestysluku: %',
                urakan_hinnat.hoitokauden_alkuvuosi, urakan_hinnat.hoitokauden_alun_tavoitehinta, urakan_hinnat.tavoitehinta, urakan_hinnat.kattohinta, urakan_hinnat.tarjous_tavoitehinta, hoitokauden_jarjestysluku;

            RAISE NOTICE 'Toteutuneet kustannukset: %', toteutuneet_kustannukset_urakalle;

            CASE paatos.tyyppi
                WHEN 'lupaussanktio'
                    THEN RAISE NOTICE 'sanktio tiedot: %', paatos;

                         SELECT korotus
                         FROM sanktion_indeksikorotus(paatos.luotu::DATE, urakan_tiedot.indeksi,
                                                      paatos."urakoitsijan-maksu", paatos."urakka-id",
                                                      'lupaussanktio'::sanktiolaji)
                         INTO indeksikorotus;

                         INSERT INTO paatos_lupaus (urakkaid, hoitokauden_alkuvuosi, tyyppi,
                                                    tavoitehinta,
                                                    tarjous_tavoitehinta,
                                                    luvatut_pisteet, toteutuneet_pisteet,
                                                    lupaussanktio,
                                                    sanktioprosentti, indeksi, indeksikorotus, sanktio_id, luoja, luotu,
                                                    poistettu)
                         VALUES (paatos."urakka-id", paatos."hoitokauden-alkuvuosi", 'sanktio',
                                 urakan_hinnat.tavoitehinta,
                                 urakan_hinnat.tarjous_tavoitehinta, paatos."lupaus-luvatut-pisteet",
                                 paatos."lupaus-toteutuneet-pisteet",
                                 paatos."urakoitsijan-maksu",
                                 urakka_parametrit.lupauspaatoksen_sanktioprosentti,
                                 urakan_tiedot.indeksi, indeksikorotus.korotus,
                                 paatos.sanktio_id, paatos."luoja-id", paatos.luotu, paatos.poistettu);

                WHEN 'lupausbonus'
                    THEN RAISE NOTICE 'bonus tiedot: %', paatos;
                         SELECT korotus
                         FROM sanktion_indeksikorotus(paatos.luotu::DATE, urakan_tiedot.indeksi,
                                                      paatos."urakoitsijan-maksu", paatos."urakka-id",
                                                      'lupaussanktio'::sanktiolaji)
                         INTO indeksikorotus;
                         INSERT INTO paatos_lupaus (urakkaid, hoitokauden_alkuvuosi, tyyppi,
                                                    tavoitehinta,
                                                    tarjous_tavoitehinta,
                                                    luvatut_pisteet, toteutuneet_pisteet, lupausbonus,
                                                    bonusprosentti, indeksi, indeksikorotus, erilliskustannus_id, luoja,
                                                    luotu,
                                                    poistettu)
                         VALUES (paatos."urakka-id", paatos."hoitokauden-alkuvuosi", 'bonus',
                                 urakan_hinnat.tavoitehinta, urakan_hinnat.tarjous_tavoitehinta,
                                 paatos."lupaus-luvatut-pisteet",
                                 paatos."lupaus-toteutuneet-pisteet",
                                 paatos."tilaajan-maksu",
                                 urakka_parametrit.lupauspaatoksen_bonusprosentti,
                                 urakan_tiedot.indeksi, indeksikorotus.korotus,
                                 paatos.erilliskustannus_id,
                                 paatos."luoja-id", paatos.luotu, paatos.poistettu);

                WHEN 'tavoitehinnan-alitus'
                    THEN RAISE NOTICE 'tavoitehinnan-alitus tiedot: %', paatos;
                         alituksen_maara_urakalle :=
                             urakan_hinnat.tavoitehinta - toteutuneet_kustannukset_urakalle;
                         INSERT INTO paatos_tavoitehinta_alitus (urakkaid,
                                                                 hoitokauden_alkuvuosi,
                                                                 hoitokauden_alun_tavoitehinta,
                                                                 hoitokauden_lopun_tavoitehinta,
                                                                 toteutuneet_kustannukset,
                                                                 alituksen_maara, siirron_maara,
                                                                 tavoitepalkkio,
                                                                 tavoitepalkkion_maksuprosentti,
                                                                 kulu_id,
                                                                 viimeinen_hoitokausi,
                                                                 luotu, luoja,
                                                                 poistettu)
                         VALUES (paatos."urakka-id", paatos."hoitokauden-alkuvuosi",
                                 urakan_hinnat.hoitokauden_alun_tavoitehinta, urakan_hinnat.tavoitehinta,
                                 toteutuneet_kustannukset_urakalle,
                                 alituksen_maara_urakalle,
                                 paatos.siirto,
                                 (paatos."urakoitsijan-maksu" * -1), -- Vanhassa päätöstaulussa on tavoitepalkkio negatiivisena ja eri tavalla tallennettuna, koska siitä on tehty negatiivinen kulu.
                                 urakka_parametrit.tavoitepalkkion_maksuprosentti,
                                 paatos.kulu_id, viimeinen_hoitokausi,
                                 paatos.luotu, paatos."luoja-id",
                                 paatos.poistettu);
                WHEN 'tavoitehinnan-ylitys'
                    THEN RAISE NOTICE 'tavoitehinnan-ylitys tiedot: %', paatos;

                    -- Lasketaan tavoitehinnan ylitys.
                         IF urakan_hinnat.kattohinta >= toteutuneet_kustannukset_urakalle THEN
                             -- Jos toteutuneet kustannukset ovat pienemmät kuin kattohinta, niin tavoitehinnan ylitys lasketaan toteutuneista kustannuksista
                             tavoitehinnan_ylitys :=
                                 (toteutuneet_kustannukset_urakalle - urakan_hinnat.tavoitehinta);
                             RAISE NOTICE 'tavoitehinnan-ylitys :: kattohinta suurempi kuin toteuma :: Toteutuneet kustannukset: %, kattohinta: %, tavoitehinnan_ylitys: %', toteutuneet_kustannukset_urakalle, urakan_hinnat.kattohinta, tavoitehinnan_ylitys;
                         ELSE
                             -- Kun toteutuneet kustannukset ylittävät myös kattohinnan, niin tavoitehinnan ylitys lasketaan kattohinnan ja tavoitehinnan välistä
                             tavoitehinnan_ylitys :=
                                 (urakan_hinnat.kattohinta - urakan_hinnat.tavoitehinta);
                             RAISE NOTICE 'tavoitehinnan-ylitys tiedot :: toteuma alle kattohinnan  :: Toteutuneet kustannukset: %, kattohinta: %, tavoitehinnan_ylitys: %', toteutuneet_kustannukset_urakalle, urakan_hinnat.kattohinta, tavoitehinnan_ylitys;

                         end if;

                         INSERT INTO paatos_tavoitehinta_ylitys (urakkaid,
                                                                 hoitokauden_alkuvuosi,
                                                                 tavoitehinta,
                                                                 toteutuneet_kustannukset,
                                                                 ylityksen_maara,
                                                                 tilaajan_prosentti,
                                                                 urakoitsijan_prosentti,
                                                                 tilaaja_maksaa,
                                                                 urakoitsija_maksaa, siirto,
                                                                 kulu_id, viimeinen_hoitokausi,
                                                                 luotu, luoja, poistettu)
                         VALUES (paatos."urakka-id", paatos."hoitokauden-alkuvuosi",
                                 urakan_hinnat.tavoitehinta, urakan_hinnat.tarjous_tavoitehinta,
                                 tavoitehinnan_ylitys, -- Ylityksen määrä on laskettava, koska sitä ei ollut päätöksissä tallessa
                                 urakka_parametrit.tavoitehinnan_ylityksen_tilaajan_maksuprosentti,
                                 urakka_parametrit.tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti,
                                 paatos."tilaajan-maksu",
                                 paatos."urakoitsijan-maksu", paatos.siirto, paatos.kulu_id,
                                 viimeinen_hoitokausi, paatos.luotu, paatos."luoja-id", paatos.poistettu);
                WHEN 'kattohinnan-ylitys'
                    THEN RAISE NOTICE 'kattohinnan-ylitys tiedot: %', paatos;

                         IF urakka_parametrit.kattohintaylityksen_siirron_maaran_rajoitus IS TRUE THEN
                             maksimi_siirrettava_maara := urakan_hinnat.kattohinta *
                                                          urakka_parametrit.kattohintaylityksen_siirron_prosenttirajoitus;
                         ELSE
                             maksimi_siirrettava_maara :=
                                 (toteutuneet_kustannukset_urakalle - urakan_hinnat.kattohinta);
                         END IF;

                         INSERT INTO paatos_kattohinta (urakkaid, hoitokauden_alkuvuosi,
                                                        kattohinta,
                                                        toteutuneet_kustannukset, ylityksen_maara,
                                                        urakoitsija_maksaa,
                                                        siirrettava_maara, kulu_id,
                                                        viimeinen_hoitokausi, maksimi_siirrettava_maara,
                                                        siirtorajoitus_prosentti, luotu, luoja,
                                                        poistettu)
                         VALUES (paatos."urakka-id", paatos."hoitokauden-alkuvuosi",
                                 urakan_hinnat.kattohinta, toteutuneet_kustannukset_urakalle,
                                 (toteutuneet_kustannukset_urakalle - urakan_hinnat.kattohinta),
                                 paatos."urakoitsijan-maksu", paatos.siirto, paatos.kulu_id,
                                 viimeinen_hoitokausi, maksimi_siirrettava_maara,
                                 urakka_parametrit.kattohintaylityksen_siirron_prosenttirajoitus, paatos.luotu,
                                 paatos."luoja-id", paatos.poistettu);

                END CASE;

        END LOOP;

END
$$ LANGUAGE plpgsql;

-- Käynnistä laskenta. Eli konvertoi vanhat päätökset uusiin tauluihin
select konvertoi_vanhat_paatokset(); -- Tähän menee n. 20 sek
