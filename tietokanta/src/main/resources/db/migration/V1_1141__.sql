DROP FUNCTION IF EXISTS paivita_yllapitokohteiden_korjausluokat(alkupaiva DATE, loppupaiva DATE);

-- Käytetään vain massa-ajoihin, eli tiedonkorjauksiin tuotannossa, vaikka näin korjataan 2024 kohteiden pk-luokat:
-- SELECT * FROM paivita_yllapitokohteiden_korjausluokat(2024);
CREATE OR REPLACE FUNCTION paivita_yllapitokohteiden_korjausluokat(_vuosi INTEGER)
    RETURNS VOID AS
$$
DECLARE
    yllapitokohde RECORD;
    tienumerot    INTEGER[];
    pk1geom       geometry;
    pk2geom       geometry;
    pk3geom       geometry;

BEGIN

    RAISE NOTICE 'Haetaan yllapitokohteiden tienumerot vuodelle %', _vuosi;

    tienumerot := (WITH tiet AS (SELECT y.tr_numero AS tie
                                   FROM yllapitokohde y
                                  WHERE y.vuodet @> ARRAY[_vuosi] ::INT[]
                                  UNION
                                 SELECT yo.tr_numero AS tie
                                   FROM yllapitokohde y
                                            JOIN yllapitokohdeosa yo ON yo.yllapitokohde = y.id
                                  WHERE y.vuodet @> ARRAY[_vuosi] ::INT[])
                 SELECT ARRAY_AGG(DISTINCT tie)
      FROM tiet);


    RAISE NOTICE 'tienumerot: %', tienumerot;

    RAISE NOTICE 'Haetaan pk geometryt:';
    -- Muuttuneet osat: Lisättiin st_buffer geometrialle, jotta siitä saadaan "makkara" eikä viiva
    SELECT ST_BUFFER(ST_UNION(p.geometria), 10)
      INTO pk1geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK1'
       AND p.tie = ANY (tienumerot);
    SELECT ST_BUFFER(ST_UNION(p.geometria), 10)
      INTO pk2geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK2'
       AND p.tie = ANY (tienumerot);
    SELECT ST_BUFFER(ST_UNION(p.geometria), 10)
      INTO pk3geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK3'
       AND p.tie = ANY (tienumerot);

    RAISE NOTICE 'Geometriat haettu';

    FOR yllapitokohde IN (SELECT y.id
                            FROM yllapitokohde y
                           WHERE y.vuodet @> ARRAY[_vuosi] ::INT[]
                           ORDER BY y.id ASC)
        LOOP
            RAISE NOTICE 'Yllapitokohde :: id: %', yllapitokohde.id;
            PERFORM laske_yllapitokohdeosien_pk_pituudet(yllapitokohde.id, pk1geom::geometry,
                                                         pk2geom::geometry, pk3geom::geometry);

        END LOOP;
END;
$$ LANGUAGE plpgsql;


-- Korjataan tätä yksittäisen ypk:n pk-luokkien laskentaa siten että käytetään samaa st_bufferia kuin massa-ajossa
-- se ei toimi täydellisesti; ilman bufferia jäisi paljon kokonaan nollaksi ja luokittelematta, tämä st_buffer imaisee joskus
-- tien päässä vähän seuraavaa palasta mukaan, mutta käytännössä aina niin vähän että itse luokittelu menee oikein
CREATE OR REPLACE FUNCTION paivita_yllapitokohteen_korjausluokat(yllapitokohde_id INTEGER)
    RETURNS VOID AS
$$
DECLARE
    yllapitokohde RECORD;
    tienumerot    INTEGER[];
    pk1geom       geometry;
    pk2geom       geometry;
    pk3geom       geometry;

BEGIN

    RAISE NOTICE 'Haetaan yllapitokohteen:  % tienumerot.', yllapitokohde_id;

    tienumerot := (WITH tiet AS (SELECT y.tr_numero AS tie
                                   FROM yllapitokohde y
                                  WHERE y.id = yllapitokohde_id
                                  UNION
                                 SELECT yo.tr_numero AS tie
                                   FROM yllapitokohde y
                                            JOIN yllapitokohdeosa yo ON yo.yllapitokohde = y.id AND y.id = yllapitokohde_id)
                 SELECT ARRAY_AGG(DISTINCT tie)
                   FROM tiet);

    RAISE NOTICE 'tienumerot: %', tienumerot;

    RAISE NOTICE 'Haetaan pk geometryt:';
    SELECT ST_BUFFER(ST_UNION(p.geometria), 10)
      INTO pk1geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK1'
       AND p.tie = ANY (tienumerot);
    SELECT ST_BUFFER(ST_UNION(p.geometria), 10)
      INTO pk2geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK2'
       AND p.tie = ANY (tienumerot);
    SELECT ST_BUFFER(ST_UNION(p.geometria), 10)
      INTO pk3geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK3'
       AND p.tie = ANY (tienumerot);

    RAISE NOTICE 'Geometriat haettu';

    PERFORM laske_yllapitokohdeosien_pk_pituudet(yllapitokohde_id, pk1geom::geometry,
                                                 pk2geom::geometry, pk3geom::geometry);

END;
$$ LANGUAGE plpgsql;
