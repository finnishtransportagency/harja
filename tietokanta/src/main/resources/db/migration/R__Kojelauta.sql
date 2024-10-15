-- Kojelautaa, eli (hoito)urakoiden tietojen yleisnäkymää varten tarvittavia apufunktioita
CREATE OR REPLACE FUNCTION urakan_kustannussuunnitelman_tila(_urakka INTEGER, _hoitovuosi INTEGER)
    RETURNS jsonb AS $$

DECLARE
    aloittamattomia INTEGER;
    vahvistamattomia INTEGER;
    vahvistettuja INTEGER;
    suunnitelman_tila TEXT;
    kaikkien_osioiden_lkm_per_hoitokausi INTEGER;
    tavoite_ja_kattohinta_kesken BOOLEAN;

BEGIN
    -- jos kaikki ao. osioit on vahvistettu, on ko. hoitokauden osalta kustannussuunnitelma vahvistettu
    kaikkien_osioiden_lkm_per_hoitokausi := 6;
    SELECT COALESCE(count(*), 0) INTO vahvistettuja
                                 FROM suunnittelu_kustannussuunnitelman_tila
                                WHERE hoitovuosi = _hoitovuosi AND urakka = _urakka AND vahvistettu IS TRUE
                                  AND osio NOT IN ('tilaajan-rahavaraukset', 'tavoite-ja-kattohinta');
    SELECT COALESCE(count(*), 0) INTO vahvistamattomia
                                 FROM suunnittelu_kustannussuunnitelman_tila
                                WHERE hoitovuosi = _hoitovuosi AND urakka = _urakka AND vahvistettu IS FALSE
                                  AND osio NOT IN ('tilaajan-rahavaraukset');

    -- Jos mikä tahansa osio on edes aloitettu, tavoite- ja kattohintaosio on täten aina aloitettu
    SELECT exists(SELECT id from suunnittelu_kustannussuunnitelman_tila WHERE urakka = _urakka and hoitovuosi = _hoitovuosi)
      INTO tavoite_ja_kattohinta_kesken;

    -- Harja ei erikseen kirjaa jos tavoite- ja kattohinta on "aloitettu", koska minkä tahansa osion aloittaminen
    -- aloittaa myös tavoite- ja kattohinnan. Tässä lisätään se tarvittaessa aloitetuksi yksinkertaisella tarkistuksella.
    -- Poikkeus: jos kaikki osiot on vahvistettu, ei lisätä tavoite- ja kattohintaosiota keskeneräiseksi
    IF (tavoite_ja_kattohinta_kesken IS TRUE AND vahvistettuja != kaikkien_osioiden_lkm_per_hoitokausi)
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
