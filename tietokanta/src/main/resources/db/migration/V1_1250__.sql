-- Yhdistetty funktio, joka päivittää sekä PK-pituudet että ylläpitoluokat samassa transaktiossa
-- Tämä varmistaa että ylläpitoluokat päivitetään vasta kun PK-pituudet on ensin päivitetty
CREATE OR REPLACE FUNCTION paivita_yllapitokohteen_korjausluokat_ja_yllapitoluokat(
    yllapitokohde_id INTEGER)
    RETURNS VOID AS
$$

DECLARE
    yllapitokohde RECORD;
    tienumerot    INTEGER[];
    pk1geom       GEOMETRY;
    pk2geom       GEOMETRY;
    pk3geom       GEOMETRY;
    radius        INTEGER := 10;
    yosa           RECORD;
    yko_pk1_pituus NUMERIC;
    yko_pk2_pituus NUMERIC;
    yko_pk3_pituus NUMERIC;
    ypkluokka      TEXT;

BEGIN
    -- Tarkistetaan että kaikilla yllapitokohdeosilla on geometria
    IF EXISTS (SELECT 1 FROM yllapitokohdeosa yko WHERE yko.yllapitokohde = paivita_yllapitokohteen_korjausluokat_ja_yllapitoluokat.yllapitokohde_id AND yko.sijainti IS NULL AND yko.poistettu = FALSE) THEN
        RAISE WARNING 'Ylläpitokohteella % on osia joilla sijainti on NULL. Näiden osien PK-pituuksia ei voida laskea. Jatketaan muiden osien käsittelyä.', yllapitokohde_id;
    END IF;

    -- 1. Päivitetään ensin PK-pituudet
    tienumerot := (WITH tiet AS (SELECT y.tr_numero AS tie
                                   FROM yllapitokohde y
                                  WHERE y.id = yllapitokohde_id
                                  UNION
                                 SELECT yo.tr_numero AS tie
                                   FROM yllapitokohde y
                                            JOIN yllapitokohdeosa yo ON yo.yllapitokohde = y.id AND y.id = yllapitokohde_id)
                 SELECT ARRAY_AGG(DISTINCT tie)
                   FROM tiet);

    SELECT ST_BUFFER(ST_UNION(p.geometria), radius)
    INTO pk1geom
    FROM paallysteen_korjausluokka p
    WHERE p.korjausluokka = 'PK1'
      AND p.tie = ANY (tienumerot);
    SELECT ST_BUFFER(ST_UNION(p.geometria), radius)
    INTO pk2geom
    FROM paallysteen_korjausluokka p
    WHERE p.korjausluokka = 'PK2'
      AND p.tie = ANY (tienumerot);
    SELECT ST_BUFFER(ST_UNION(p.geometria), radius)
    INTO pk3geom
    FROM paallysteen_korjausluokka p
    WHERE p.korjausluokka = 'PK3'
      AND p.tie = ANY (tienumerot);

    RAISE NOTICE 'Tienumerot: %', tienumerot;

    RAISE NOTICE 'Haetaan yllapitokohdeosat:';
    yko_pk1_pituus := 0.0;
    yko_pk2_pituus := 0.0;
    yko_pk3_pituus := 0.0;

    FOR yosa IN (SELECT y.id,
                        y.sijainti,
                        COALESCE(st_length(y.sijainti), 0)                         AS pituus,
                        COALESCE(st_length(st_intersection(y.sijainti, pk1geom)), 0) AS pk1_pituus,
                        COALESCE(st_length(st_intersection(y.sijainti, pk2geom)), 0) AS pk2_pituus,
                        COALESCE(st_length(st_intersection(y.sijainti, pk3geom)), 0) AS pk3_pituus
                 FROM yllapitokohdeosa y
                 WHERE y.yllapitokohde = paivita_yllapitokohteen_korjausluokat_ja_yllapitoluokat.yllapitokohde_id)
            LOOP
                RAISE NOTICE 'Ylläpitokohde: % :: osa :: id: % sijainti NULL: % pituus: % pk1_pituus: % pk2_pituus: % pk3_pituus %',
                    yllapitokohde_id, yosa.id, (yosa.sijainti IS NULL), yosa.pituus, yosa.pk1_pituus, yosa.pk2_pituus, yosa.pk3_pituus;

                UPDATE yllapitokohdeosa
                SET pk1_pituus = yosa.pk1_pituus,
                    pk2_pituus = yosa.pk2_pituus,
                    pk3_pituus = yosa.pk3_pituus
                WHERE id = yosa.id;

            END LOOP;

    -- 2. Päivitetään ylläpitoluokat PK-pituuksien perusteella
    -- Ylläpitoluokka määräytyy sen mukaan, mikä PK-pituus on suurin kyseisellä ylläpitokohdeosalla
    -- PK1 -> ylläpitoluokka 8, PK2 -> 9, PK3 -> 10

    -- Päivitetään ylläpitoluokka = 8 niille osille, joilla pk1_pituus on suurin JA ylläpitoluokka on NULL
    UPDATE yllapitokohdeosa yko
    SET yllapitoluokka = 8
    WHERE yko.yllapitokohde = paivita_yllapitokohteen_korjausluokat_ja_yllapitoluokat.yllapitokohde_id
      AND yko.yllapitoluokka IS NULL
      AND COALESCE(yko.pk1_pituus, 0) >= COALESCE(yko.pk2_pituus, 0)
      AND COALESCE(yko.pk1_pituus, 0) >= COALESCE(yko.pk3_pituus, 0)
      AND NOT (COALESCE(yko.pk1_pituus, 0) = COALESCE(yko.pk2_pituus, 0)
        AND COALESCE(yko.pk2_pituus, 0) = COALESCE(yko.pk3_pituus, 0));

    -- Päivitetään ylläpitoluokka = 9 niille osille, joilla pk2_pituus on suurin JA ylläpitoluokka on NULL
    UPDATE yllapitokohdeosa yko
    SET yllapitoluokka = 9
    WHERE yko.yllapitokohde = paivita_yllapitokohteen_korjausluokat_ja_yllapitoluokat.yllapitokohde_id
      AND yko.yllapitoluokka IS NULL
      AND COALESCE(yko.pk2_pituus, 0) >= COALESCE(yko.pk1_pituus, 0)
      AND COALESCE(yko.pk2_pituus, 0) >= COALESCE(yko.pk3_pituus, 0)
      AND NOT (COALESCE(yko.pk1_pituus, 0) = COALESCE(yko.pk2_pituus, 0)
        AND COALESCE(yko.pk2_pituus, 0) = COALESCE(yko.pk3_pituus, 0));

    -- Päivitetään ylläpitoluokka = 10 niille osille, joilla pk3_pituus on suurin JA ylläpitoluokka on NULL
    UPDATE yllapitokohdeosa yko
    SET yllapitoluokka = 10
    WHERE yko.yllapitokohde = paivita_yllapitokohteen_korjausluokat_ja_yllapitoluokat.yllapitokohde_id
      AND yko.yllapitoluokka IS NULL
      AND COALESCE(yko.pk3_pituus, 0) >= COALESCE(yko.pk1_pituus, 0)
      AND COALESCE(yko.pk3_pituus, 0) >= COALESCE(yko.pk2_pituus, 0)
      AND NOT (COALESCE(yko.pk1_pituus, 0) = COALESCE(yko.pk2_pituus, 0)
        AND COALESCE(yko.pk2_pituus, 0) = COALESCE(yko.pk3_pituus, 0));


END;
$$ LANGUAGE plpgsql;
