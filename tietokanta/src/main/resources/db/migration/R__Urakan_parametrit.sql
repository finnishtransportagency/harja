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
    onko_laskutusraja_kaytossa                                   BOOLEAN;
    onko_muutosten_hallinta_kaytossa                             BOOLEAN;
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
            onko_laskutusraja_kaytossa := (CASE
                                          WHEN urakan_tiedot.alkupvm >= '2025-10-01' THEN TRUE
                                          ELSE FALSE END);
            onko_muutosten_hallinta_kaytossa := (CASE
                                                     WHEN urakan_tiedot.alkupvm >= '2025-01-01' THEN TRUE
                                                     ELSE FALSE END);


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
                    muokkaaja                                           = luojaid,
                    laskutusraja_kaytossa                               = onko_laskutusraja_kaytossa,
                    muutosten_hallinta                                  = onko_muutosten_hallinta_kaytossa
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
                                               lisaa_tavoitehintaan_hoitovuodenlopunindeksikorjaus, luoja, luotu, laskutusraja_kaytossa)
                VALUES (urakan_tiedot.id, indeksi_kaytossa, indeksi_kaytossa, bonusprosentti, sanktioprosentti,
                        tavoitepalkkioprosentti, tavoitepalkkionmaxprosentti,
                        (100 - tavoitehinnan_ylityksen_maksuprosentti), tavoitehinnan_ylityksen_maksuprosentti,
                        kattohintaylityksen_prosenttirajoitus_siirrolle, muokkaa_kasin_kattohinta,
                        kerroin_hoitokauden_lopun_kattohinnalle,
                        tavoitehintaan_hoitovuodenlopunindeksikorjaus, luojaid, NOW(), onko_laskutusraja_kaytossa);
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
