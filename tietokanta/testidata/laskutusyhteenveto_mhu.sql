-- Tänne Laskutusyhteenvedon MHU VERSION testidatoja

-- SANKTIOT && urakka_tavoite
-- Käytetään nimetöntä koodi blokkia, jotta voidaan määritellä muuttujia
DO
$$
    DECLARE
        kayttaja_id                        INTEGER;
        saktio_talvihoito                  INTEGER;
        saktio_liikymp                     INTEGER;
        toimenpideinstanssi_talvihoito_id  INTEGER;
        toimenpideinstanssi_liikenneymp_id INTEGER;
        toimenpideinstanssi_hoidonjohto_id INTEGER;
        urakka_id                          INTEGER;
        sopimus_id                         INTEGER;
    BEGIN
        kayttaja_id := (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');
        saktio_talvihoito :=
                (SELECT id FROM sanktiotyyppi WHERE nimi = 'Talvihoito, päätiet (talvihoitoluokat Is ja I)');
        saktio_liikymp := (SELECT id FROM sanktiotyyppi WHERE nimi = 'Liikenneympäristön hoito');
        toimenpideinstanssi_talvihoito_id := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Talvihoito TP');
        toimenpideinstanssi_liikenneymp_id :=
                (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Liikenneympäristön hoito TP');
        toimenpideinstanssi_hoidonjohto_id :=
                (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP');
        urakka_id := (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024');
        -- MHU Oulu sopimus
        sopimus_id := (SELECT id FROM sopimus WHERE urakka = urakka_id AND paasopimus IS NULL);


        -- Sanktiot -- Oulun MHU 2019-2024 - Talvihoito 10/2019
        INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                                    tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty,
                                    selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa,
                                    tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
            VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
                    'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei toimi', 123,
                    kayttaja_id, NOW(), '2019-10-11 06:06.37', '2019-10-11 06:06.37', FALSE, FALSE, urakka_id,
                    'Sanktion sisältävä laatupoikkeama - MHU T1', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi,
                             suorasanktio, luoja)
            VALUES ('A'::SANKTIOLAJI, 1000.77, '2019-10-12 06:06.37', 'MAKU 2015',
                    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU T1'),
                    toimenpideinstanssi_talvihoito_id, saktio_talvihoito, FALSE, kayttaja_id);


        -- Sanktiot -- Oulun MHU 2019-2024 - Talvihoito 03/2020

        INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                                    tarkastuspiste, luoja, luotu, aika,
                                    kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero,
                                    tr_alkuosa, tr_loppuosa,
                                    tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
            VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
                    'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei toimi', 123, kayttaja_id,
                    NOW(), '2020-03-16 06:06.37', '2020-03-16 06:06.37', FALSE, FALSE, urakka_id,
                    'Sanktion sisältävä laatupoikkeama - MHU T2', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi,
                             suorasanktio, luoja)
            VALUES ('A'::SANKTIOLAJI, 100.20, '2020-03-17 06:06.37', 'MAKU 2015',
                    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU T2'),
                    toimenpideinstanssi_talvihoito_id, saktio_talvihoito, FALSE, kayttaja_id);

        -- Sanktiot -- Oulun MHU 2019-2024 - Liikenneympäristön hoito 10/2019
        INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                                    tarkastuspiste, luoja, luotu, aika,
                                    kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero,
                                    tr_alkuosa, tr_loppuosa,
                                    tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
            VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
                    'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei toimi', 123,
                    kayttaja_id, NOW(), '2019-10-11 06:06.37', '2019-10-11 06:06.37', FALSE, FALSE, urakka_id,
                    'Sanktion sisältävä laatupoikkeama - MHU L1', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi,
                             suorasanktio, luoja)
            VALUES ('A'::SANKTIOLAJI, 1000.77, '2019-10-12 06:06.37', 'MAKU 2015',
                    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU L1'),
                    toimenpideinstanssi_liikenneymp_id, saktio_talvihoito, FALSE, kayttaja_id);

        -- SUOLASAKOT
        -- Suolasakot :: Vaatii suolasakon, toteutuma_materiaalin, toteutuman sekä lämpötilat, toimiakseen.
        -- Lisätään hatusta vedetyt lämpötilat 10/2019 ja 09/2020 kuukausille
        INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila,
                                pitka_keskilampotila_vanha)
            VALUES (urakka_id,
                '2019-10-01', '2020-09-30', -3.5, -5.6, -5.6);

        -- Suolasakko tarkistetaan aina talvihoitokauden jälkeen. Joten lisää sakko kesäkuulle
        -- Suolasakko -- Oulun MHU 2019-2024 - Talvihoidon jälkeen
        INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja, luoja,
                                luotu)
            VALUES (3.0, 2019, 6, 'MAKU 2015', urakka_id, 800, kayttaja_id, NOW());
        -- Suolauksen toteuma (materiaalitoteuma)
        INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi,
                             suorittajan_ytunnus, lisatieto, luoja)
            VALUES ('harja-ui'::LAHDE, urakka_id, sopimus_id, NOW(), '2019-10-01 13:00:00+02', '2020-04-30 13:00:00+02',
                    'kokonaishintainen'::TOTEUMATYYPPI, 'Sami Suolaaja', '4153724-6', 'Sami-Suolaaja-2020', kayttaja_id);

        INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, luoja, urakka_id)
            VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Sami-Suolaaja-2020'), NOW(), 1369, 2, kayttaja_id, urakka_id);

        INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, luoja, urakka_id)
            VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Sami-Suolaaja-2020'), NOW(),
                    (SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola'), 1300, kayttaja_id, urakka_id);


        INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta, luotu, luoja)
            VALUES (urakka_id, 1, 250000, NULL, 1.1 * 250000, NOW(), kayttaja_id);

        -- MHU Hoidonjohto - SANKTIOT: lupaussanktio (tämän lopullinen summa indeksilaskennan kautta)
        INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                                    tarkastuspiste, luoja, luotu, aika,
                                    kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero,
                                    tr_alkuosa, tr_loppuosa,
                                    tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
            VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
                    'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei toimi', 123, kayttaja_id, NOW(), '2020-03-16 06:06.37',
                    '2020-03-16 06:06.37', FALSE, FALSE, urakka_id,'Sanktion sisältävä laatupoikkeama - MHU HJ1', 1, 2,
                    3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi,
                             suorasanktio, luoja)
            VALUES ('lupaussanktio'::SANKTIOLAJI, 1000, '2019-10-12 06:06.37', 'MAKU 2015',
                    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU HJ1'),
                    toimenpideinstanssi_hoidonjohto_id, (SELECT id FROM sanktiotyyppi WHERE nimi = 'Sanktiotyyppiä ei tarvita'), FALSE, kayttaja_id);

        -- MHU Hoidonjohto - SANKTIOT: vastuuhenkilön vaihtosanktio (tämän lopullinen summa indeksilaskennan kautta), arvonvähennykset
        INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                                    tarkastuspiste, luoja, luotu, aika,
                                    kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero,
                                    tr_alkuosa, tr_loppuosa,
                                    tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
            VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
                    'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei toimi', 123, kayttaja_id, NOW(), '2020-03-16 06:06.37',
                    '2020-03-16 06:06.37', FALSE, FALSE, urakka_id,'Sanktion sisältävä laatupoikkeama - MHU HJ2', 1, 2,
                    3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi,
                             suorasanktio, luoja)
            VALUES ('vaihtosanktio'::SANKTIOLAJI, 1000, '2019-10-12 06:06.37', 'MAKU 2015',
                    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU HJ2'),
                    toimenpideinstanssi_hoidonjohto_id, (SELECT id FROM sanktiotyyppi WHERE nimi = 'Sanktiotyyppiä ei tarvita'), FALSE, kayttaja_id);

        -- MHU Hoidonjohto - SANKTIOT: arvonvähennykset
        INSERT INTO laatupoikkeama (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu,
                                    tarkastuspiste, luoja, luotu, aika,
                                    kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero,
                                    tr_alkuosa, tr_loppuosa,
                                    tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
            VALUES ('harja-ui'::LAHDE, 'Testikohde', 'tilaaja'::OSAPUOLI, 'puhelin'::LAATUPOIKKEAMAN_KASITTELYTAPA, '',
                    'sanktio'::LAATUPOIKKEAMAN_PAATOSTYYPPI, 'Ei toimi', 123, kayttaja_id, NOW(), '2020-03-16 06:06.37',
                    '2020-03-16 06:06.37', FALSE, FALSE, urakka_id,'Sanktion sisältävä laatupoikkeama - MHU HJ3', 1, 2,
                    3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi,
                             suorasanktio, luoja)
            VALUES ('arvonvahennyssanktio'::SANKTIOLAJI, 1000, '2019-10-12 06:06.37', 'MAKU 2015',
                    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU HJ3'),
                    toimenpideinstanssi_hoidonjohto_id, (SELECT id FROM sanktiotyyppi WHERE nimi = 'Sanktiotyyppiä ei tarvita'), FALSE, kayttaja_id);

    END
$$ LANGUAGE plpgsql;


-- MHU hoidon johto
-- Johto- ja hallintokorvaukset, HJ-palkkio, bonukset
DO
$$
    DECLARE
        urakka_id                          INTEGER;
        sopimus_id                         INTEGER;
        tpi                                INTEGER;
        kayttaja_id                        INTEGER;
        toimenpidekoodi_tyonjohto          INTEGER;
        tehtavaryhma_erillishankinnat      INTEGER;
        tehtavaryhma_hjpalkkiot            INTEGER;
        tehtavaryhma_johto_hallintokorvaus INTEGER;
        vuosi                              INTEGER;
        ennen_urakkaa                      BOOLEAN;
    BEGIN
        urakka_id := (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024');
        sopimus_id := (SELECT id FROM sopimus WHERE urakka = urakka_id AND paasopimus IS NULL); --MHU Oulu sopimus
        tpi := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP');
        kayttaja_id := (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');
        toimenpidekoodi_tyonjohto := (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto');
        tehtavaryhma_erillishankinnat := (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');
        tehtavaryhma_hjpalkkiot := (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)');
        tehtavaryhma_johto_hallintokorvaus := (SELECT id FROM tehtavaryhma WHERE nimi = 'Johto- ja hallintokorvaus (J)');
        vuosi := (SELECT extract(YEAR FROM NOW()));
        ennen_urakkaa := FALSE;

        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, "ennen-urakkaa", luotu,
                                              "toimenkuva-id")
            VALUES (urakka_id, 5, 40, vuosi, 3, ennen_urakkaa, NOW(),
                    (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

        -- Bonukset - 10/2019
        -- Erilliskustannukset - Alihankintabonus - lupausbonus - tavoitepalkkio
        INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi,
                                      lisatieto, luotu, luoja)
            VALUES ('alihankintabonus', sopimus_id, urakka_id, tpi, '2019-10-15', 1000, 'MAKU 2015',
                    'Alihankittu hyvin!', '2019-10-13', kayttaja_id);

        INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi,
                                      lisatieto, luotu, luoja)
            VALUES ('lupausbonus', sopimus_id, urakka_id, tpi, '2019-10-15', 1000, 'MAKU 2015',
                    'Hyvin luvattu!', '2019-10-13', kayttaja_id);

        INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi,
                                      lisatieto, luotu, luoja)
            VALUES ('tavoitepalkkio', sopimus_id, urakka_id, tpi, '2019-10-15', 1000, 'MAKU 2015',
                    'Pääsit tavoitteisiin!', '2019-10-13', kayttaja_id);

        -- Bonukset - 03/2020
        INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi,
                                      lisatieto, luotu, luoja)
            VALUES ('alihankintabonus', sopimus_id, urakka_id, tpi, '2020-03-15', 500, 'MAKU 2015',
                    'Alihankittu hyvin!', '2020-03-13', kayttaja_id);

        INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi,
                                      lisatieto, luotu, luoja)
            VALUES ('lupausbonus', sopimus_id, urakka_id, tpi, '2020-03-15', 500, 'MAKU 2015',
                    'Hyvin luvattu!', '2020-03-13', kayttaja_id);

        INSERT INTO erilliskustannus (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi,
                                      lisatieto, luotu, luoja)
            VALUES ('tavoitepalkkio', sopimus_id, urakka_id, tpi, '2020-03-15', 500, 'MAKU 2015',
                    'Pääsit tavoitteisiin!', '2020-03-13', kayttaja_id);

        -- Hoidonjohdon palkkiot - 10/2019
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
            VALUES (2019, 10, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, toimenpidekoodi_tyonjohto, NULL, tpi, sopimus_id,
                    kayttaja_id);
        -- Hoidonjohdon palkkiot - 03/2020
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
            VALUES (2020, 03, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, toimenpidekoodi_tyonjohto, NULL, tpi, sopimus_id,
                    kayttaja_id);

        -- Erillishankinnat - 10/2019
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
            VALUES (2019, 10, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, NULL, tehtavaryhma_erillishankinnat, tpi,
                    sopimus_id, kayttaja_id);
        -- Erillishankinnat - 03/2020
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
            VALUES (2020, 03, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, NULL, tehtavaryhma_erillishankinnat, tpi,
                    sopimus_id, kayttaja_id);

        -- HJ-palkkio - 10/2019
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
            VALUES (2019, 10, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, NULL, tehtavaryhma_hjpalkkiot, tpi, sopimus_id,
                    kayttaja_id);
        -- Erillishankinnat - 03/2020
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
            VALUES (2020, 03, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, NULL, tehtavaryhma_hjpalkkiot, tpi, sopimus_id,
                    kayttaja_id);

    END
$$ LANGUAGE plpgsql;

