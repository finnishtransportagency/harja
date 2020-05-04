-- Tänne Laskutusyhteenvedon MHU VERSION testidatoja

-- SANKTIOT && urakka_tavoite
-- Käytetään nimetöntä koodi blokkia, jotta voidaan määritellä muuttujia
DO
$$
    DECLARE
        kayttaja_id                       INTEGER;
        sanktio_tyyppi_id                 INTEGER;
        toimenpideinstanssi_talvihoito_id INTEGER;
        urakka_id                         INTEGER;
        sopimus_id                        INTEGER;
    BEGIN
        kayttaja_id := (select id from kayttaja where kayttajanimi = 'Integraatio');
        sanktio_tyyppi_id :=
                (SELECT id FROM sanktiotyyppi WHERE nimi = 'Talvihoito, päätiet (talvihoitoluokat Is ja I)');
        toimenpideinstanssi_talvihoito_id := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Talvihoito TP');
        urakka_id := (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024');
        sopimus_id := (SELECT id FROM sopimus WHERE urakka = urakka_id AND paasopimus IS null);
        --MHU Oulu sopimus


        -- Sanktiot -- Oulun MHU 2019-2024 - Talvihoito 10/2019
        INSERT INTO laatupoikkeama
        (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste, luoja, luotu, aika,
         kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa,
         tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
        VALUES ('harja-ui'::lahde, 'Testikohde', 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
                'sanktio'::laatupoikkeaman_paatostyyppi, 'Ei toimi', 123,
                kayttaja_id, NOW(), '2019-10-11 06:06.37', '2019-10-11 06:06.37', false, false, urakka_id,
                'Sanktion sisältävä laatupoikkeama - MHU T1', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio
        (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi, suorasanktio, luoja)
        VALUES ('A'::sanktiolaji, 1000.77, '2019-10-12 06:06.37', 'MAKU 2015',
                (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU T1'),
                toimenpideinstanssi_talvihoito_id, sanktio_tyyppi_id, false, kayttaja_id);


        -- Sanktiot -- Oulun MHU 2019-2024 - Talvihoito 03/2020

        INSERT INTO laatupoikkeama
        (lahde, kohde, tekija, kasittelytapa, muu_kasittelytapa, paatos, perustelu, tarkastuspiste, luoja, luotu, aika,
         kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa,
         tr_loppuetaisyys, sijainti, tr_alkuetaisyys)
        VALUES ('harja-ui'::lahde, 'Testikohde', 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
                'sanktio'::laatupoikkeaman_paatostyyppi, 'Ei toimi', 123, kayttaja_id,
                NOW(), '2020-03-16 06:06.37', '2020-03-16 06:06.37', false, false, urakka_id,
                'Sanktion sisältävä laatupoikkeama - MHU T2', 1, 2, 3, 4, point(418237, 7207744)::GEOMETRY, 5);
        INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi, tyyppi,
                             suorasanktio, luoja)
        VALUES ('A'::sanktiolaji, 100.20, '2020-03-17 06:06.37', 'MAKU 2015',
                (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Sanktion sisältävä laatupoikkeama - MHU T2'),
                toimenpideinstanssi_talvihoito_id, sanktio_tyyppi_id, false, kayttaja_id);

        -- SUOLASAKOT
        -- Suolasakot :: Vaatii suolasakon, toteutuma_materiaalin, toteutuman sekä lämpötilat, toimiakseen.
        -- Lisätään hatusta vedetyt lämpötilat 10/2019 ja 03/2020 kuukausille
        INSERT INTO lampotilat (urakka, alkupvm, loppupvm, keskilampotila, pitka_keskilampotila,
                                pitka_keskilampotila_vanha)
        VALUES (urakka_id,
                '2019-10-01', '2020-09-30', -3.5, -5.6, -5.6);


        -- Suolasakko -- Oulun MHU 2019-2024 - Talvihoito 10/2019
        INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja, luoja,
                                luotu)
        VALUES (3.0, 2019, 10, 'MAKU 2015', urakka_id, 800, kayttaja_id, NOW());
        -- Suolauksen toteuma (materiaalitoteuma)
        INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi,
                             suorittajan_ytunnus, lisatieto)
        VALUES ('harja-ui'::lahde, urakka_id, sopimus_id, NOW(), '2019-10-01 13:00:00+02', '2019-10-31 13:00:00+02',
                'kokonaishintainen'::toteumatyyppi, 'Sami Suolaaja', '4153724-6', 'Sami-Suolaaja-2019');

        INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)
        VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Sami-Suolaaja-2019'), NOW(),
                (SELECT id FROM materiaalikoodi WHERE nimi = 'Talvisuola'), 1300);


        -- Ilmeisesit suolasakkoja voi olla vain yksi per talvi, joten tämä nyt kommenteissa
        -- Suolasakko -- Oulun MHU 2019-2024 - Talvihoito 03/2020
        --INSERT INTO suolasakko (maara, hoitokauden_alkuvuosi, maksukuukausi, indeksi, urakka, talvisuolaraja, luoja, luotu)
        --VALUES (7.0, 2019, 3, 'MAKU 2015', urakka_id, 800, kayttaja_id, NOW());
        -- Suolauksen toteuma (materiaalitoteuma)
        --INSERT INTO toteuma (lahde, urakka, sopimus, luotu, alkanut, paattynyt, tyyppi, suorittajan_nimi, suorittajan_ytunnus, lisatieto)
        --VALUES ('harja-ui'::lahde, urakka_id, sopimus_id, NOW(), '2020-03-01 13:00:00+02', '2020-03-31 13:00:00+02',
        --       'kokonaishintainen'::toteumatyyppi, 'Sami Suolaaja', '4153724-6', 'Sami-Suolaaja-2020');

        --INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara)
        --VALUES ((SELECT id FROM toteuma WHERE lisatieto = 'Sami-Suolaaja-2020'), NOW(),
        --        (SELECT id FROM materiaalikoodi WHERE nimi='Talvisuola'), 1500);


        INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta, luotu, luoja)
        VALUES (urakka_id, 1, 250000, NULL, 1.1 * 250000, NOW(), kayttaja_id);

    END
