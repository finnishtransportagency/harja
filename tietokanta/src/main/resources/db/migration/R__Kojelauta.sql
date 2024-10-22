-- Kojelautaa, eli (hoito)urakoiden tietojen yleisnäkymää varten tarvittavia apufunktioita
CREATE OR REPLACE FUNCTION urakan_kustannussuunnitelman_tila(_urakka INTEGER, _hoitovuosi INTEGER)
    RETURNS jsonb AS $$

DECLARE
    aloittamattomia INTEGER;
    vahvistamattomia INTEGER;
    vahvistettuja INTEGER;
    suunnitelman_tila TEXT;
    kaikkien_osioiden_lkm_per_hoitokausi INTEGER;
    jokin_osio_aloitettu BOOLEAN;

BEGIN
    -- jos kaikki osiot on vahvistettu, on ko. hoitokauden osalta kustannussuunnitelma vahvistettu
    kaikkien_osioiden_lkm_per_hoitokausi := 6;
    SELECT COALESCE(count(*), 0) INTO vahvistettuja
                                 FROM suunnittelu_kustannussuunnitelman_tila
                                WHERE hoitovuosi = _hoitovuosi AND urakka = _urakka AND vahvistettu IS TRUE
                                  AND osio NOT IN ('tilaajan-rahavaraukset');
    SELECT COALESCE(count(*), 0) INTO vahvistamattomia
                                 FROM suunnittelu_kustannussuunnitelman_tila
                                WHERE hoitovuosi = _hoitovuosi AND urakka = _urakka AND vahvistettu IS FALSE
                                  AND osio NOT IN ('tilaajan-rahavaraukset', 'tavoite-ja-kattohinta');


    SELECT exists(SELECT id from suunnittelu_kustannussuunnitelman_tila WHERE urakka = _urakka and hoitovuosi = _hoitovuosi)
      INTO jokin_osio_aloitettu;

    -- Harja ei erikseen kirjaa jos tavoite- ja kattohinta on "aloitettu", koska minkä tahansa osion aloittaminen
    -- aloittaa myös tavoite- ja kattohinnan. Tässä lisätään se tarvittaessa aloitetuksi yksinkertaisella tarkistuksella.
    -- Poikkeus: jos kaikki osiot on vahvistettu, ei lisätä tavoite- ja kattohintaosiota keskeneräiseksi
    IF (jokin_osio_aloitettu IS TRUE AND vahvistettuja != kaikkien_osioiden_lkm_per_hoitokausi)
    THEN vahvistamattomia := vahvistamattomia + 1;
    END IF;


    aloittamattomia := kaikkien_osioiden_lkm_per_hoitokausi - vahvistamattomia - vahvistettuja;

    suunnitelman_tila := CASE
        WHEN vahvistettuja = kaikkien_osioiden_lkm_per_hoitokausi THEN 'vahvistettu'
        WHEN (vahvistamattomia > 0 OR vahvistettuja > 0) THEN 'aloitettu'
        ELSE 'aloittamatta'
END;

    RETURN
        json_build_object('aloittamattomia', aloittamattomia,
                          'vahvistamattomia', vahvistamattomia,
                          'vahvistettuja', vahvistettuja,
                          'suunnitelman_tila', suunnitelman_tila);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION monesko_hoitokausi(alkupvm DATE, loppupvm DATE, hoitokauden_alkuvuosi INTEGER)
    RETURNS INTEGER AS $$
    DECLARE jarjestysluku INTEGER;
    BEGIN
    SELECT ARRAY_POSITION(ARRAY(SELECT * FROM GENERATE_SERIES(EXTRACT(YEAR FROM alkupvm)::INTEGER,
                                                               (EXTRACT(YEAR FROM loppupvm)::INTEGER - 1))), hoitokauden_alkuvuosi) INTO jarjestysluku;
    RETURN jarjestysluku;
END;

$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION korjaa_kustannussuunnitelmien_puuttuvat_tilat(hoitokauden_alkuvuosi INTEGER)
    RETURNS INTEGER AS $$
DECLARE
    hankintakustannus_rivi RECORD;
    erillishankinnat_rivi RECORD;
    tavoitehintaiset_rahavaraukset_rivi RECORD;
    johto_ja_hallintokorvaus_rivi RECORD;
    hoidonjohtopalkkio_rivi RECORD;
    hoitourakka_idt INTEGER[];
    urakkaid INTEGER;
    hoitokauden_jarjestysluku INTEGER;
    integraatiokayttaja INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');


