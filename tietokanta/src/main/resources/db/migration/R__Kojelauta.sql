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
    paivitetyt_hankintakustannukset INTEGER;
    hankintakustannus_rivi RECORD;
    hoitourakka_idt INTEGER[];
    urakkaid INTEGER;
    vahvistamatta BOOLEAN;
    vahvistettu BOOLEAN;


BEGIN
    -- valitaan annetun hoitokauden osalta käynnissä olleet urakat
    SELECT ARRAY (SELECT id FROM urakka u WHERE u.tyyppi = 'teiden-hoito' AND
                                                make_date(hoitokauden_alkuvuosi, 10, 1) BETWEEN u.alkupvm and u.loppupvm)
      into hoitourakka_idt;

    RAISE NOTICE 'hoitourakka-idt: %', hoitourakka_idt;
    FOREACH urakkaid IN ARRAY hoitourakka_idt LOOP
            SELECT EXISTS (SELECT * from kiinteahintainen_tyo WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
                -- osuttava hoitokaudelle
                and ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
                     (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
                    and indeksikorjaus_vahvistettu IS NULL) INTO vahvistamatta;

            SELECT EXISTS (SELECT * from kiinteahintainen_tyo WHERE toimenpideinstanssi in (select id from toimenpideinstanssi WHERE urakka = urakkaid)
                -- osuttava hoitokaudelle
                AND ((vuosi = hoitokauden_alkuvuosi AND kuukausi BETWEEN 10 AND 12) OR
                     (vuosi = hoitokauden_alkuvuosi + 1 AND kuukausi BETWEEN 1 AND 9))
                    and indeksikorjaus_vahvistettu IS NOT NULL) INTO vahvistettu;
            RAISE NOTICE 'URAKKAID % hankintakustannus vahvistamatta: % ja vahvistettu %', urakkaid, vahvistamatta, vahvistettu;


END LOOP;


    paivitetyt_hankintakustannukset := 555;

    RETURN paivitetyt_hankintakustannukset;
END;

$$ LANGUAGE plpgsql;

