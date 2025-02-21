
ALTER FUNCTION tierekisteriosoitteelle_viiva(tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER)
    RENAME TO tieosoitteelle_geometria;

-- nimimuutoksen vuoksi joudutaan nämä pari muutakin funktiota tekemään uudestaan
CREATE OR REPLACE FUNCTION paivita_paikkauskohteen_korjausluokka(paikkauskohde_id INTEGER)
    RETURNS TEXT AS
$$
DECLARE
    kohde      RECORD;
    pk1geom    GEOMETRY;
    pk2geom    GEOMETRY;
    pk3geom    GEOMETRY;
    radius     INTEGER := 10;
    pk1_pituus NUMERIC;
    pk2_pituus NUMERIC;
    pk3_pituus NUMERIC;
    pkluokka_t TEXT;

BEGIN
    SELECT (tierekisteriosoite_laajennettu).tie  AS tie,
           (tierekisteriosoite_laajennettu).aosa AS aosa,
           (tierekisteriosoite_laajennettu).losa AS losa,
           CASE
               WHEN (tierekisteriosoite_laajennettu).tie IS NOT NULL
                   AND (tierekisteriosoite_laajennettu).aosa IS NOT NULL
                   AND (tierekisteriosoite_laajennettu).losa IS NOT NULL
                   AND (tierekisteriosoite_laajennettu).aet IS NOT NULL
                   AND (tierekisteriosoite_laajennettu).let IS NOT NULL
                   THEN
                   (SELECT tieosoitteelle_geometria AS geometria
                      FROM tieosoitteelle_geometria(
                          CAST((tierekisteriosoite_laajennettu).tie AS INTEGER),
                          CAST((tierekisteriosoite_laajennettu).aosa AS INTEGER),
                          CAST((tierekisteriosoite_laajennettu).aet AS INTEGER),
                          CAST((tierekisteriosoite_laajennettu).losa AS INTEGER),
                          CAST((tierekisteriosoite_laajennettu).let AS INTEGER)))
               ELSE
                   NULL
               END                               AS geometria
      FROM paikkauskohde pk
     WHERE pk.id = paikkauskohde_id
      INTO kohde;

    -- Tieosoite voi olla tyhjä
    IF kohde.tie IS NULL OR kohde.aosa IS NULL OR kohde.losa IS NULL OR kohde.geometria IS NULL THEN
        RAISE NOTICE 'Tieosoite puuttuu paikkauskohteelta % - Ei lasketa PK luokkaa', paikkauskohde_id;
        RAISE NOTICE 'kohde.tie %, kohde.aosa: %, kohde.losa: %, kohde.geometria: %', kohde.tie, kohde.aosa, kohde.losa, kohde.geometria;
        RETURN 'tieosoite puutteellinen';
    END IF;

    SELECT ST_BUFFER(ST_UNION(p.geometria), radius, 'endcap=flat')
      INTO pk1geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK1'
       AND p.tie = kohde.tie
       AND p.aosa = kohde.aosa
       AND p.losa = kohde.losa;

    SELECT ST_BUFFER(ST_UNION(p.geometria), radius, 'endcap=flat')
      INTO pk2geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK2'
       AND p.tie = kohde.tie
       AND p.aosa = kohde.aosa
       AND p.losa = kohde.losa;

    SELECT ST_BUFFER(ST_UNION(p.geometria), radius, 'endcap=flat')
      INTO pk3geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK3'
       AND p.tie = kohde.tie
       AND p.aosa = kohde.aosa
       AND p.losa = kohde.losa;


    pk1_pituus := (SELECT COALESCE(st_length(st_intersection(kohde.geometria, pk1geom)), 0));
    pk2_pituus := (SELECT COALESCE(st_length(st_intersection(kohde.geometria, pk2geom)), 0));
    pk3_pituus := (SELECT COALESCE(st_length(st_intersection(kohde.geometria, pk3geom)), 0));

    CASE
        WHEN pk1_pituus > pk2_pituus AND pk1_pituus > pk3_pituus THEN pkluokka_t := 'PK1';
        WHEN pk2_pituus > pk1_pituus AND pk2_pituus > pk3_pituus THEN pkluokka_t := 'PK2';
        WHEN pk3_pituus > pk1_pituus AND pk3_pituus > pk2_pituus THEN pkluokka_t := 'PK3';
        ELSE pkluokka_t := 'Ei tiedossa';
        END CASE;

    UPDATE paikkauskohde SET pkluokka = pkluokka_t::korjausluokkatyyppi WHERE id = paikkauskohde_id;
    RETURN pkluokka_t;
