-- Urakoilla on paljon parametreja, joilla mm. päätöksien tietoja muutellaan.
-- Tehdään alustava taulu, jota on helppo laajentaa eri parametrien suhteen
CREATE TABLE urakka_parametrit
(
    id                                                  SERIAL PRIMARY KEY,
    urakkaid                                            INTEGER   NOT NULL,
    indeksi_kaytossa_sanktiolla                         BOOLEAN,       -- Onko indeksikorjaus käytössä sanktioilla. -19/20 alkavilla urakoilla käytössä, muilla ei
    indeksi_kaytossa_bonuksella                         BOOLEAN,       -- Onko indeksikorjaus käytössä bonuksella. -19/20 alkavilla urakoilla käytössä asiakastyytyväisyysbonuksella, muilla ei
    lupauspaatoksen_bonusprosentti                      DECIMAL(4, 2), -- Luvatun pistemäärän ylittävää pistettä kohden maksettava bonusprosentti tarjouksen tavoitehinnasta
    lupauspaatoksen_sanktioprosentti                    DECIMAL(4, 2), -- Luvatun pistemäärän alittavaa pistettä kohden maksettava sanktioprosentti tarjouksen tavoitehinnasta
    lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus BOOLEAN,       -- -24 alkaen hoitovuoden lopun tavoitehintaan lisätään myös hiotovuoden lopun indeksikorjaus
    hoitokauden_lopun_kattohinta_kerroin                DECIMAL(4, 2), -- Kaava kattohinnan laskemiseen, voi olla 1.1 tai 1.2 kertaa hoitovuoden lopun tavoitehinta, joka sekin lasketaan eri tavalla eri vuosina
    muokkaa_kattohinta_kasin                            BOOLEAN,       -- -19/20 alkavilla urakoilla kattohinta annetaan käsin, muilla 10% tavoitehinnasta
    kattohintaylityksen_siirron_prosenttirajoitus       DECIMAL(4, 2), -- Esim 0.03 (prosenttia) vuonna -25 alkavilla urakoilla
    tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti INTEGER,       -- Kuinka monta prosenttia urakoitsija maksaa ylityksen kustannuksista
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti     DECIMAL(4, 2), -- Tavoitehinnan ylityksen maksuprosentti tilaajalle (kattohintaan asti)
    tavoitepalkkion_maksuprosentti                      DECIMAL(4, 2), -- Tavoitepalkkion maksuprosentti. Voi olla esim 30% tavoitehinnan alituksesta tai 75% alennuksesta
    tavoitepalkkion_maksimi                             DECIMAL(4, 2), -- Tavoitepalkkion maksimi määrä prosentteina
    luotu                                               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muokattu                                            TIMESTAMP          DEFAULT CURRENT_TIMESTAMP,
    luoja                                               INTEGER   NOT NULL,
    muokkaaja                                           INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (muokkaaja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id) ON DELETE CASCADE
);
COMMENT ON COLUMN urakka_parametrit.indeksi_kaytossa_sanktiolla IS 'Onko indeksikorjaus käytössä sanktioilla. -19/20 alkavilla urakoilla käytössä, muilla ei.';
COMMENT ON COLUMN urakka_parametrit.indeksi_kaytossa_bonuksella IS 'Onko indeksikorjaus käytössä bonuksella. -19/20 alkavilla urakoilla käytössä asiakastyytyväisyysbonuksella, muilla ei.';
COMMENT ON COLUMN urakka_parametrit.lupauspaatoksen_bonusprosentti IS 'Luvatun pistemäärän ylittävää pistettä kohden maksettava bonusprosentti tarjouksen tavoitehinnasta.';
COMMENT ON COLUMN urakka_parametrit.lupauspaatoksen_sanktioprosentti IS 'Luvatun pistemäärän alittavaa pistettä kohden maksettava sanktioprosentti tarjouksen tavoitehinnasta.';
COMMENT ON COLUMN urakka_parametrit.lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus IS '-24 alkaen hoitovuoden lopun tavoitehintaan lisätään myös hiotovuoden lopun indeksikorjaus.';
COMMENT ON COLUMN urakka_parametrit.hoitokauden_lopun_kattohinta_kerroin IS 'Kaava kattohinnan laskemiseen, voi olla 1.1 tai 1.2 kertaa hoitovuoden lopun tavoitehinta, joka sekin lasketaan eri tavalla eri vuosina.';
COMMENT ON COLUMN urakka_parametrit.muokkaa_kattohinta_kasin IS '-19/20 alkavilla urakoilla kattohinta annetaan käsin, muilla 10% tavoitehinnasta.';
COMMENT ON COLUMN urakka_parametrit.kattohintaylityksen_siirron_prosenttirajoitus IS 'Esim 0.03 (prosenttia) vuonna -25 alkavilla urakoilla.';
COMMENT ON COLUMN urakka_parametrit.tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti IS 'Kuinka monta prosenttia urakoitsija maksaa ylityksen kustannuksista.';
COMMENT ON COLUMN urakka_parametrit.tavoitehinnan_ylityksen_tilaajan_maksuprosentti IS 'Tavoitehinnan ylityksen maksuprosentti tilaajalle (kattohintaan asti).';
COMMENT ON COLUMN urakka_parametrit.tavoitepalkkion_maksuprosentti IS 'Tavoitepalkkion maksuprosentti. Voi olla esim 30% tavoitehinnan alituksesta tai 75% alennuksesta.';
COMMENT ON COLUMN urakka_parametrit.tavoitepalkkion_maksimi IS 'Tavoitepalkkion maksimi määrä prosentteina. Esim 3';


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
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2024_MHUplus DECIMAL(4, 2)  := 50;
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2025_        DECIMAL(4, 2)  := 25;
    tavoitehinnan_ylityksen_maksuprosentti                       DECIMAL(4, 2);
    luojaid                                                      INTEGER        := (SELECT id
                                                                                    FROM kayttaja
                                                                                    WHERE kayttajanimi = 'Integraatio');
    indeksi_kaytossa                                             BOOLEAN;
    kattohintaylityksen_prosenttirajoitus_siirrolle              DECIMAL(4, 2);
    muokkaa_kasin_kattohinta                                     BOOLEAN;
    kerroin_hoitokauden_lopun_kattohinnalle                      DECIMAL(4, 2);
    tavoitehintaan_hoitovuodenlopunindeksikorjaus                BOOLEAN;
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
                                                           WHEN urakan_tiedot.alkupvm > '2023-10-02' AND
                                                                urakan_tiedot.sopimustyyppi = 'mhu+'
                                                               THEN tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2024_MHUplus
                -- Kaikille muille defaulttina 70%
                                                           ELSE tavoitehinnan_ylityksen_tilaajan_maksuprosentti_2019_2024 END);

            RAISE NOTICE 'tavoitehinnan_ylityksen_maksuprosentti :: urakkaid % :: sopimustyyppi: % :: tavoitehinnan_ylityksen_maksuprosentti: %',
                urakan_tiedot.id, urakan_tiedot.sopimustyyppi, tavoitehinnan_ylityksen_maksuprosentti;

            -- Jos indeksi on käytössä sanktiolla, niin se on myös käytössä bonuksella
            indeksi_kaytossa := (CASE
                                     WHEN urakan_tiedot.alkupvm < '2020-10-02' THEN TRUE
                                     ELSE FALSE END);

            muokkaa_kasin_kattohinta := (CASE
                                             WHEN urakan_tiedot.alkupvm < '2020-10-02' THEN TRUE
                                             ELSE FALSE END);

            -- -25 vuodesta alkaen kattohintaylityksen siirron määrälle rajoitus on voimassa
            kattohintaylityksen_prosenttirajoitus_siirrolle := (CASE
                                                                    WHEN urakan_tiedot.alkupvm < '2024-10-02'
                                                                        THEN NULL
                                                                    ELSE 0.03 END);

            kerroin_hoitokauden_lopun_kattohinnalle := (CASE
                                                            WHEN urakan_tiedot.alkupvm < '2024-10-02' THEN 1.1
                                                            ELSE 1.2 END);

            tavoitehintaan_hoitovuodenlopunindeksikorjaus := (CASE
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
                    kattohintaylityksen_siirron_prosenttirajoitus       = kattohintaylityksen_prosenttirajoitus_siirrolle,
                    muokkaa_kattohinta_kasin                            = muokkaa_kasin_kattohinta,
                    hoitokauden_lopun_kattohinta_kerroin                = kerroin_hoitokauden_lopun_kattohinnalle,
                    lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus = tavoitehintaan_hoitovuodenlopunindeksikorjaus,
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
                        kattohintaylityksen_prosenttirajoitus_siirrolle, muokkaa_kasin_kattohinta,
                        kerroin_hoitokauden_lopun_kattohinnalle,
                        tavoitehintaan_hoitovuodenlopunindeksikorjaus, luojaid, NOW());
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
