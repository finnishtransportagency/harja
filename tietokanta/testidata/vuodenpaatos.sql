-- Tätä tiedostoa käytetään mhu-kustannusten-kirjaus - cypress-testeissä.
DO
$$
    DECLARE
        urakka_id             INT                := (SELECT id
                                                     FROM urakka
                                                     WHERE nimi = 'Kittilän MHU 2019-2024');
        vuosi_                INT;
        tpi                   INT;
        sopimus_id            INT                := (SELECT id
                                                     FROM sopimus
                                                     WHERE nimi = 'Kittilän MHU sopimus');
        kayttaja              INT                := (SELECT id
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
    BEGIN

        -- Kiinteähintaiset, talvihoito
        tpi := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kittilä MHU Talvihoito TP');

        FOR kuukausi_ IN 1..12
            LOOP
                vuosi_ := (CASE WHEN (kuukausi_ <= 9) THEN 2020 ELSE 2019 END);
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(12345, vuosi_, kuukausi_, urakka_id));
                INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus, luotu, luoja,
                                                  summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio)
                VALUES (vuosi_, kuukausi_, 12345, tpi, sopimus_id, now(), kayttaja, indeksikorjattu_summa, now(),
                        kayttaja, 0);
            END LOOP;

        -- kustannusarvioidut
        tpi := (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kittilä MHU Hallinnolliset toimenpiteet TP');
        tr := (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)');
        FOR kuukausi_ IN 1..12
            LOOP
                vuosi_ := (CASE WHEN (kuukausi_ <= 9) THEN 2020 ELSE 2019 END);

                -- Erillishankinnat
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(500, vuosi_, kuukausi_, urakka_id));
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtavaryhma, toimenpideinstanssi,
                                                   sopimus, luotu, luoja, summa_indeksikorjattu,
                                                   indeksikorjaus_vahvistettu, vahvistaja, osio)
                VALUES (vuosi_, kuukausi_, 500, 'laskutettava-tyo', tr, tpi, sopimus_id, now(), kayttaja,
                        indeksikorjattu_summa, NOW(), kayttaja, 'erillishankinnat');

                -- JJHK, muut kulut
                tpk := (SELECT id
                        FROM toimenpidekoodi
                        WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388');
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(3.50, vuosi_, kuukausi_, urakka_id));
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, toimenpideinstanssi,
                                                   sopimus, luotu, luoja, summa_indeksikorjattu,
                                                   indeksikorjaus_vahvistettu, vahvistaja, osio)
                VALUES (vuosi_, kuukausi_, 3.50, 'laskutettava-tyo', tpk, tpi, sopimus_id, now(), kayttaja,
                        indeksikorjattu_summa, NOW(), kayttaja, 'johto-ja-hallintokorvaus');

                -- HJ-palkkio
                tpk := (SELECT id
                        FROM toimenpidekoodi
                        WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8');
                indeksikorjattu_summa := (SELECT testidata_indeksikorjaa(7000, vuosi_, kuukausi_, urakka_id));
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, toimenpideinstanssi,
                                                   sopimus, luotu, luoja, summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, osio)
                VALUES (vuosi_, kuukausi_, 7000, 'laskutettava-tyo', tpk, tpi, sopimus_id, now(), kayttaja,
                        indeksikorjattu_summa, NOW(), kayttaja, 'hoidonjohtopalkkio');

            END LOOP;

        -- Johto- ja hallintakorvaus, tuntipalkat.
        FOR kuukausi_ IN 1..12
            LOOP
                vuosi_ := (CASE WHEN (kuukausi_ <= 9) THEN 2020 ELSE 2019 END);
                INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu, luoja,
                                                      vuosi, kuukausi, tuntipalkka_indeksikorjattu,
                                                      indeksikorjaus_vahvistettu, vahvistaja)
                VALUES (urakka_id, toimenkuvat[1], 120, 20, NOW(), kayttaja, vuosi_, kuukausi_,
                        testidata_indeksikorjaa(20, vuosi_, kuukausi_, urakka_id),
                        NOW(), kayttaja);

                IF kuukausi_ IN (1, 2, 3, 4, 10, 11, 12) THEN
                    -- Apulaisjohtaja töissä vain loka-huhtikuun
                    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu,
                                                          luoja,
                                                          vuosi, kuukausi, tuntipalkka_indeksikorjattu,
                                                          indeksikorjaus_vahvistettu, vahvistaja)
                    VALUES (urakka_id, toimenkuvat[2], 60, 50, NOW(), kayttaja, vuosi_, kuukausi_,
                            testidata_indeksikorjaa(50, vuosi_, kuukausi_, urakka_id),
                            NOW(), kayttaja);
                END IF;

                IF kuukausi_ BETWEEN 5 AND 8 THEN
                    -- Harjoittelija vain touko-elokuussa
                    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu,
                                                          luoja,
                                                          vuosi, kuukausi, tuntipalkka_indeksikorjattu,
                                                          indeksikorjaus_vahvistettu, vahvistaja)
                    VALUES (urakka_id, toimenkuvat[3], 240, 80, NOW(), kayttaja, vuosi_, kuukausi_,
                            testidata_indeksikorjaa(80, vuosi_, kuukausi_, urakka_id),
                            NOW(), kayttaja);
                END IF;
            END LOOP;

        -- Tavoite- ja kattohinta
        INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, luotu, luoja, muokattu, muokkaaja,
                                    tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu,
                                    indeksikorjaus_vahvistettu, vahvistaja, tarjous_tavoitehinta)
        VALUES (urakka_id, 1, 364782, 401260.2, NOW(), kayttaja, NOW(), kayttaja,
                testidata_indeksikorjaa(364782, 2019, 10, urakka_id),
                testidata_indeksikorjaa(401260.2, 2019, 10, urakka_id),
                NOW(), kayttaja, 40000);
        FOR vuosi_ IN 2..4
            LOOP
                INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, luotu, luoja, muokattu,
                                            muokkaaja,
                                            tavoitehinta_indeksikorjattu, kattohinta_indeksikorjattu,
                                            indeksikorjaus_vahvistettu, vahvistaja, tarjous_tavoitehinta)
                VALUES (urakka_id, vuosi_, 0, 0, NOW(), kayttaja, NOW(), kayttaja, 0, 0, NOW(), kayttaja, 40000);
            END LOOP;

        FOREACH osio_ in ARRAY vahvistettavat_osiot
            LOOP
                INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja, luotu,
                                                                    vahvistaja, vahvistus_pvm)
                VALUES (urakka_id, osio_, 1, true, kayttaja, NOW(), kayttaja, NOW());
            END LOOP;
    END
$$;