END;
$$ LANGUAGE plpgsql;

-- Nimimuutoksen vuoksi tehtävä uudestaan
CREATE OR REPLACE FUNCTION tr_valin_suolatoteumat(urakkaid INTEGER, tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER, threshold INTEGER, alkuaika TIMESTAMP, loppuaika TIMESTAMP) RETURNS TABLE (
                                                                                                                                                                                                                          rivinumero BIGINT,
                                                                                                                                                                                                                          materiaali_id INTEGER,
                                                                                                                                                                                                                          materiaali_nimi VARCHAR,
                                                                                                                                                                                                                          pvm TIMESTAMP,
                                                                                                                                                                                                                          maara NUMERIC,
                                                                                                                                                                                                                          lukumaara INTEGER,
                                                                                                                                                                                                                          toteumaidt INTEGER[],
                                                                                                                                                                                                                          koneellinen BOOLEAN) AS $$
DECLARE
    g geometry;
BEGIN
    SELECT tieosoitteelle_geometria(tie_, aosa_, aet_, losa_, let_) INTO g;

    RETURN QUERY SELECT row_number() OVER ()            AS rivinumero,
                        mk.id                           AS materiaali_id,
                        mk.nimi                         AS materiaali_nimi,
                        date_trunc('day', tot.alkanut)  AS pvm,
                        SUM(rp.maara)                   AS maara,
                        count(rp.maara)::integer        AS lukumaara,
                        array_agg(tot.id)               AS toteumaidt,
                        TRUE                            AS koneellinen
                   FROM suolatoteuma_reittipiste AS rp
                            JOIN toteuma tot ON (tot.id = rp.toteuma AND tot.poistettu IS NOT TRUE)
                            JOIN materiaalikoodi mk ON rp.materiaalikoodi = mk.id
                            LEFT JOIN kayttaja k ON tot.luoja = k.id
                  WHERE tot.urakka = urakkaid
                    AND ST_DWithin(g, rp.sijainti::geometry, threshold)
                    AND aika BETWEEN alkuaika AND loppuaika
                  GROUP BY mk.id, tot.alkanut;
END;
$$ LANGUAGE plpgsql;

-- ei muutoksia, vain dependency-funtion nimi vaihtuu
CREATE OR REPLACE FUNCTION paivita_reikapaikkauksen_korjausluokka(reikapaikkaus_id INTEGER)
    RETURNS TEXT AS
$$
DECLARE
    paikkaus   RECORD;
    pk1geom    GEOMETRY;
    pk2geom    GEOMETRY;
    pk3geom    GEOMETRY;
    radius     INTEGER := 10;
    pk1_pituus NUMERIC;
    pk2_pituus NUMERIC;
    pk3_pituus NUMERIC;
    pkluokka_t TEXT;

