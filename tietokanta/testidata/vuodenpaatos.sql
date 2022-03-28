-- Tätä tiedostoa käytetään mhu-kustannusten-kirjaus - cypress-testeissä.
DO
$$
    DECLARE
        urakka_id_            INT                := (SELECT id
                                                     FROM urakka
                                                     WHERE nimi = 'Kittilän MHU 2019-2024');
        vuosi_                INT;
        tpi                   INT;
        sopimus_id            INT                := (SELECT id
                                                     FROM sopimus
                                                     WHERE nimi = 'Kittilän MHU sopimus');
        kayttaja_             INT                := (SELECT id
                                                     FROM kayttaja
                                                     WHERE kayttajanimi = 'Integraatio');
        indeksikorjattu_summa INT;
        tr                    INT;
        tpk                   INT;
        toimenkuvat           INT[]              := (SELECT array_agg(id)
                                                     FROM johto_ja_hallintokorvaus_toimenkuva
                                                     WHERE toimenkuva IN
                                                           ('sopimusvastaava', 'apulainen/työnjohtaja',
                                                            'harjoittelija'));
        vahvistettavat_osiot  SUUNNITTELU_OSIO[] := ARRAY ['hankintakustannukset', 'erillishankinnat', 'johto-ja-hallintokorvaus', 'hoidonjohtopalkkio', 'tavoite-ja-kattohinta'];
        osio_                 SUUNNITTELU_OSIO;
        kulun_id_             INT;
        toteuma_              NUMERIC;
    BEGIN

        -- Kiinteähintaiset, talvihoito
        tpi := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kittilä MHU Talvihoito TP');
        tr := (SELECT id FROM tehtavaryhma WHERE nimi = 'Talvihoito (A)');

        FOR kuukausi_ IN 1..12
            LOOP
                -- Suunnitellut
                vuosi_ := (CASE WHEN (kuukausi_ <= 9) THEN 2020 ELSE 2019 END);
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(12345, vuosi_, kuukausi_, urakka_id_));
                INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, tehtavaryhma, sopimus,
                                                  luotu, luoja, summa_indeksikorjattu, indeksikorjaus_vahvistettu,
                                                  vahvistaja, versio)
                VALUES (vuosi_, kuukausi_, 12345, tpi, tr, sopimus_id, now(), kayttaja_, indeksikorjattu_summa, now(),
                        kayttaja_, 0)
                ON CONFLICT DO NOTHING;

                -- Toteutuneet, alitetaan tavoitehinta
                INSERT INTO kulu (tyyppi, kokonaissumma, erapaiva, urakka, luotu, luoja,
                                  koontilaskun_kuukausi)
                VALUES ('laskutettava', indeksikorjattu_summa * 0.9,
                        (vuosi_::TEXT || '-' || kuukausi_::TEXT || '-' || '15')::DATE, urakka_id_, NOW(),
                        kayttaja_, kuukauden_nimi(kuukausi_) || '-1-hoitovuosi')
                RETURNING id INTO kulun_id_;

                INSERT INTO kulu_kohdistus (rivi, kulu, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, luotu,
                                            luoja)
                VALUES (0, kulun_id_, indeksikorjattu_summa * 0.9, tpi, tr, 'kokonaishintainen', NOW(), kayttaja_);
            END LOOP;

        -- kustannusarvioidut
        tpi := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kittilä MHU Hallinnolliset toimenpiteet TP');
        tr := (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');
        FOR kuukausi_ IN 1..12
            LOOP
                vuosi_ := (CASE WHEN (kuukausi_ <= 9) THEN 2020 ELSE 2019 END);

                -- Erillishankinnat
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(500, vuosi_, kuukausi_, urakka_id_));
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtavaryhma, toimenpideinstanssi,
                                                   sopimus, luotu, luoja, summa_indeksikorjattu,
                                                   indeksikorjaus_vahvistettu, vahvistaja, osio)
                VALUES (vuosi_, kuukausi_, 500, 'laskutettava-tyo', tr, tpi, sopimus_id, now(), kayttaja_,
                        indeksikorjattu_summa, NOW(), kayttaja_, 'erillishankinnat')
                ON CONFLICT DO NOTHING;

                -- JJHK, muut kulut
                tpk := (SELECT id
                        FROM toimenpidekoodi
                        WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'); -- Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(3.50, vuosi_, kuukausi_, urakka_id_));
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, toimenpideinstanssi,
                                                   sopimus, luotu, luoja, summa_indeksikorjattu,
                                                   indeksikorjaus_vahvistettu, vahvistaja, osio)
                VALUES (vuosi_, kuukausi_, 3.50, 'laskutettava-tyo', tpk, tpi, sopimus_id, now(), kayttaja_,
                        indeksikorjattu_summa, NOW(), kayttaja_, 'johto-ja-hallintokorvaus')
                ON CONFLICT DO NOTHING;

                -- HJ-palkkio
                tpk := (SELECT id
                        FROM toimenpidekoodi
                        WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'); -- Hoidonjohtopalkkio
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(7000, vuosi_, kuukausi_, urakka_id_));
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, toimenpideinstanssi,
                                                   sopimus, luotu, luoja, summa_indeksikorjattu,
                                                   indeksikorjaus_vahvistettu, vahvistaja, osio)
                VALUES (vuosi_, kuukausi_, 7000, 'laskutettava-tyo', tpk, tpi, sopimus_id, now(), kayttaja_,
                        indeksikorjattu_summa, NOW(), kayttaja_, 'hoidonjohtopalkkio')
                ON CONFLICT DO NOTHING;

            END LOOP;

        -- Johto- ja hallintakorvaus, tuntipalkat.
        FOR kuukausi_ IN 1..12
            LOOP
                vuosi_ := (CASE WHEN (kuukausi_ <= 9) THEN 2020 ELSE 2019 END);
                INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu, luoja,
                                                      vuosi, kuukausi, tuntipalkka_indeksikorjattu,
                                                      indeksikorjaus_vahvistettu, vahvistaja)
                VALUES (urakka_id_, toimenkuvat[1], 120, 20, NOW(), kayttaja_, vuosi_, kuukausi_,
                        testidata_indeksikorjaa(20, vuosi_, kuukausi_, urakka_id_),
                        NOW(), kayttaja_)
                ON CONFLICT DO NOTHING;

                IF kuukausi_ IN (1, 2, 3, 4, 10, 11, 12) THEN
                    -- Apulaisjohtaja töissä vain loka-huhtikuun
                    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu,
                                                          luoja,
                                                          vuosi, kuukausi, tuntipalkka_indeksikorjattu,
                                                          indeksikorjaus_vahvistettu, vahvistaja)
                    VALUES (urakka_id_, toimenkuvat[2], 60, 50, NOW(), kayttaja_, vuosi_, kuukausi_,
                            testidata_indeksikorjaa(50, vuosi_, kuukausi_, urakka_id_),
                            NOW(), kayttaja_)
                    ON CONFLICT DO NOTHING;
                END IF;

                IF kuukausi_ BETWEEN 5 AND 8 THEN
                    -- Harjoittelija vain touko-elokuussa
                    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu,
                                                          luoja,
                                                          vuosi, kuukausi, tuntipalkka_indeksikorjattu,
                                                          indeksikorjaus_vahvistettu, vahvistaja)
                    VALUES (urakka_id_, toimenkuvat[3], 240, 80, NOW(), kayttaja_, vuosi_, kuukausi_,
                            testidata_indeksikorjaa(80, vuosi_, kuukausi_, urakka_id_),
                            NOW(), kayttaja_)
                    ON CONFLICT DO NOTHING;
                END IF;
            END LOOP;

        -- Tavoite- ja kattohinta
        INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, luotu, luoja, muokattu, muokkaaja,
                                    tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu,
                                    indeksikorjaus_vahvistettu, vahvistaja, tarjous_tavoitehinta)
        VALUES (urakka_id_, 1, 364782, 401260.2, NOW(), kayttaja_, NOW(), kayttaja_,
                testidata_indeksikorjaa(364782, 2019, 10, urakka_id_),
                testidata_indeksikorjaa(401260.2, 2019, 10, urakka_id_),
                NOW(), kayttaja_, 40000)
        ON CONFLICT DO NOTHING;
        FOR vuosi_ IN 2..4
            LOOP
                INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, luotu, luoja, muokattu,
                                            muokkaaja,
                                            tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu,
                                            indeksikorjaus_vahvistettu, vahvistaja, tarjous_tavoitehinta)
                VALUES (urakka_id_, vuosi_, 0, 0, NOW(), kayttaja_, NOW(), kayttaja_, 0, 0, NOW(), kayttaja_, 40000)
                ON CONFLICT DO NOTHING;
            END LOOP;

        FOREACH osio_ in ARRAY vahvistettavat_osiot
            LOOP
                INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja, luotu,
                                                                    vahvistaja, vahvistus_pvm)
                VALUES (urakka_id_, osio_, 1, true, kayttaja_, NOW(), kayttaja_, NOW())
                ON CONFLICT DO NOTHING;
            END LOOP;


        PERFORM siirra_budjetoidut_tyot_toteumiin(current_date);

        toteuma_ := (SELECT SUM(summa_indeksikorjattu)
                     FROM toteutuneet_kustannukset tk
                     WHERE tk.urakka_id = urakka_id_
                       AND (tk.vuosi = 2019
                         OR
                            tk.vuosi = 2020 AND kuukausi <= 9)) +
                    (SELECT SUM(summa) FROM kulu_kohdistus
                     WHERE toimenpideinstanssi =
                           (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kittilä MHU Talvihoito TP'));

        INSERT INTO urakka_paatos ("hoitokauden-alkuvuosi", "urakka-id", "urakoitsijan-maksu", "tilaajan-maksu", siirto,
                                   tyyppi, muokattu, "muokkaaja-id", "luoja-id")
        VALUES (2019, urakka_id_, ((SELECT tavoitehinta_indeksikorjattu
                                    FROM urakka_tavoite
                                    WHERE urakka = urakka_id_
                                      AND hoitokausi = 1) - toteuma_) * -0.3, 0, 0, 'tavoitehinnan-alitus', NOW(),
                kayttaja_, kayttaja_);

    END
$$;