BEGIN
    -- valitaan annetun hoitokauden osalta käynnissä olleet urakat
    SELECT ARRAY (SELECT id FROM urakka u WHERE u.tyyppi = 'teiden-hoito' AND id = 36 AND -- fixme älä unohda 36 kovakoodausta!!
                                                make_date(hoitokauden_alkuvuosi, 10, 1) BETWEEN u.alkupvm and u.loppupvm)
      into hoitourakka_idt;

    RAISE NOTICE 'hoitourakka-idt: %', hoitourakka_idt;
    FOREACH urakkaid IN ARRAY hoitourakka_idt LOOP

        SELECT * FROM monesko_hoitokausi((SELECT alkupvm FROM urakka where id = urakkaid),
                                         (SELECT loppupvm FROM urakka where id = urakkaid),
                      hoitokauden_alkuvuosi) into hoitokauden_jarjestysluku;
        RAISE NOTICE 'Hoitokauden alkuvuosi: % ja järjestysluku: %', hoitokauden_alkuvuosi, hoitokauden_jarjestysluku;

        SELECT *
        FROM kiinteahintainen_tyo
       WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
         AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
              (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9)) LIMIT 1 INTO hankintakustannus_rivi;

        raise notice 'Jarnon hankintakustannuksen count %', (SELECT count(hankintakustannus_rivi));
        raise notice 'Jarnon hankintakustannuksen vahvistaja %', (hankintakustannus_rivi.vahvistaja);

        -- Jos on olemassa hankintakustannusten rivi, mutta ei vielä vastaavaa tietoa tilataulussa, lisätään tilatauluun rivi
        IF (SELECT count(hankintakustannus_rivi) = 1) AND
           ((SELECT count(*)
             FROM suunnittelu_kustannussuunnitelman_tila
            WHERE urakka = urakkaid AND osio = 'hankintakustannukset' AND hoitovuosi = hoitokauden_jarjestysluku) = 0) THEN
            RAISE NOTICE 'hankintakustannusten riviä ei ollut suunnittelu_kustannussuunnitelman_tila -taulussa, insertoidaan...';

            INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja, luotu, vahvistaja, vahvistus_pvm)

            VALUES (urakkaid, 'hankintakustannukset', hoitokauden_jarjestysluku,
                    hankintakustannus_rivi.vahvistaja IS NOT NULL, integraatiokayttaja, hankintakustannus_rivi.luotu,
            hankintakustannus_rivi.vahvistaja, hankintakustannus_rivi.indeksikorjaus_vahvistettu)
            ON CONFLICT (urakka, osio, hoitovuosi) DO UPDATE SET
                vahvistaja = hankintakustannus_rivi.vahvistaja,
                vahvistettu = hankintakustannus_rivi.vahvistaja IS NOT NULL,
                vahvistus_pvm = hankintakustannus_rivi.indeksikorjaus_vahvistettu,
                muokattu = NOW(),
                muokkaaja = integraatiokayttaja;
        END IF;

    SELECT osio, indeksikorjaus_vahvistettu, vahvistaja, luotu, luoja, muokkaaja, muokattu
      FROM kustannusarvioitu_tyo
     WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
       AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
            (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
       AND osio = 'erillishankinnat' INTO erillishankinnat_rivi;

    SELECT osio, indeksikorjaus_vahvistettu, vahvistaja, luotu, luoja, muokkaaja, muokattu
      FROM kustannusarvioitu_tyo
     WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
       AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
            (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
       AND osio = 'tavoitehintaiset-rahavaraukset' INTO tavoitehintaiset_rahavaraukset_rivi;

    SELECT osio, indeksikorjaus_vahvistettu, vahvistaja, luotu, luoja, muokkaaja, muokattu
      FROM kustannusarvioitu_tyo
     WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
       AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
            (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
       AND osio = 'johto-ja-hallintokorvaus' INTO johto_ja_hallintokorvaus_rivi;

    SELECT osio, indeksikorjaus_vahvistettu, vahvistaja, luotu, luoja, muokkaaja, muokattu
      FROM kustannusarvioitu_tyo
     WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
       AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
            (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
       AND osio = 'hoidonjohtopalkkio' INTO hoidonjohtopalkkio_rivi;

    RAISE NOTICE 'URAKKAID %, nimi %:, seuraavat kustannussuunnitelman tiedot löytyivät', urakkaid, (SELECT nimi FROM urakka where id = urakkaid);
    RAISE NOTICE 'hankintakustannus_rivi %', hankintakustannus_rivi;
    RAISE NOTICE 'erillishankinnat_rivi %', erillishankinnat_rivi;
    RAISE NOTICE 'tavoitehintaiset_rahavaraukset_rivi %', tavoitehintaiset_rahavaraukset_rivi;
    RAISE NOTICE 'johto_ja_hallintokorvaus_rivi %', johto_ja_hallintokorvaus_rivi;
    RAISE NOTICE 'hoidonjohtopalkkio_rivi %', hoidonjohtopalkkio_rivi;


END LOOP;




    RETURN 123456;
END;

$$ LANGUAGE plpgsql;

