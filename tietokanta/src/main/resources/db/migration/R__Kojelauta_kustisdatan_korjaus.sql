CREATE OR REPLACE FUNCTION korjaa_kustannussuunnitelmien_puuttuvat_tilat(hoitokauden_alkuvuosi INTEGER)
    RETURNS BOOLEAN AS $$
DECLARE
    hankintakustannus_rivi RECORD;
    kustannusarvoitutyo_rivi RECORD;
    kustannusarvioidut_osiot TEXT[] := ARRAY['erillishankinnat', 'tavoitehintaiset-rahavaraukset', 'hoidonjohtopalkkio'];
    kust_arv_osio TEXT;
    johto_ja_hallintokorvaus_rivi RECORD;
    tavoite_ja_kattohinta_rivi RECORD;
    hoitourakka_idt INTEGER[];
    urakkaid INTEGER;
    hoitokauden_jarjestysluku INTEGER;
    integraatiokayttaja INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');


BEGIN
    -- valitaan annetun hoitokauden osalta käynnissä olleet urakat
    SELECT ARRAY (SELECT id FROM urakka u WHERE u.tyyppi = 'teiden-hoito' AND
                                                make_date(hoitokauden_alkuvuosi, 10, 1) BETWEEN u.alkupvm and u.loppupvm)
      into hoitourakka_idt;

    -- Kustannusssuunitelman tilatiedot löytyvät taulusta suunnittelu_kustannussuunnitelman_tila ja ne rakentuvat ao. logiikan mukaisesti.
    -- Tilataulu otettiin käyttöön vasta 2021 lopulla, joten kaikki se tieto, jota on viimeksi muokattu tuota ajankohtaa ennen, ei ole vielä tilataulussa.

    -- hankintakustannukset: kiinteahintainen_tyo --> suunnittelu_kustannussuunnitelman_tila (osio: hankintakustannukset)
    -- erillishankinnat: kustannusarvioitu_tyo (osio: erillishankinnat) --> suunnittelu_kustannussuunnitelman_tila (osio: erillishankinnat)
    -- tavoitehintaiset-rahavaraukset: kustannusarvioitu_tyo (osio: tavoitehintaiset-rahavaraukset) --> suunnittelu_kustannussuunnitelman_tila (osio: tavoitehintaiset-rahavaraukset)
    -- tilaajan-rahavaraukset: tämä on deprikoitunut, jätetään huomiotta
    -- johto_ja_hallintokorvaus: johto_ja_hallintokorvaus --> suunnittelu_kustannussuunnitelman_tila (osio: johto_ja_hallintokorvaus)
    -- hoidonjohtopalkkio: kustannusarvioitu_tyo (osio: hoidonjohtopalkkio) --> suunnittelu_kustannussuunnitelman_tila (osio: hoidonjohtopalkkio)
    -- tavoite_ja_kattohinta: urakka_tavoite (vain vahvistetut siirretään) --> suunnittelu_kustannussuunnitelman_tila (osio: tavoite_ja_kattohinta)
    -- huom: tavoite- ja kattohinnalla ei ole tilariviä muuta kuin vahvistettu-tilassa. Eli jos jokin osio on vielä vahvistamatta, myös tavoite- ja kattohinnan
    -- tulkitaan olevan vahvistamatta urakan_kustannussuunnitelman_tila -funktion logiikan mukaisesti, vaikkei niistä luodakaan riviä tilatauluun.

    FOREACH urakkaid IN ARRAY hoitourakka_idt LOOP
        SELECT * FROM monesko_hoitokausi((SELECT alkupvm FROM urakka where id = urakkaid),
                                         (SELECT loppupvm FROM urakka where id = urakkaid),
                      hoitokauden_alkuvuosi) into hoitokauden_jarjestysluku;
        RAISE NOTICE 'Korjataan kustannussuunnitelman tilatieto urakalle %, nimi %:', urakkaid, (SELECT nimi FROM urakka where id = urakkaid);
        RAISE NOTICE 'Hoitokauden alkuvuosi: % ja hoitokauden järjestysluku: %', hoitokauden_alkuvuosi, hoitokauden_jarjestysluku;

        -- Haetaan urakan kiinteähintaista työtä eli hankintakustannuksia
        SELECT *
        FROM kiinteahintainen_tyo
       WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
         AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
              (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9)) LIMIT 10 INTO hankintakustannus_rivi;

        -- Jos on olemassa hankintakustannusten rivi, mutta ei vielä vastaavaa tietoa tilataulussa, lisätään tilatauluun rivi
        -- olemassaolo on testattava jonkin RECORD-tyypin aina läsnä olevan kentän olemassaoloa tarkastelemalla. Tyhjäkin RECORD
        -- "on olemassa", vaikka sen sisältö olisi täynnä NULL:ia (,,,,,,,,,,,)
        IF hankintakustannus_rivi.id IS NOT NULL AND
           ((SELECT count(*)
             FROM suunnittelu_kustannussuunnitelman_tila
            WHERE urakka = urakkaid AND osio = 'hankintakustannukset' AND hoitovuosi = hoitokauden_jarjestysluku) = 0) THEN
            RAISE NOTICE 'Hankintakustannus on kirjattu mutta sen riviä ei ollut suunnittelu_kustannussuunnitelman_tila -taulussa, insertoidaan urakkaan: %...', urakkaid;

            INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja, luotu, vahvistaja, vahvistus_pvm)

            VALUES (urakkaid, 'hankintakustannukset', hoitokauden_jarjestysluku,
                    hankintakustannus_rivi.vahvistaja IS NOT NULL, integraatiokayttaja, NOW(),
            hankintakustannus_rivi.vahvistaja, hankintakustannus_rivi.indeksikorjaus_vahvistettu)
            ON CONFLICT (urakka, osio, hoitovuosi) DO UPDATE SET
                vahvistaja = hankintakustannus_rivi.vahvistaja,
                vahvistettu = hankintakustannus_rivi.vahvistaja IS NOT NULL,
                vahvistus_pvm = hankintakustannus_rivi.indeksikorjaus_vahvistettu,
                muokattu = NOW(),
                muokkaaja = integraatiokayttaja;
        END IF;

        -- Käsitellään tässä samassa koodissa loopin avulla loput osiot, koska ne kaikki löytyvät taulusta kustannusarvioitu_tyo:

        FOREACH kust_arv_osio IN ARRAY kustannusarvioidut_osiot LOOP
                SELECT id, osio, indeksikorjaus_vahvistettu, vahvistaja, luotu, luoja, muokkaaja, muokattu
                  FROM kustannusarvioitu_tyo
                 WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
                   AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
                        (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
                   AND osio = kust_arv_osio::suunnittelu_osio LIMIT 1 INTO kustannusarvoitutyo_rivi;

                -- rivimn olemassaolo on testattava jonkin RECORD-tyypin aina läsnä olevan kentän olemassaoloa tarkastelemalla.
                IF kustannusarvoitutyo_rivi.id IS NOT NULL AND
                   ((SELECT count(*)
                     FROM suunnittelu_kustannussuunnitelman_tila
                    WHERE urakka = urakkaid AND osio = kust_arv_osio::suunnittelu_osio AND hoitovuosi = hoitokauden_jarjestysluku) = 0) THEN
                    RAISE NOTICE 'kustannusarvoitutyo_rivi on olemassa osioon %, muttei ollut suunnittelu_kustannussuunnitelman_tila -taulussa, insertoidaan urakkaan: %...' , kust_arv_osio, urakkaid;

                    INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja, luotu, vahvistaja, vahvistus_pvm)
                    VALUES (urakkaid, kust_arv_osio::suunnittelu_osio, hoitokauden_jarjestysluku,
                            kustannusarvoitutyo_rivi.vahvistaja IS NOT NULL, integraatiokayttaja, NOW(),
                            kustannusarvoitutyo_rivi.vahvistaja, kustannusarvoitutyo_rivi.indeksikorjaus_vahvistettu)
                        ON CONFLICT (urakka, osio, hoitovuosi) DO NOTHING;
                    IF NOT FOUND THEN RAISE WARNING 'suunnittelu_kustannussuunnitelman_tila insert osioon % odottamattomasti epäonnistui.', kust_arv_osio;
                    END IF;
                END IF;
            END LOOP;

        -- johto- ja hallintokorvaus on erillisessä taulussa, joten sille oma, joskin samankaltainen, käsittelynsä
        SELECT id, indeksikorjaus_vahvistettu, vahvistaja, luotu, luoja, muokkaaja, muokattu
          FROM johto_ja_hallintokorvaus
         WHERE "urakka-id" = urakkaid
           AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
                (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
         LIMIT 1 INTO johto_ja_hallintokorvaus_rivi;

        -- rivimn olemassaolo on testattava jonkin RECORD-tyypin aina läsnä olevan kentän olemassaoloa tarkastelemalla.
        IF johto_ja_hallintokorvaus_rivi.id IS NOT NULL AND
           ((SELECT count(*)
               FROM suunnittelu_kustannussuunnitelman_tila
              WHERE urakka = urakkaid AND osio = 'johto-ja-hallintokorvaus' AND hoitovuosi = hoitokauden_jarjestysluku) = 0) THEN
            RAISE NOTICE 'johto_ja_hallintokorvaus_rivi on olemassa, muttei ollut suunnittelu_kustannussuunnitelman_tila -taulussa, insertoidaan urakkaan: %...', urakkaid;

            INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja, luotu, vahvistaja, vahvistus_pvm)
            VALUES (urakkaid, 'johto-ja-hallintokorvaus', hoitokauden_jarjestysluku,
                    johto_ja_hallintokorvaus_rivi.vahvistaja IS NOT NULL, integraatiokayttaja, NOW(),
                    johto_ja_hallintokorvaus_rivi.vahvistaja, johto_ja_hallintokorvaus_rivi.indeksikorjaus_vahvistettu)
                ON CONFLICT (urakka, osio, hoitovuosi) DO NOTHING;
            IF NOT FOUND THEN RAISE WARNING 'suunnittelu_kustannussuunnitelman_tila insert osioon johto-ja-hallintokorvaus odottamattomasti epäonnistui.';
            END IF;
        END IF;

        -- Lisätään vielä tavoite- ja kattohinnan päivitys tilaan, mutta se tehdään vain jos se on vahvistettu (kuten sovelluslogiikassa)
        SELECT * FROM urakka_tavoite
                 WHERE urakka = urakkaid AND hoitokausi = hoitokauden_jarjestysluku
        AND vahvistaja IS NOT NULL INTO tavoite_ja_kattohinta_rivi;

        IF tavoite_ja_kattohinta_rivi.id IS NOT NULL AND
           ((SELECT count(*)
               FROM suunnittelu_kustannussuunnitelman_tila
              WHERE urakka = urakkaid AND osio = 'tavoite-ja-kattohinta' AND hoitovuosi = hoitokauden_jarjestysluku) = 0) THEN
            RAISE NOTICE 'tavoite_ja_kattohinta_rivi on olemassa, muttei ollut suunnittelu_kustannussuunnitelman_tila -taulussa, insertoidaan urakkaan: %...', urakkaid;

            INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja, luotu, vahvistaja, vahvistus_pvm)
            VALUES (urakkaid, 'tavoite-ja-kattohinta', hoitokauden_jarjestysluku,
                    tavoite_ja_kattohinta_rivi.vahvistaja IS NOT NULL, integraatiokayttaja, NOW(),
                    tavoite_ja_kattohinta_rivi.vahvistaja, tavoite_ja_kattohinta_rivi.indeksikorjaus_vahvistettu)
                ON CONFLICT (urakka, osio, hoitovuosi) DO NOTHING;
            IF NOT FOUND THEN RAISE WARNING 'suunnittelu_kustannussuunnitelman_tila insert osioon tavoite-ja-kattohinta odottamattomasti epäonnistui.';
            END IF;
        END IF;
END LOOP;




    RETURN TRUE;
END;

$$ LANGUAGE plpgsql;

