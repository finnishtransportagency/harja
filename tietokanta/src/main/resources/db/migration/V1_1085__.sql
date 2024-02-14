-- Lisätään päällystyskohteen korjausluokalle geometrian tallennusmahdollisuus
ALTER TABLE paallysteen_korjausluokka
    ADD COLUMN geometria geometry;

-- Lisätään päällystyskohteen osalle jokaisen korjausluokan pituus
ALTER TABLE yllapitokohdeosa
    ADD COLUMN pk1_pituus numeric,
    ADD COLUMN pk2_pituus numeric,
    ADD COLUMN pk3_pituus numeric;

-- Lisätään pääkohteelle pk-luokka, joka määräytyy sen mukaan, mitä pk-luokkaa on pituudeltaa alikohteella eniten
ALTER TABLE yllapitokohde
    ADD COLUMN pkluokka text;

-- Tehdään funktio, joka laskee pituudet jokaiselle ylläpitokohdeosalle
-- Pituuden laskennan jälkeen määritetään yllapitokohteelle pkluokka sen mukaan, mitä pk-luokkaa on alikohteella eniten
CREATE OR REPLACE FUNCTION laske_yllapitokohdeosan_pk_pituudet(yllapitokohde_id Integer, pk1geom geometry,
                                                               pk2geom geometry, pk3geom geometry)
    RETURNS void AS $$
DECLARE
    yosa      RECORD;
    yko_pk1_pituus         numeric;
    yko_pk2_pituus         numeric;
    yko_pk3_pituus         numeric;
    ypkluokka               text;

BEGIN

    RAISE NOTICE 'Haetaan yllapitokohdeosat:';

    FOR yosa IN (SELECT y.id, COALESCE(st_length(y.sijainti),0) AS pituus,
                        COALESCE(st_length(st_difference(y.sijainti, pk1geom)),0) AS eropituus_pk1,
                        COALESCE(st_length(st_difference(y.sijainti, pk2geom)),0) AS eropituus_pk2,
                        COALESCE(st_length(st_difference(y.sijainti, pk3geom)),0) AS eropituus_pk3
                   FROM yllapitokohdeosa y
                  WHERE y.yllapitokohde = yllapitokohde_id)
        LOOP
            RAISE NOTICE 'Ylläpitokode: % :: osa :: id: % pituus: % erop1: % erop2: % erop3 %',
                yllapitokohde_id, yosa.id, yosa.pituus, yosa.eropituus_pk1,
                yosa.eropituus_pk2, yosa.eropituus_pk3;
            yko_pk1_pituus := CASE WHEN yosa.eropituus_pk1 > 0 THEN ROUND((yosa.pituus - yosa.eropituus_pk1)::numeric, 2)::numeric ELSE null END;
            yko_pk2_pituus := CASE WHEN yosa.eropituus_pk2 > 0 THEN ROUND((yosa.pituus - yosa.eropituus_pk2)::numeric, 2)::numeric ELSE null END;
            yko_pk3_pituus := CASE WHEN yosa.eropituus_pk3 > 0 THEN ROUND((yosa.pituus - yosa.eropituus_pk3)::numeric, 2)::numeric ELSE null END;

            RAISE NOTICE 'Pituudet: % :: % ::  %',
                yko_pk1_pituus, yko_pk2_pituus, yko_pk3_pituus;

            UPDATE yllapitokohdeosa SET pk1_pituus = yko_pk1_pituus, pk2_pituus = yko_pk2_pituus,
                                        pk3_pituus = yko_pk3_pituus WHERE id = yosa.id;

            -- Lisään ylläpitokohteelle pkluokka sen perusteella, mitä pkluokkaa on eniten
            CASE
                WHEN yko_pk1_pituus > yko_pk2_pituus AND yko_pk1_pituus > yko_pk3_pituus THEN ypkluokka := 'PK1';
                WHEN yko_pk2_pituus > yko_pk1_pituus AND yko_pk2_pituus > yko_pk3_pituus THEN ypkluokka := 'PK2';
                WHEN yko_pk3_pituus > yko_pk1_pituus AND yko_pk3_pituus > yko_pk2_pituus THEN ypkluokka := 'PK3';
                ELSE ypkluokka := 'Ei tiedossa';
                END CASE;

            RAISE NOTICE 'Saatiin yllapikohteelle pkluokka: %', ypkluokka;

            UPDATE yllapitokohde SET pkluokka = ypkluokka WHERE id = yllapitokohde_id;

        END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Lasketaan yllapitokohdeosan pkluokkapituudet valmiiksi ja päivitetään yllapitokohteelle pkluokaksi suurimman pituuden saanut pkluokka.
CREATE OR REPLACE FUNCTION paivita_yllapitokohdeosan_korjausluokat(alkupaiva DATE, loppupaiva DATE)
    RETURNS VOID AS
$$
DECLARE
    yllapitokohde   RECORD;
    tienumerot   INTEGER[];
    pk1geom         geometry;
    pk2geom         geometry;
    pk3geom         geometry;

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
    SELECT st_union(p.geometria)
      INTO pk1geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK1'
       AND p.tie = ANY (tienumerot);
    SELECT st_union(p.geometria)
      INTO pk2geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK2'
       AND p.tie = ANY (tienumerot);
    SELECT st_union(p.geometria)
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
            PERFORM laske_yllapitokohdeosan_pk_pituudet(yllapitokohde.id, pk1geom::geometry,
                                                        pk2geom::geometry, pk3geom::geometry);

        END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Päivitä yksittäisen ylläpitkohteen päällysteen korjausluokka
CREATE OR REPLACE FUNCTION paivita_yllapitokohdeosan_korjausluokat(yllapitkohde_id INTEGER)
    RETURNS VOID AS
$$
DECLARE
    yllapitokohde   RECORD;
    tienumerot   INTEGER[];
    pk1geom         geometry;
    pk2geom         geometry;
    pk3geom         geometry;

BEGIN

    RAISE NOTICE 'Haetaan yllapitokohteen:  % tienumerot.', yllapitkohde_id;

    tienumerot := (WITH tiet AS (SELECT y.tr_numero AS tie FROM yllapitokohde y WHERE y.id = yllapitkohde_id
                                  UNION
                                 SELECT yo.tr_numero AS tie
                                   FROM yllapitokohde y
                                            JOIN yllapitokohdeosa yo ON yo.yllapitokohde = y.id AND y.id = yllapitkohde_id)
                 SELECT ARRAY_AGG(DISTINCT tie)
                   FROM tiet);

    RAISE NOTICE 'tienumerot: %', tienumerot;

    RAISE NOTICE 'Haetaan pk geometryt:';
    SELECT st_union(p.geometria)
      INTO pk1geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK1'
       AND p.tie = ANY (tienumerot);
    SELECT st_union(p.geometria)
      INTO pk2geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK2'
       AND p.tie = ANY (tienumerot);
    SELECT st_union(p.geometria)
      INTO pk3geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK3'
       AND p.tie = ANY (tienumerot);

    RAISE NOTICE 'Geometriat haettu';

    PERFORM laske_yllapitokohdeosan_pk_pituudet(yllapitkohde_id, pk1geom::geometry,
                                                pk2geom::geometry, pk3geom::geometry);

END;
$$ LANGUAGE plpgsql;

-- Voit päivittää päällystyskohteen korjausluokat tällä komennolla koko ajalle tai vaikka jollekin päivälle
-- select paivita_yllapitokohdeosan_korjausluokat('2000-06-01'::DATE, '2024-06-30'::DATE);
