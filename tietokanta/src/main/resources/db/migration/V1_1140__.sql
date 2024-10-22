-- Korjataan bugi, jos kaikki pk1 pk2 ja pk3 pituus ovat 0, pitää palautua NULL, eikä PK1

CREATE OR REPLACE FUNCTION laske_yllapitokohdeosien_pk_pituudet(yllapitokohde_id INTEGER, pk1geom geometry,
                                                                pk2geom geometry, pk3geom geometry)
    RETURNS VOID AS
$$
DECLARE
    yosa           RECORD;
    yko_pk1_pituus NUMERIC;
    yko_pk2_pituus NUMERIC;
    yko_pk3_pituus NUMERIC;
    ypkluokka      TEXT;

BEGIN

    RAISE NOTICE 'Haetaan yllapitokohdeosat:';
    yko_pk1_pituus := 0.0;
    yko_pk2_pituus := 0.0;
    yko_pk3_pituus := 0.0;

    FOR yosa IN (SELECT y.id,
                        COALESCE(st_length(y.sijainti), 0)                         AS pituus,
                        COALESCE(st_length(st_intersection(y.sijainti, pk1geom)), 0) AS pk1_pituus,
                        COALESCE(st_length(st_intersection(y.sijainti, pk2geom)), 0) AS pk2_pituus,
                        COALESCE(st_length(st_intersection(y.sijainti, pk3geom)), 0) AS pk3_pituus
                   FROM yllapitokohdeosa y
                  WHERE y.yllapitokohde = yllapitokohde_id)
        LOOP
            RAISE NOTICE 'Ylläpitokode: % :: osa :: id: % pituus: % pk1_pituus: % pk2_pituus: % pk3_pituus %',
                yllapitokohde_id, yosa.id, yosa.pituus, yosa.pk1_pituus, yosa.pk2_pituus, yosa.pk3_pituus;

            UPDATE yllapitokohdeosa
               SET pk1_pituus = yosa.pk1_pituus,
                   pk2_pituus = yosa.pk2_pituus,
                   pk3_pituus = yosa.pk3_pituus
             WHERE id = yosa.id;

            -- Lasketaan yhteen
            yko_pk1_pituus := yko_pk1_pituus + yosa.pk1_pituus;
            yko_pk2_pituus := yko_pk2_pituus + yosa.pk2_pituus;
            yko_pk3_pituus := yko_pk3_pituus + yosa.pk3_pituus;

        END LOOP;

    -- Lisään ylläpitokohteelle pkluokka sen perusteella, mitä pkluokkaa on eniten
    CASE
        -- Jos kaikkien pituus on sama (joskus erityisesti 0.0), ei aseteta PK1:tä ettei tiedot vääristy
        WHEN yko_pk1_pituus = yko_pk2_pituus AND yko_pk2_pituus = yko_pk3_pituus THEN ypkluokka := NULL;
        WHEN yko_pk1_pituus >= yko_pk2_pituus AND yko_pk1_pituus >= yko_pk3_pituus THEN ypkluokka := 'PK1';
        WHEN yko_pk2_pituus >= yko_pk1_pituus AND yko_pk2_pituus >= yko_pk3_pituus THEN ypkluokka := 'PK2';
        WHEN yko_pk3_pituus >= yko_pk1_pituus AND yko_pk3_pituus >= yko_pk2_pituus THEN ypkluokka := 'PK3';
        ELSE ypkluokka := NULL;
        END CASE;

    RAISE NOTICE 'Yhteenlasketut luokkapituudet osapk1: %, osapk2: %, osapk3: %',
        yko_pk1_pituus, yko_pk2_pituus, yko_pk3_pituus;
    RAISE NOTICE 'Saatiin yllapikohteelle pkluokka: %', ypkluokka;
    UPDATE yllapitokohde SET pkluokka = ypkluokka WHERE id = yllapitokohde_id;
END;
$$ LANGUAGE plpgsql;