BEGIN
    SELECT (tierekisteriosoite).tie  AS tie,
           (tierekisteriosoite).aosa AS aosa,
           (tierekisteriosoite).losa AS losa,
           CASE
               WHEN (tierekisteriosoite).tie IS NOT NULL
                   AND (tierekisteriosoite).aosa IS NOT NULL
                   AND (tierekisteriosoite).losa IS NOT NULL
                   AND (tierekisteriosoite).aet IS NOT NULL
                   AND (tierekisteriosoite).let IS NOT NULL
                   THEN
                   (SELECT tieosoitteelle_geometria AS geometria
                      FROM tieosoitteelle_geometria(
                          CAST((tierekisteriosoite).tie AS INTEGER),
                          CAST((tierekisteriosoite).aosa AS INTEGER),
                          CAST((tierekisteriosoite).aet AS INTEGER),
                          CAST((tierekisteriosoite).losa AS INTEGER),
                          CAST((tierekisteriosoite).let AS INTEGER)))
               ELSE
                   NULL
               END                               AS geometria
      FROM paikkaus p
     WHERE p.id = reikapaikkaus_id
      INTO paikkaus;

    -- Tieosoite voi olla tyhjä
    IF paikkaus.tie IS NULL OR paikkaus.aosa IS NULL OR paikkaus.losa IS NULL OR paikkaus.geometria IS NULL THEN
        RAISE NOTICE 'Tieosoite puuttuu reikäpaikkaukselta % - Ei lasketa PK luokkaa', reikapaikkaus_id;
        RAISE NOTICE 'paikkaus.tie %, paikkaus.aosa: %, paikkaus.losa: %, paikkaus.geometria: %',
            paikkaus.tie, paikkaus.aosa, paikkaus.losa, paikkaus.geometria;
        RETURN 'tieosoite puutteellinen';
    END IF;

    SELECT ST_BUFFER(ST_UNION(p.geometria), radius, 'endcap=flat')
      INTO pk1geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK1'
       AND p.tie = paikkaus.tie
       AND p.aosa = paikkaus.aosa
       AND p.losa = paikkaus.losa;

    SELECT ST_BUFFER(ST_UNION(p.geometria), radius, 'endcap=flat')
      INTO pk2geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK2'
       AND p.tie = paikkaus.tie
       AND p.aosa = paikkaus.aosa
       AND p.losa = paikkaus.losa;

    SELECT ST_BUFFER(ST_UNION(p.geometria), radius, 'endcap=flat')
      INTO pk3geom
      FROM paallysteen_korjausluokka p
     WHERE p.korjausluokka = 'PK3'
       AND p.tie = paikkaus.tie
       AND p.aosa = paikkaus.aosa
       AND p.losa = paikkaus.losa;

    pk1_pituus := (SELECT COALESCE(st_length(st_intersection(paikkaus.geometria, pk1geom)), 0));
    pk2_pituus := (SELECT COALESCE(st_length(st_intersection(paikkaus.geometria, pk2geom)), 0));
    pk3_pituus := (SELECT COALESCE(st_length(st_intersection(paikkaus.geometria, pk3geom)), 0));

    CASE
        WHEN pk1_pituus > pk2_pituus AND pk1_pituus > pk3_pituus THEN pkluokka_t := 'PK1';
        WHEN pk2_pituus > pk1_pituus AND pk2_pituus > pk3_pituus THEN pkluokka_t := 'PK2';
        WHEN pk3_pituus > pk1_pituus AND pk3_pituus > pk2_pituus THEN pkluokka_t := 'PK3';
        ELSE pkluokka_t := 'Ei tiedossa';
        END CASE;

    UPDATE paikkaus SET pkluokka = pkluokka_t::korjausluokkatyyppi WHERE id = reikapaikkaus_id;
    RETURN pkluokka_t;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION leikkaavat_pohjavesialueet(tie INTEGER, aosa INTEGER, aet INTEGER,
                                                      losa INTEGER, let INTEGER) RETURNS SETOF POHJAVESIALUE_RIVI AS
$$

DECLARE
    p    RECORD;
    rivi POHJAVESIALUE_RIVI;
BEGIN
    FOR p IN SELECT distinct on (pa.nimi) nimi, pa.tunnus, pa.alue
               FROM pohjavesialue pa
              WHERE ST_INTERSECTS(pa.alue,
                                  (SELECT * FROM
                                      tieosoitteelle_geometria(tie, aosa, aet, losa, let)))
        LOOP
            rivi := (p.nimi, p.tunnus);
            RETURN NEXT rivi;
        END LOOP;
END;
$$ LANGUAGE plpgsql;
