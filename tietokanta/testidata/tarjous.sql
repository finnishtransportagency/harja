-- Lisätään tarjouksen tiedot testiä varten
-- Toimenkuvaid:t on kovakoodattu. Voi olla, että tämä hajoaa sen vuoksi joskus.
-- Rahavarausid:t on kovakoodattu. Voi olla, että tämä hajoaa sen vuoksi joskus.
DO
$$
    DECLARE
        urakkaid   INTEGER;
        sopimusid   INTEGER;
        kayttajaid INTEGER;
        tarjousid1 INTEGER := 1;
        tarjousid2 INTEGER := 2;
        tarjousid3 INTEGER := 3;
        tarjousid4 INTEGER := 4;
        tarjousid5 INTEGER := 5;
    BEGIN
        urakkaid = (SELECT id FROM urakka where nimi = 'POP MHU Kajaani 2025-2030');
        sopimusid = (SELECT id FROM sopimus where urakka = urakkaid);
        kayttajaid = (SELECT id FROM kayttaja where kayttajanimi = 'Integraatio');

        RAISE NOTICE 'Urakka ID: %, Käyttäjä ID: %', urakkaid, kayttajaid;

        -- Tarjoukset
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid1, 2025, urakkaid, 1988273.50, 2385928.20, NOW(), kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid2, 2026, urakkaid, 1988273.50, 2385928.20, NOW(), kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid3, 2027, urakkaid, 1988273.50, 2385928.20, NOW(), kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid4, 2028, urakkaid, 1988273.50, 2385928.20, NOW(), kayttajaid, null,
                null);
        INSERT INTO tarjous (id, hoitokauden_alkuvuosi, urakka_id, tarjous_tavoitehinta, tarjous_kattohinta,
                             luotu, luoja, muokattu, muokkaaja)
        VALUES (tarjousid5, 2029, urakkaid, 1988273.50, 2385928.20, NOW(), kayttajaid, null,
                null);


        --- Johto- ja hallintokorvaukset
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (1, tarjousid1, urakkaid, 2025, 5000.00, 'johto-ja-hallintokorvaus',
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'valmistelukausi ennen urakka-ajan alkua' AND "urakka-id" = urakkaid), 
                kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (2, tarjousid1, urakkaid, 2025, 90300.00, 'johto-ja-hallintokorvaus',
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja' AND "urakka-id" = urakkaid),
                kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (3, tarjousid1, urakkaid, 2025, 72943.50, 'johto-ja-hallintokorvaus',
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '2. työnjohtaja' AND "urakka-id" = urakkaid),
                kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (4, tarjousid1, urakkaid, 2025, 10.00, 'johto-ja-hallintokorvaus',
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '3. työnjohtaja' AND "urakka-id" = urakkaid), 
                kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (5, tarjousid1, urakkaid, 2025, 10.00, 'johto-ja-hallintokorvaus',
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö' AND "urakka-id" = urakkaid), 
                kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (6, tarjousid1, urakkaid, 2025, 10.00, 'johto-ja-hallintokorvaus',
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava' AND "urakka-id" = urakkaid), 
                kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (7, tarjousid1, urakkaid, 2025, 10000.00, 'johto-ja-hallintokorvaus',
                (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija' AND "urakka-id" = urakkaid), 
                kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (8, tarjousid2, urakkaid, 2026, 90300.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (9, tarjousid2, urakkaid, 2026, 72943.50, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '2. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (10, tarjousid2, urakkaid, 2026, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '3. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (11, 2, urakkaid, 2026, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö' AND "urakka-id" = urakkaid), kayttajaid, NOW(), null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (12, tarjousid2, urakkaid, 2026, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (13, tarjousid2, urakkaid, 2026, 10000.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (14, tarjousid3, urakkaid, 2027, 90300.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja' AND "urakka-id" = urakkaid), 3, NOW(), null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (15, tarjousid3, urakkaid, 2027, 72943.50, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '2. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (16, tarjousid3, urakkaid, 2027, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '3. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (17, tarjousid3, urakkaid, 2027, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (18, tarjousid3, urakkaid, 2027, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (19, tarjousid3, urakkaid, 2027, 10000.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (20, tarjousid4, urakkaid, 2028, 90300.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (21, 4, urakkaid, 2028, 72943.50, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '2. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid, NOW(), null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (22, tarjousid4, urakkaid, 2028, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '3. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (23, tarjousid4, urakkaid, 2028, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (24, tarjousid4, urakkaid, 2028, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (25, tarjousid4, urakkaid, 2028, 10000.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (26, tarjousid5, urakkaid, 2029, 90300.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (27, 5, urakkaid, 2029, 72943.50, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '2. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid, NOW(), null,
                null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (28, tarjousid5, urakkaid, 2029, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = '3. työnjohtaja' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (29, tarjousid5, urakkaid, 2029, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (30, tarjousid5, urakkaid, 2029, 0.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava' AND "urakka-id" = urakkaid), kayttajaid, NOW(),
                null, null,
                'vuosi');
        INSERT INTO tarjous_johto_ja_hallintokorvaus (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio,
                                                      johto_ja_hallintokorvaus_toimenkuva_id, luoja, luotu, muokattu,
                                                      muokkaaja, maksukausi)
        VALUES (31, tarjousid5, urakkaid, 2029, 10000.00, 'johto-ja-hallintokorvaus', (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija' AND "urakka-id" = urakkaid), kayttajaid,
                NOW(), null, null,
                'vuosi');

        -- Kilpailutettavat hankinnat, rahavaraukset, erillishankinnat ja hoidonjohtopalkkiot
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (1, tarjousid1, urakkaid, 2025, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (2, tarjousid1, urakkaid, 2025, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, NOW(), null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (3, tarjousid1, urakkaid, 2025, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (4, tarjousid1, urakkaid, 2025, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (5, tarjousid1, urakkaid, 2025, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (6, tarjousid1, urakkaid, 2025, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (7, tarjousid2, urakkaid, 2026, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (8, tarjousid2, urakkaid, 2026, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, NOW(), null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (9, tarjousid2, urakkaid, 2026, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (10, tarjousid2, urakkaid, 2026, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (11, tarjousid2, urakkaid, 2026, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (12, tarjousid2, urakkaid, 2026, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (13, tarjousid3, urakkaid, 2027, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (14, tarjousid3, urakkaid, 2027, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, NOW(), null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (15, tarjousid3, urakkaid, 2027, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (16, tarjousid3, urakkaid, 2027, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (17, tarjousid3, urakkaid, 2027, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (18, tarjousid3, urakkaid, 2027, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (19, tarjousid4, urakkaid, 2028, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (20, tarjousid4, urakkaid, 2028, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, NOW(), null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (21, tarjousid4, urakkaid, 2028, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (22, tarjousid4, urakkaid, 2028, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (23, tarjousid4, urakkaid, 2028, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (24, tarjousid4, urakkaid, 2028, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (25, tarjousid5, urakkaid, 2029, 1500000.00, 'hankintakustannukset', null, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (26, tarjousid5, urakkaid, 2029, 50000.00, 'erillishankinnat', null, 380, null, kayttajaid, NOW(), null,
                null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (27, tarjousid5, urakkaid, 2029, 230000.00, 'hoidonjohtopalkkio', 19434, null, null, kayttajaid, NOW(),
                null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (28, tarjousid5, urakkaid, 2029, 15000.00, 'tavoitehintaiset-rahavaraukset', null, null, 1, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (29, tarjousid5, urakkaid, 2029, 7000.00, 'tavoitehintaiset-rahavaraukset', null, null, 2, kayttajaid,
                NOW(), null, null);
        INSERT INTO tarjous_kustannukset (id, tarjous_id, urakka_id, hoitokauden_alkuvuosi, summa, osio, tehtava_id,
                                          tehtavaryhma_id, rahavaraus_id, luoja, luotu, muokattu, muokkaaja)
        VALUES (30, tarjousid5, urakkaid, 2029, 8000.00, 'tavoitehintaiset-rahavaraukset', null, null, 3, kayttajaid,
                NOW(), null, null);

        -- urakka_tavoite - muutamana vuotena ei voi olla indeksikorjattua tavoite- ja kattohintaa, koska indeksikorjaus tehdään indexin MAKU2020 perusteella ja sitä ei ole vielä kannassa.
        INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, luotu, luoja, tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu, laskutusraja, laskutusraja_alkuperainen, tarjous_tavoitehinta) VALUES
        (urakkaid, 1, 1988273.5, 2385928.2, NOW(), kayttajaid, 2091663.722, 2509996.4664, 2091663.722, 2091663.722, 1988273.5),
        (urakkaid, 2, 1988273.5, 2385928.2, NOW(), kayttajaid, 2219249.4765, 2509996.4664, 2219249.4765, 2219249.4765, 1988273.5),
        (urakkaid, 3, 1988273.5, 2385928.2, NOW(), kayttajaid, null, null, null, null, 1988273.5),
        (urakkaid, 4, 1988273.5, 2385928.2, NOW(), kayttajaid, null, null, null, null, 1988273.5),
        (urakkaid, 5, 1988273.5, 2385928.2, NOW(), kayttajaid, null, null, null, null, 1988273.5);


        -- Lisätään vielä rahavaraukset kustannusarvioitu_tyo tauluun
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 1, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 2, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 3, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 4, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 5, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 6, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 7, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 8, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 9, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 10, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 11, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 12, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1315, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 1, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 2, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 3, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 4, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 5, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 6, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 7, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 8, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 9, 583.37, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.70524, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 10, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 11, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 12, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 613.66316, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 1, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 2, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 3, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 4, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 5, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 6, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 7, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 8, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 9, 666.63, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.29476, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 10, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 11, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2025, 12, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 701.33684, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 1, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 2, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 3, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 4, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 5, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 6, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 7, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 8, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 9, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 10, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 11, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 12, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 1398.75, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 1, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 2, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 3, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 4, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 5, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 6, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 7, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 8, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 9, 583.37, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.79103, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 10, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 11, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 12, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 652.74627, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 1, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 2, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 3, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 4, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 5, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 6, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 7, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 8, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 9, 666.63, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 745.95897, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 10, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 11, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2026, 12, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, 746.00373, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 1, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 2, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 3, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 4, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 5, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 6, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 7, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 8, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 9, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 10, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 11, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 12, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 1, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 2, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 3, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 4, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 5, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 6, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 7, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 8, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 9, 583.37, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 10, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 11, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 12, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 1, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 2, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 3, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 4, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 5, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 6, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 7, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 8, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 9, 666.63, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 10, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 11, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2027, 12, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 1, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 2, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 3, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 4, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 5, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 6, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 7, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 8, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 9, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 10, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 11, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 12, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 1, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 2, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 3, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 4, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 5, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 6, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 7, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 8, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 9, 583.37, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);

        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 10, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 11, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 12, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 1, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 2, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 3, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 4, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 5, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 6, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 7, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 8, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 9, 666.63, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 10, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 11, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2028, 12, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 1, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 2, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 3, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 4, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 5, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 6, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 7, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 8, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 9, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 10, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 11, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 12, 1250, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 1);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 1, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 2, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 3, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 4, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 5, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 6, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 7, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 8, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 9, 583.37, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 10, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 11, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 12, 583.33, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 2);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 1, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 2, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 3, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 4, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 5, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 6, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 7, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 8, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2030, 9, 666.63, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 10, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 11, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja, muokattu, muokkaaja, "siirretty?", summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id) VALUES (2029, 12, 666.67, 'laskutettava-tyo', null, null, 115, sopimusid, NOW(), 3, null, null, false, null, null, null, 0, 'tilaajan-rahavaraukset', 3);


        -- Koska tarjoukset lisätään kovakoodatulla id:llä, niin korjaa sekvenssit
        -- Resetoi sekvenssit vastaamaan taulujen maksimiarvoja
        PERFORM setval( pg_get_serial_sequence('tarjous', 'id'), (SELECT COALESCE(MAX(id), 1) FROM tarjous));
        PERFORM setval(pg_get_serial_sequence('tarjous_kustannukset', 'id'),(SELECT COALESCE(MAX(id), 1) FROM tarjous_kustannukset));
        PERFORM setval(pg_get_serial_sequence('tarjous_johto_ja_hallintokorvaus', 'id'),(SELECT COALESCE(MAX(id), 1) FROM tarjous_johto_ja_hallintokorvaus));

    END
$$ LANGUAGE plpgsql;
