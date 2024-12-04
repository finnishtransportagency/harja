-- Päivitetään mpu_kustannukset taulun nimi, koska sinne tallennetaan myös muiden paikkausten kustannuksia
ALTER TABLE mpu_kustannukset RENAME TO paikkauskustannukset;

-- Lisätään paikkauskohteille pkluokka
ALTER TABLE paikkauskohde ADD COLUMN pkluokka text;

-- Lasketaan paikkauskohteelle pkluokka tieosoitteen perusteella
CREATE OR REPLACE FUNCTION paivita_paikkauskohteen_korjausluokka(paikkauskohde_id INTEGER)
    RETURNS VOID AS
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
    SELECT (tierekisteriosoite_laajennettu).tie as tie,
           (tierekisteriosoite_laajennettu).aosa as aosa,
           (tierekisteriosoite_laajennettu).losa as losa,
           CASE WHEN (tierekisteriosoite_laajennettu).tie IS NOT NULL
               AND (tierekisteriosoite_laajennettu).aosa IS NOT NULL
               AND (tierekisteriosoite_laajennettu).losa IS NOT NULL
               AND (tierekisteriosoite_laajennettu).aet IS NOT NULL
               AND (tierekisteriosoite_laajennettu).let IS NOT NULL
                    THEN
                    (SELECT tierekisteriosoitteelle_viiva AS geometria
                       FROM tierekisteriosoitteelle_viiva(
                               CAST((tierekisteriosoite_laajennettu).tie AS INTEGER),
                               CAST((tierekisteriosoite_laajennettu).aosa AS INTEGER),
                               CAST((tierekisteriosoite_laajennettu).aet AS INTEGER),
                               CAST((tierekisteriosoite_laajennettu).losa AS INTEGER),
                               CAST((tierekisteriosoite_laajennettu).let AS INTEGER)))
                ELSE
                    NULL
               END AS geometria
      FROM paikkauskohde pk
     WHERE pk.id = paikkauskohde_id INTO kohde;

    -- Tieosoite voi olla tyhjä
    IF kohde.tie IS NULL OR kohde.aosa IS NULL OR kohde.losa IS NULL OR kohde.geometria IS NULL  THEN
        RAISE NOTICE 'Tieosoite puuttuu paikkauskohteelta % - Ei lasketa PK luokkaa', paikkauskohde_id;
        RAISE NOTICE 'kohde.tie %, kohde.aosa: %, kohde.losa: %, kohde.geometria: %', kohde.tie, kohde.aosa, kohde.losa, kohde.geometria;
        RETURN;
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

    UPDATE paikkauskohde SET pkluokka = pkluokka_t WHERE id = paikkauskohde_id;

END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_paikkauskohteiden_korjausluokat(alku DATE, loppu DATE)
    RETURNS VOID AS
$$
DECLARE
    kohdeid      integer;


BEGIN

    -- Loopataan löydetyt paikkauskohteet läpi
    FOR kohdeid IN (SELECT pk.id
                      FROM paikkauskohde pk
                     WHERE pk.alkupvm BETWEEN alku::DATE AND loppu::DATE
                       AND pk.poistettu = FALSE
                     ORDER BY pk.id ASC)
        LOOP
            PERFORM paivita_paikkauskohteen_korjausluokka(kohdeid);
        END LOOP;

END;
$$ LANGUAGE plpgsql;
