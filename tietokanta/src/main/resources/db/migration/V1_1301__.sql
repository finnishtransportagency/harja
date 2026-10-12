-- V1_1298__ vaihtoi toteuma_materiaali-taulun partitioituun tauluun nimeämällä vanhan taulun toteuma_materiaali_vanha 
-- Materialisoitu näkymä pysyy kuitenkin riippuvaisena vanhasta taulusta (possun OID)
--
-- Rakennetaan näkymä uudella nimellä ensin, vaihdetaan se sitten paikalleen
-- 
-- Säilyttää vanhan näkymän indeksit, erikseen myönnetyt oikeudet, omistajan sekä kommentit
-- Kloonilla ajo 1min 45s ei tule write lukkoja toteumatauluihin 

DO $$
DECLARE
    vanhat_indeksit TEXT[];
    vanhat_oikeudet TEXT[];
    vanha_omistaja TEXT;
    vanha_kommentti TEXT;
    ddl TEXT;
BEGIN
    -- DROP poistaisi indeksit. Otetaan niiden luontilauseet talteen
    SELECT COALESCE(
               array_agg(indexdef ORDER BY indexname), ARRAY[]::TEXT[]
    )
     INTO vanhat_indeksit
     FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'raportti_toteutuneet_materiaalit';


    SELECT 
        pg_get_userbyid(c.relowner), 
        obj_description(c.oid, 'pg_class')
     INTO STRICT 
         vanha_omistaja, 
         vanha_kommentti
     FROM pg_class c
    WHERE c.oid = 'public.raportti_toteutuneet_materiaalit'::regclass;


    -- Otetaan erikseen myönnetyt oikeudet talteen (omistaja palautetaan erikseen)
    SELECT COALESCE(
        array_agg(
            format('GRANT %s ON TABLE public.raportti_toteutuneet_materiaalit TO %s%s',
                   a.privilege_type,
                   CASE WHEN a.grantee = 0 
                       THEN 'PUBLIC' ELSE format('%I', g.rolname) 
                   END,
                   CASE WHEN a.is_grantable 
                       THEN ' WITH GRANT OPTION' ELSE '' 
                   END
            )
        ), ARRAY[]::TEXT[])
    
    -- Haetaan materialisoidun näkymän nykyiset käyttöoikeudet talteen
    INTO vanhat_oikeudet FROM pg_class c
        -- Puretaan käyttöoikeudet (ACL) yksittäisiksi riveiksi
        CROSS JOIN LATERAL aclexplode(
            CASE WHEN cardinality(c.relacl) > 0 THEN c.relacl ELSE NULL::aclitem[] END
        ) a
        -- käyttöoikeuden saaneen roolin nimi
        LEFT JOIN pg_roles g ON g.oid = a.grantee
    WHERE c.oid = 'public.raportti_toteutuneet_materiaalit'::regclass
      AND a.grantee <> c.relowner;

    -- Rakennetaan uusi versio ensin, jotta vanha näkymä on käytössä mahdollisimman pitkälle
    -- WITH DATA laskee rivit saman tien
    EXECUTE $mv$
        CREATE MATERIALIZED VIEW public.raportti_toteutuneet_materiaalit_uusi AS
        SELECT
            SUM(tm.maara)                   AS kokonaismaara,
            t.urakka                        AS "urakka-id",
            mk.id                           AS "materiaali-id",
            date_trunc('day', t.alkanut)    AS paiva
        FROM public.toteuma_materiaali tm
        JOIN public.toteuma t
          ON t.id = tm.toteuma
         AND t.poistettu IS NOT TRUE
        LEFT JOIN public.materiaalikoodi mk
          ON mk.id = tm.materiaalikoodi
        WHERE tm.poistettu = FALSE
        GROUP BY 
            t.urakka, 
            mk.id, 
            date_trunc('day', t.alkanut)
        WITH DATA
    $mv$;

    -- Ei CASCADEa: jos muut riippuu vanhasta, migraatio pysähtyy turvallisesti ja kaikki perutaan
    DROP MATERIALIZED VIEW public.raportti_toteutuneet_materiaalit RESTRICT;
         
    ALTER MATERIALIZED VIEW public.raportti_toteutuneet_materiaalit_uusi
        RENAME TO raportti_toteutuneet_materiaalit;

    -- Palauta indeksit ja oikeudet samoilla nimillä
    FOREACH ddl IN ARRAY vanhat_indeksit LOOP
        EXECUTE ddl;
    END LOOP;

    FOREACH ddl IN ARRAY vanhat_oikeudet LOOP
        EXECUTE ddl;
    END LOOP;

    IF vanha_kommentti IS NOT NULL THEN
        EXECUTE format(
            'COMMENT ON MATERIALIZED VIEW public.raportti_toteutuneet_materiaalit IS %L',
            vanha_kommentti
       );
    END IF;

    EXECUTE format(
        'ALTER MATERIALIZED VIEW public.raportti_toteutuneet_materiaalit OWNER TO %I', 
        vanha_omistaja
    );
END;
$$;
