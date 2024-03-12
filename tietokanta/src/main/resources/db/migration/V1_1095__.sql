-- yllapitokohde yotyo voi olla null. Pakotetaan null arvot falseksi ja estetään null.
UPDATE yllapitokohde SET yotyo = COALESCE(yotyo, false) WHERE yotyo IS NULL;
ALTER TABLE yllapitokohde ALTER COLUMN yotyo SET NOT NULL;

-- Tämä on esitelty jo aiemmin, mutta korjataan tässä pkluokka nulliksi, kun sitä ei tiedetä
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

    FOR yosa IN (SELECT y.id,
                        COALESCE(st_length(y.sijainti), 0)                         AS pituus,
                        COALESCE(st_length(st_difference(y.sijainti, pk1geom)), 0) AS eropituus_pk1,
                        COALESCE(st_length(st_difference(y.sijainti, pk2geom)), 0) AS eropituus_pk2,
                        COALESCE(st_length(st_difference(y.sijainti, pk3geom)), 0) AS eropituus_pk3
                   FROM yllapitokohdeosa y
                  WHERE y.yllapitokohde = yllapitokohde_id)
        LOOP
            RAISE NOTICE 'Ylläpitokode: % :: osa :: id: % pituus: % erop1: % erop2: % erop3 %',
                yllapitokohde_id, yosa.id, yosa.pituus, yosa.eropituus_pk1,
                yosa.eropituus_pk2, yosa.eropituus_pk3;
            yko_pk1_pituus := CASE
                                  WHEN yosa.eropituus_pk1 > 0 THEN ROUND((yosa.pituus - yosa.eropituus_pk1)::NUMERIC, 2)::NUMERIC
                                  ELSE NULL END;
            yko_pk2_pituus := CASE
                                  WHEN yosa.eropituus_pk2 > 0 THEN ROUND((yosa.pituus - yosa.eropituus_pk2)::NUMERIC, 2)::NUMERIC
                                  ELSE NULL END;
            yko_pk3_pituus := CASE
                                  WHEN yosa.eropituus_pk3 > 0 THEN ROUND((yosa.pituus - yosa.eropituus_pk3)::NUMERIC, 2)::NUMERIC
                                  ELSE NULL END;

            RAISE NOTICE 'Pituudet: % :: % ::  %',
                yko_pk1_pituus, yko_pk2_pituus, yko_pk3_pituus;

            UPDATE yllapitokohdeosa
               SET pk1_pituus = yko_pk1_pituus,
                   pk2_pituus = yko_pk2_pituus,
                   pk3_pituus = yko_pk3_pituus
             WHERE id = yosa.id;

            -- Lisään ylläpitokohteelle pkluokka sen perusteella, mitä pkluokkaa on eniten
            CASE
                WHEN yko_pk1_pituus > yko_pk2_pituus AND yko_pk1_pituus > yko_pk3_pituus THEN ypkluokka := 'PK1';
                WHEN yko_pk2_pituus > yko_pk1_pituus AND yko_pk2_pituus > yko_pk3_pituus THEN ypkluokka := 'PK2';
                WHEN yko_pk3_pituus > yko_pk1_pituus AND yko_pk3_pituus > yko_pk2_pituus THEN ypkluokka := 'PK3';
                ELSE ypkluokka := null;
                END CASE;

            -- Muuttunut koodi on tuo: ELSE ypkluokka := null;

            RAISE NOTICE 'Saatiin yllapikohteelle pkluokka: %', ypkluokka;

            UPDATE yllapitokohde SET pkluokka = ypkluokka WHERE id = yllapitokohde_id;

        END LOOP;
END;
$$ LANGUAGE plpgsql;
