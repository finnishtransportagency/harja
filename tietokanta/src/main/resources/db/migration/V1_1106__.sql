-- Muodostetaan se uusiksi
CREATE OR REPLACE FUNCTION paivita_yllapitokohteiden_korjausluokat(alkupaiva DATE, loppupaiva DATE)
    RETURNS VOID AS
$$
DECLARE
    yllapitokohde RECORD;
    tienumerot    INTEGER[];
    pk1geom       geometry;
    pk2geom       geometry;
    pk3geom       geometry;

BEGIN

    RAISE NOTICE 'Haetaan yllapitokohteiden tienumerot';

    tienumerot := (WITH tiet AS (SELECT y.tr_numero AS tie
                                   FROM yllapitokohde y
                                            JOIN yllapitokohteen_aikataulu ya ON y.id = ya.yllapitokohde
                                  WHERE ya.kohde_alku BETWEEN alkupaiva AND loppupaiva
                                  UNION
                                 SELECT yo.tr_numero AS tie
                                   FROM yllapitokohde y
                                            JOIN yllapitokohteen_aikataulu ya ON y.id = ya.yllapitokohde
                                            JOIN yllapitokohdeosa yo ON yo.yllapitokohde = y.id
                                  WHERE ya.kohde_alku BETWEEN alkupaiva AND loppupaiva)
                 SELECT ARRAY_AGG(DISTINCT tie)
                   FROM tiet);


    RAISE NOTICE 'tienumerot: %', tienumerot;

    RAISE NOTICE 'Haetaan pk geometryt:';
    -- Muuttuneet osat: Lisättiin st_buffer geometrialle, jotta siitä saadaan "makkara" eikä viiva
    SELECT st_buffer(st_union(p.geometria), 50)
      INTO pk1geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK1'
       AND p.tie = ANY (tienumerot);
    SELECT st_buffer(st_union(p.geometria), 50)
      INTO pk2geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK2'
       AND p.tie = ANY (tienumerot);
    SELECT st_buffer(st_union(p.geometria), 50)
      INTO pk3geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK3'
       AND p.tie = ANY (tienumerot);

    RAISE NOTICE 'Geometriat haettu';

    FOR yllapitokohde IN (SELECT y.id
                            FROM yllapitokohde y
                                     JOIN yllapitokohteen_aikataulu ya ON y.id = ya.yllapitokohde
                           WHERE ya.kohde_alku BETWEEN alkupaiva AND loppupaiva
                           ORDER BY y.id ASC)
        LOOP
            RAISE NOTICE 'Yllapitokohde :: id: %', yllapitokohde.id;
            PERFORM laske_yllapitokohdeosien_pk_pituudet(yllapitokohde.id, pk1geom::geometry,
                                                         pk2geom::geometry, pk3geom::geometry);

        END LOOP;
END;
$$ LANGUAGE plpgsql;


-- laske_yllapitokohdeosien_pk_pituudet funktiossa muutettiin yllapitokohdeosien pituuksien laskenta niin
-- että pituus ottaa huomioon muutakin, kuin viimeisen osan.
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
        WHEN yko_pk1_pituus >= yko_pk2_pituus AND yko_pk1_pituus >= yko_pk3_pituus THEN ypkluokka := 'PK1';
        WHEN yko_pk2_pituus >= yko_pk1_pituus AND yko_pk2_pituus >= yko_pk3_pituus THEN ypkluokka := 'PK2';
        WHEN yko_pk3_pituus >= yko_pk1_pituus AND yko_pk3_pituus >= yko_pk2_pituus THEN ypkluokka := 'PK3';
        ELSE ypkluokka := null;
        END CASE;

    RAISE NOTICE 'Yhteenlasketut luokkapituudet osapk1: %, osapk2: %, osapk3: %',
        yko_pk1_pituus, yko_pk2_pituus, yko_pk3_pituus;
    RAISE NOTICE 'Saatiin yllapikohteelle pkluokka: %', ypkluokka;
    UPDATE yllapitokohde SET pkluokka = ypkluokka WHERE id = yllapitokohde_id;
END;
$$ LANGUAGE plpgsql;