$$ LANGUAGE plpgsql;


-- MHU hoidon johto
-- Johto - ja hallintokorvaukset
DO
$$
    DECLARE
        urakka_id                     INTEGER;
        sopimus_id                    INTEGER;
        tpi                           INTEGER;
        kayttaja_id                   INTEGER;
        toimenpidekoodi_tyonjohto     INTEGER;
        tehtavaryhma_erillishankinnat INTEGER;
        vuosi                         INTEGER;
        ennen_urakkaa                 BOOLEAN;
    BEGIN
        urakka_id := (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024');
        sopimus_id := (SELECT id FROM sopimus WHERE urakka = urakka_id AND paasopimus IS null); --MHU Oulu sopimus
        tpi := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP');
        kayttaja_id := (select id from kayttaja where kayttajanimi = 'Integraatio');
        toimenpidekoodi_tyonjohto := (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto');
        tehtavaryhma_erillishankinnat := (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');
        vuosi := (SELECT extract(year from NOW()));
        ennen_urakkaa := FALSE;

        INSERT INTO johto_ja_hallintokorvaus
        ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, "ennen-urakkaa", luotu, "toimenkuva-id")
        VALUES (urakka_id, 5, 40, vuosi, 3, ennen_urakkaa, NOW(),
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));


        -- Bonukset - 10/2019
        -- Erilliskustannukset - Alihankintabonus - lupausbonus - tktt-bonus - tavoitepalkkio
        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('alihankintabonus', sopimus_id, urakka_id, tpi, '2019-10-15', 1000, 'MAKU 2015',
                'Alihankittu hyvin!', '2019-10-13', kayttaja_id);

        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('lupausbonus', sopimus_id, urakka_id, tpi, '2019-10-15', 1000, 'MAKU 2015',
                'Hyvin luvattu!', '2019-10-13', kayttaja_id);

        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('tktt-bonus', sopimus_id, urakka_id, tpi, '2019-10-15', 1000, 'MAKU 2015',
                'Hoiditpa tktt jutut hyvin!', '2019-10-13', kayttaja_id);

        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('tavoitepalkkio', sopimus_id, urakka_id, tpi, '2019-10-15', 1000, 'MAKU 2015',
                'Pääsit tavoitteisiin!', '2019-10-13', kayttaja_id);


        -- Bonukset - 03/2020
        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('alihankintabonus', sopimus_id, urakka_id, tpi, '2020-03-15', 500, 'MAKU 2015',
                'Alihankittu hyvin!', '2020-03-13', kayttaja_id);

        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('lupausbonus', sopimus_id, urakka_id, tpi, '2020-03-15', 500, 'MAKU 2015',
                'Hyvin luvattu!', '2020-03-13', kayttaja_id);

        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('tktt-bonus', sopimus_id, urakka_id, tpi, '2020-03-15', 500, 'MAKU 2015',
                'Hoiditpa tktt jutut hyvin!', '2020-03-13', kayttaja_id);

        INSERT INTO erilliskustannus
        (tyyppi, sopimus, urakka, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, luotu, luoja)
        VALUES ('tavoitepalkkio', sopimus_id, urakka_id, tpi, '2020-03-15', 500, 'MAKU 2015',
                'Pääsit tavoitteisiin!', '2020-03-13', kayttaja_id);


        -- Hoidonjohdon palkkiot - 10/2019
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
        VALUES (2019, 10, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, toimenpidekoodi_tyonjohto, null, tpi, sopimus_id,
                kayttaja_id);
        -- Hoidonjohdon palkkiot - 03/2020
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
        VALUES (2020, 03, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, toimenpidekoodi_tyonjohto, null, tpi, sopimus_id,
                kayttaja_id);

        -- Erillishankinnat - 10/2019
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
        VALUES (2019, 10, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, NULL, tehtavaryhma_erillishankinnat, tpi, sopimus_id,
                kayttaja_id);
        -- Erillishankinnat - 03/2020
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi,
                                           sopimus, luoja)
        VALUES (2020, 03, 50, 'laskutettava-tyo'::TOTEUMATYYPPI, NULL, tehtavaryhma_erillishankinnat, tpi, sopimus_id,
                kayttaja_id);

    END
$$ LANGUAGE plpgsql;

