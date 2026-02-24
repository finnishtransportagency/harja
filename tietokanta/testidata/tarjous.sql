-- Lisätään tarjouksen tiedot testiä varten
-- Toimenkuvaid:t on kovakoodattu. Voi olla, että tämä hajoaa sen vuoksi joskus.
-- Rahavarausid:t on kovakoodattu. Voi olla, että tämä hajoaa sen vuoksi joskus.
DO
$$
    DECLARE
        urakkaid   INTEGER;
        kayttajaid INTEGER;
        tarjousid1 INTEGER := 1;
        tarjousid2 INTEGER := 2;
        tarjousid3 INTEGER := 3;
        tarjousid4 INTEGER := 4;
        tarjousid5 INTEGER := 5;
    BEGIN
        urakkaid = (SELECT id FROM urakka where nimi = 'POP MHU Kajaani 2025-2030');
        kayttajaid = (SELECT id FROM kayttaja where kayttajanimi = 'Integraatio');

        RAISE NOTICE 'Urakka ID: %, Käyttäjä ID: %', urakkaid, kayttajaid;
        --RAISE NOTICE 'Toimenkuvat %', (SELECT * from johto_ja_hallintokorvaus_toimenkuva);

        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid1, 2025, urakkaid, 2169661.50, 2603593.80, '2026-02-23 07:19:51.152977', kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid2, 2026, urakkaid, 2164631.50, 2597557.80, '2026-02-23 07:19:51.152977', kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid3, 2027, urakkaid, 2164631.50, 2597557.80, '2026-02-23 07:19:51.152977', kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid4, 2028, urakkaid, 2164631.50, 2597557.80, '2026-02-23 07:19:51.152977', kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid5, 2029, urakkaid, 2164631.50, 2597557.80, '2026-02-23 07:19:51.152977', kayttajaid, null,
                null);


        --- Johto- ja hallintokorvaukset
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (1, tarjousid1, urakkaid, 2025, 5000.00, 'johto-ja-hallintokorvaus', 32, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (2, tarjousid1, urakkaid, 2025, 90300.00, 'johto-ja-hallintokorvaus', 33, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (3, tarjousid1, urakkaid, 2025, 72943.50, 'johto-ja-hallintokorvaus', 34, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (4, tarjousid1, urakkaid, 2025, 10.00, 'johto-ja-hallintokorvaus', 35, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (5, tarjousid1, urakkaid, 2025, 10.00, 'johto-ja-hallintokorvaus', 36, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (6, tarjousid1, urakkaid, 2025, 10.00, 'johto-ja-hallintokorvaus', 37, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (7, tarjousid1, urakkaid, 2025, 10000.00, 'johto-ja-hallintokorvaus', 38, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (8, tarjousid2, urakkaid, 2026, 90300.00, 'johto-ja-hallintokorvaus', 33, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (9, tarjousid2, urakkaid, 2026, 72943.50, 'johto-ja-hallintokorvaus', 34, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (10, tarjousid2, urakkaid, 2026, 0.00, 'johto-ja-hallintokorvaus', 35, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (11, 2, urakkaid, 2026, 0.00, 'johto-ja-hallintokorvaus', 36, kayttajaid, '2026-02-23 07:19:51.152977', null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (12, tarjousid2, urakkaid, 2026, 0.00, 'johto-ja-hallintokorvaus', 37, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (13, tarjousid2, urakkaid, 2026, 10000.00, 'johto-ja-hallintokorvaus', 38, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (14, tarjousid3, urakkaid, 2027, 90300.00, 'johto-ja-hallintokorvaus', 33, 3, '2026-02-23 07:19:51.152977', null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (15, tarjousid3, urakkaid, 2027, 72943.50, 'johto-ja-hallintokorvaus', 34, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (16, tarjousid3, urakkaid, 2027, 0.00, 'johto-ja-hallintokorvaus', 35, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (17, tarjousid3, urakkaid, 2027, 0.00, 'johto-ja-hallintokorvaus', 36, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (18, tarjousid3, urakkaid, 2027, 0.00, 'johto-ja-hallintokorvaus', 37, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (19, tarjousid3, urakkaid, 2027, 10000.00, 'johto-ja-hallintokorvaus', 38, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (20, tarjousid4, urakkaid, 2028, 90300.00, 'johto-ja-hallintokorvaus', 33, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (21, 4, urakkaid, 2028, 72943.50, 'johto-ja-hallintokorvaus', 34, kayttajaid, '2026-02-23 07:19:51.152977', null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (22, tarjousid4, urakkaid, 2028, 0.00, 'johto-ja-hallintokorvaus', 35, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (23, tarjousid4, urakkaid, 2028, 0.00, 'johto-ja-hallintokorvaus', 36, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (24, tarjousid4, urakkaid, 2028, 0.00, 'johto-ja-hallintokorvaus', 37, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (25, tarjousid4, urakkaid, 2028, 10000.00, 'johto-ja-hallintokorvaus', 38, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (26, tarjousid5, urakkaid, 2029, 90300.00, 'johto-ja-hallintokorvaus', 33, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (27, 5, urakkaid, 2029, 72943.50, 'johto-ja-hallintokorvaus', 34, kayttajaid, '2026-02-23 07:19:51.152977', null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (28, tarjousid5, urakkaid, 2029, 0.00, 'johto-ja-hallintokorvaus', 35, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (29, tarjousid5, urakkaid, 2029, 0.00, 'johto-ja-hallintokorvaus', 36, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (30, tarjousid5, urakkaid, 2029, 0.00, 'johto-ja-hallintokorvaus', 37, kayttajaid, '2026-02-23 07:19:51.152977',
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (31, tarjousid5, urakkaid, 2029, 10000.00, 'johto-ja-hallintokorvaus', 38, kayttajaid,
                '2026-02-23 07:19:51.152977', null, null,
                'vuosi');

        -- Kilpailutettavat hankinnat, rahavaraukset, erillishankinnat ja hoidonjohtopalkkiot
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (1, tarjousid1, urakkaid, 2025, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (2, tarjousid1, urakkaid, 2025, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, '2026-02-23 08:12:09.191964', null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (3, tarjousid1, urakkaid, 2025, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (4, tarjousid1, urakkaid, 2025, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (5, tarjousid1, urakkaid, 2025, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (6, tarjousid1, urakkaid, 2025, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (7, tarjousid2, urakkaid, 2026, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (8, tarjousid2, urakkaid, 2026, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, '2026-02-23 08:12:09.191964', null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (9, tarjousid2, urakkaid, 2026, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (10, tarjousid2, urakkaid, 2026, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (11, tarjousid2, urakkaid, 2026, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (12, tarjousid2, urakkaid, 2026, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (13, tarjousid3, urakkaid, 2027, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (14, tarjousid3, urakkaid, 2027, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, '2026-02-23 08:12:09.191964', null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (15, tarjousid3, urakkaid, 2027, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (16, tarjousid3, urakkaid, 2027, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (17, tarjousid3, urakkaid, 2027, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (18, tarjousid3, urakkaid, 2027, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (19, tarjousid4, urakkaid, 2028, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (20, tarjousid4, urakkaid, 2028, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, '2026-02-23 08:12:09.191964', null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (21, tarjousid4, urakkaid, 2028, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (22, tarjousid4, urakkaid, 2028, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (23, tarjousid4, urakkaid, 2028, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (24, tarjousid4, urakkaid, 2028, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (25, tarjousid5, urakkaid, 2029, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (26, tarjousid5, urakkaid, 2029, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, '2026-02-23 08:12:09.191964', null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (27, tarjousid5, urakkaid, 2029, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, '2026-02-23 08:12:09.191964',
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (28, tarjousid5, urakkaid, 2029, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (29, tarjousid5, urakkaid, 2029, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (30, tarjousid5, urakkaid, 2029, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                '2026-02-23 08:12:09.191964', null, null);

        -- Päivitä tarjouksen summa myös urakka_tavoite tauluun
        UPDATE urakka_tavoite
        SET tarjous_tavoitehinta = (SELECT SUM(summa) FROM tarjous_kustannukset WHERE tarjous_id = tarjousid1),
        tarjous_kattohinta = (SELECT SUM(summa)*1.2 FROM tarjous_kustannukset WHERE tarjous_id = tarjousid1)
        WHERE urakka = urakkaid; -- Ei välitetä hoitokaudesta, koska summa on kaikilla hiotokausilla (1-5) sama
    END
$$ LANGUAGE plpgsql;
