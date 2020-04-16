CREATE OR REPLACE FUNCTION yrita_tierekisteriosoite_pisteille2(apiste geometry, bpiste geometry, threshold INTEGER) RETURNS tr_osoite AS
$$
DECLARE
    r            RECORD;
    aosa         INTEGER;
    aet          INTEGER;
    alkukohta    tr_osan_kohta;
    losa         INTEGER;
    let          INTEGER;
    loppukohta   tr_osan_kohta;
    geomertria   GEOMETRY;
    osat_geom    GEOMETRY;
    osat_split_a GEOMETRY;
    osat_split_b GEOMETRY;
    tmp_osa      INTEGER;
    tmp_et       INTEGER;
BEGIN
    SELECT a.tie,
           a.osa                                                       as alkuosa,
           a.ajorata,
           b.osa                                                       as loppuosa,
           a.geom                                                      as alkuosa_geom,
           b.geom                                                      as loppuosa_geom,
           ST_Length(a.geom):: INTEGER                                 as alkuosa_geom_pituus,
           ST_Length(b.geom):: INTEGER                                 as loppuosa_geom_pituus,
           (ST_Distance(apiste, a.geom) + ST_Distance(bpiste, b.geom)) as d
    FROM tr_osan_ajorata a
             JOIN tr_osan_ajorata b
                  ON b.tie = a.tie AND b.ajorata = a.ajorata
    WHERE a.geom IS NOT NULL
      AND b.geom IS NOT NULL
      AND ST_Intersects(apiste, a.envelope)
      AND ST_Intersects(bpiste, b.envelope)
    ORDER BY d ASC
    LIMIT 1
    INTO r;
    IF r IS NULL THEN
        RETURN NULL;
    ELSE

        aosa := r.alkuosa;
        alkukohta := laske_tr_osan_kohta(r.alkuosa_geom, apiste);
        aet := alkukohta.etaisyys;
        losa := r.loppuosa;
        loppukohta := laske_tr_osan_kohta(r.loppuosa_geom, bpiste);
        let := loppukohta.etaisyys;

        RAISE NOTICE 'Haettiin osien geometria %, %, % - %.', r.tie, r.ajorata, r.alkuosa, r.loppuosa;

        -- Ajoratatietoon ei 1-ajorataisilla teillä voi luottaa. Harja tallentaa tr_osan_ajorata-tauluun niistä kaksi riviä, joissa on
        -- sama geometria. Kun yllä oleva haku tehdään, palautuu ajorata 1 tai 2 riippumatta ajosuunnasta. Tarkistetaan siis todennäköinen ajorata.
        IF (r.ajorata = 1 AND r.alkuosa > r.loppuosa) THEN
            r.ajorata = 2; -- Jos osat pienenevät ajosuuntaan, on ajettu ajoradan 2 puolella.
        ELSEIF (r.ajorata = 2 AND r.alkuosa < r.loppuosa) THEN
            r.ajorata = 1; -- Jos osat kasvavat ajosuuntaan, on ajettu ajoradan 1 puolella.
        ELSEIF (r.ajorata = 1 AND r.alkuosa = r.loppuosa AND (alkukohta.etaisyys > loppukohta.etaisyys AND
                                                              (alkukohta.etaisyys < r.alkuosa_geom_pituus AND
                                                               loppukohta.etaisyys < r.loppuosa_geom_pituus))) THEN
            r.ajorata = 2; -- Jos ajetaan samalla tien osalla, eivätkä pisteet ole alussa tai lopussa, alkukohdan etäisyysarvo on suurempi, kun ajetaan ajoradan 2 puolella.
        ELSEIF (r.ajorata = 2 AND r.alkuosa = r.loppuosa AND (alkukohta.etaisyys < loppukohta.etaisyys AND
                                                              (alkukohta.etaisyys < r.alkuosa_geom_pituus AND
                                                               loppukohta.etaisyys < r.loppuosa_geom_pituus))) THEN
            r.ajorata = 1; -- Jos ajetaan samalla tien osalla, eivätkä pisteet ole alussa tai lopussa, alkukohdan etäisyysarvo on pienempi, kun ajetaan ajoradan 1 puolella.

        -- TODO:
        -- Tästä kohtaa puuttuu ajoratatiedon arvointi tilanteessa, jossa alku- tai loppupiste on tienosan päässä ja alku- ja loppu on samalla tienosalla.
        -- Jos piste on tienosan päässä, sen pituudeksi palautuu splitin jälkeen koko osan pituus, eikä voi tietää onko piste osan alussa vai lopussa.
        -- On mahdollista, että joissakin tapauksissa ajoratatietoa ei saada tarkistettua ja myöhemmin reitin suunnan päättely menee pieleen.
        -- Kartalla tämä näkyy ylimääräisinä ja puuttuvina pätkinä. Niitä ei kuitenkaan pitäisi olla enää kovin montaa.

        -- Ajorataa käytetään hakuehtona (alla), kun muodostetaan yhdistetty reitti. Yksiajorataisten kohdalla tällä ei ole merkitystä, koska sama ajoratageometria on tallennettu molemmille ajoradoille.
        -- (Yksiajorataisillekin on tallennettu geometriat ajoradoille 1 ja 2.) Vaikka ajorata on hakuehto, voitaisiin ehkä silti käyttää yhdistettyä reittiä ajoradan (ajosuunnan) päättelyyn??
        -- TODO: Pitäisikö alussa vai lopussa-päättelyssä tehdäkin niin, että haetaan yhdistettävään geometriaan myös edeltävät ja jälkeen tulevat tien osat (jos niitä on).
        -- Voisiko splitistä silloin päätellä missä kohtaa alku ja loppu ovat ja mihin suuntaan ajetaan. Tämä ei tietenkään onnistu, jos piste on tien alussa tai lopussa, eikä ympäröiviä tienosia ole.
        -- Testasin pikaisesti muuttamatta päättelylogiikkaa alla, mutta hakemalla pidemmin tienosia. Se ei toiminut, mutta tätä ajatusta voisi kehitellä kuitenkin eteenpäin.

        END IF;

        RAISE NOTICE 'Ajorata tarkistuksen jälkeen: %.', r.ajorata;

        -- Hae ja yhdistä koko reitti: alku- ja loppuosa + väliosat. Yhdistetyn reitin perusteella päätellään onko alkupista/loppupiste osan alussa vai lopussa
        -- eli mihin suuntaan ajetaan, jos alkuosan/loppuosan pituudeksi palautuu osan pituus. Osan pituus palautuu aina, kun piste on jommassa kummassa päässä.
        SELECT st_linemerge(st_collect(geom))
        FROM tr_osan_ajorata
        WHERE tie = r.tie
          AND ajorata = r.ajorata
          AND geom is not null
          AND CASE
                  WHEN (r.alkuosa <= r.loppuosa) THEN
                      osa BETWEEN r.alkuosa AND r.loppuosa
                  ELSE
                      osa BETWEEN r.loppuosa AND r.alkuosa
            END
        INTO osat_geom;

        -- Splittaa yhdistetty reitti a-pisteellä
        SELECT ST_Split(ST_Snap(osat_geom, st_closestpoint(osat_geom, apiste), 0.1), st_closestpoint(osat_geom, apiste))
        INTO osat_split_a;

        -- Jos ensimmäisestä tienosasta (alkuosasta) a-pistellä splitatun pätkän pituus on sama kuin alkuosan pituus, on mahdollista että a-piste on alkuosan alussa tai lopussa. St_Splitin feature.
        -- Tutkitaan yhdistetyn tienpätkän ja ajoradan perusteella kumpi on totta.
        IF (alkukohta.etaisyys = r.alkuosa_geom_pituus) THEN
            RAISE NOTICE 'Tarkista onko alkupiste alkuosan alussa vai lopussa?';
            RAISE NOTICE 'split 1 pituus, split a-pisteen perusteella: %', st_length(st_geometryn(osat_split_a, 1))::INTEGER;
            RAISE NOTICE 'split 2 pituus, split a-pisteen perusteella: %', st_length(st_geometryn(osat_split_a, 2))::INTEGER;
            -- Jos kaikista osista yhdistetty reitti on yhtä pitkä kuin a-pisteellä splitattu viiva, a-piste on alkuosan alussa (St_Splitin feature).
            IF (st_length(osat_geom)::INTEGER = st_length(st_geometryn(osat_split_a, 1))::INTEGER) THEN
                IF r.ajorata = 1 THEN
                    aet = 0;
                ELSE
                    aet = alkukohta.etaisyys;
                END IF;
            ELSE
                IF r.ajorata = 1 THEN
                    aet = alkukohta.etaisyys;
                ELSE
                    aet = 0;
                END IF;
            END IF;
        END IF;
        RAISE NOTICE 'AET % ', aet;

        -- Splittaa yhdistetty reitti b-pisteellä
        SELECT ST_Split(ST_Snap(osat_geom, st_closestpoint(osat_geom, bpiste), 0.1),
                        st_closestpoint(osat_geom, bpiste))
        INTO osat_split_b;

        -- Jos b-pistellä splitatun pätkän pituus on sama kuin loppuosan pituus, on mahdollista että b-piste on loppuosan alussa tai lopussa. St_Splitin feature.
        -- Loppuosa = tienosa jolla b-piste sijaitsee. Ajoradan (ajosuunnan) perusteella päätellään onko piste tienosan lopussa vai alussa.
        IF (loppukohta.etaisyys = r.loppuosa_geom_pituus) THEN
            RAISE NOTICE 'Tarkista onko loppupiste loppuosan alussa vai lopussa?';
            RAISE NOTICE 'split 1 pituus, split b-pisteen perusteella: %', st_length(st_geometryn(osat_split_b, 1))::INTEGER;
            RAISE NOTICE 'split 2 pituus, split b-pisteen perusteella: %', st_length(st_geometryn(osat_split_b, 2))::INTEGER;
            -- Jos kaikista osista yhdistetty reitti on yhtä pitkä kuin b-pisteellä splitattu viiva, b-piste on loppuosan lopussa (St_Splitin feature).
            IF (st_length(osat_geom)::INTEGER = st_length(st_geometryn(osat_split_b, 1))::INTEGER) THEN
                IF r.ajorata = 1 THEN
                    let = loppukohta.etaisyys;
                ELSE
                    let = 0;
                END IF;
            ELSE
                IF r.ajorata = 1 THEN
                    let = 0;
                ELSE
                    let = loppukohta.etaisyys;
                END IF;
            END IF;
        END IF;
        RAISE NOTICE 'LET % ', let;

        -- Varmista TR-osoitteen suunta ajoradan mukaan

        IF (r.ajorata = 1 AND (aosa > losa OR (aosa = losa AND aet > let))) OR
           (r.ajorata = 2 AND (aosa < losa OR (aosa = losa AND aet < let))) THEN
            tmp_osa := aosa;
            aosa := losa;
            losa := tmp_osa;
            tmp_et := aet;
            aet := let;
            let := tmp_et;
            RAISE NOTICE 'Aosa ja losa käännettiin.';
        END IF;
        RAISE NOTICE 'Lopputulos % / % / % / % / % .', r.tie, aosa, aet, losa, let;
        geomertria := tr_osoitteelle_viiva3(r.tie, aosa, aet, losa, let);
        RETURN ROW (r.tie, aosa, aet, losa, let, geomertria);
    END IF;
END;
$$ LANGUAGE plpgsql;