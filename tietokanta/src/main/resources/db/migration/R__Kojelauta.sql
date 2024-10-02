-- Kojelautaa, eli (hoito)urakoiden tietojen yleisnäkymää varten tarvittavia apufunktioita
CREATE OR REPLACE FUNCTION urakan_kustannussuunnitelman_tila(_urakka INTEGER, _hoitovuosi INTEGER)
    RETURNS jsonb AS $$

DECLARE
    aloittamattomia INTEGER;
    vahvistamattomia INTEGER;
    vahvistettuja INTEGER;
    suunnitelman_tila TEXT;
    kaikkien_osioiden_lkm_per_hoitokausi INTEGER;

BEGIN
    -- jos kaikki ao. osioit on vahvistettu, on ko. hoitokauden osalta kustannussuunnitelma vahvistettu
    kaikkien_osioiden_lkm_per_hoitokausi := 5;
    SELECT count(*) as lkm FROM suunnittelu_kustannussuunnitelman_tila
                                 WHERE hoitovuosi = _hoitovuosi AND urakka = _urakka AND vahvistettu IS TRUE
                                 INTO vahvistettuja;
    SELECT count(*) as lkm FROM suunnittelu_kustannussuunnitelman_tila
     WHERE hoitovuosi = _hoitovuosi AND urakka = _urakka AND vahvistettu IS FALSE
      INTO vahvistamattomia;






    RAISE NOTICE 'vahvistettuja: %', vahvistettuja;
    RAISE NOTICE 'vahvistamattomia: %', vahvistamattomia;
    aloittamattomia := kaikkien_osioiden_lkm_per_hoitokausi - vahvistamattomia - vahvistettuja;
    RAISE NOTICE 'aloittamattomia: %', aloittamattomia;


    suunnitelman_tila := CASE
        WHEN vahvistettuja = kaikkien_osioiden_lkm_per_hoitokausi THEN 'vahvistettu'
        WHEN vahvistamattomia > 0 THEN 'aloitettu'
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
    SELECT array_position(array(SELECT * FROM GENERATE_SERIES(EXTRACT(YEAR FROM alkupvm)::INTEGER,
                                                               (EXTRACT(YEAR FROM loppupvm)::INTEGER - 1))), hoitokauden_alkuvuosi) into jarjestysluku;
    RETURN jarjestysluku;
END;

$$ LANGUAGE plpgsql;
