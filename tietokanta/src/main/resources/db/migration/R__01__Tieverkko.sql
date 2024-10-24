-- PostGIS 3-versiossa ST_Distancen kanssa täytyy
-- geometrioita joilla on määritelty koordinaatistotunniste (SRID).
-- 4326 vastaa wgs84-koordinaatistoa.

CREATE OR REPLACE FUNCTION ST_Distance84(geom1 GEOMETRY, geom2 GEOMETRY)
 RETURNS float AS $$
BEGIN
 RETURN ST_Distance(ST_SetSRID(geom1, 4326), ST_SetSRID(geom2, 4326));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Kääntää MULTILINESTRING geometrian osien järjestyksen toisin päin.
-- Tämä tarvitaan kun kerätään vasemmalla ajoradalla olevia geometrioita
-- yhteen. ST_Reverse kääntää vain pisteet, mutta ei osia, piirtäessä
-- loppunuoli jää ensimmäisen palan loppuun, jos osien järjestystä ei käännä.
CREATE OR REPLACE FUNCTION kaanna_multilinestring(geom GEOMETRY)
  RETURNS GEOMETRY AS $$
DECLARE
  i INTEGER;
  tulos GEOMETRY[];
BEGIN
  tulos := ARRAY[]::GEOMETRY[];
  FOR i IN 1..ST_NumGeometries(geom) LOOP
    tulos := ST_GeometryN(geom, i) || tulos;
  END LOOP;
  RETURN ST_Collect(tulos);
END;
$$ LANGUAGE plpgsql;

-- Yhdistää linestring ja multilinestringit yhdeksi multilinestringiksi
CREATE OR REPLACE FUNCTION yhdista_multilinestring(geometriat GEOMETRY)
  RETURNS GEOMETRY AS $$
DECLARE
  i INTEGER;
  j INTEGER;
  viiva GEOMETRY;
  tulos GEOMETRY[];
BEGIN
  tulos := ARRAY[]::GEOMETRY[];
  FOR i IN 1..ST_NumGeometries(geometriat) LOOP
    viiva := ST_GeometryN(geometriat, i);
    IF ST_GeometryType(viiva) = 'ST_MultiLineString' THEN
      FOR j IN 1..ST_NumGeometries(viiva) LOOP
        tulos := tulos || ST_GeometryN(viiva, j);
      END LOOP;
    ELSE
      tulos := tulos || viiva;
    END IF;
  END LOOP;
  RETURN ST_Collect(tulos);
END;
$$ LANGUAGE plpgsql;


-- Otetaan tällä huomioon myös se, että tien 2d-projektiosta katoaa tieto korkeuseroista.
-- Käytännössä siis tie on pitempi, kuin sen kaksiuloinen projektio, joka tarkoittaa sitä että 2d-projektion metri on
-- alle metri oikeassa elämässä.
CREATE OR REPLACE FUNCTION tieosoitteelle_viiva (
    tie_ INTEGER,
    aosa_ INTEGER, aet_ INTEGER,
    losa_ INTEGER, let_ INTEGER) RETURNS geometry AS $$
DECLARE
    osan_projektoitu_pituus FLOAT;
    ajorata_ INTEGER; -- 1 on oikea ajorata tien kasvusuuntaan, 2 on vasen
    aosa INTEGER;
    aet INTEGER;
    losa INTEGER;
    let INTEGER;
    -- Osan looppauksen jutut
    osa_ INTEGER;
    e1 FLOAT;
    e2 FLOAT;
    osan_geometria GEOMETRY;
    osan_patka GEOMETRY;
    -- Tuloksena syntyvä geometria
    tulos GEOMETRY[];
    viiva GEOMETRY;
    -- 2d-projektoidun metrin suhde oikeaan metriin
    keskiarvo_metri FLOAT;
    osan_oikea_pituus INTEGER;
BEGIN
    tulos := ARRAY[]::GEOMETRY[];
    -- Päätellään kumpaa ajorataa ollaan menossa
    -- Jos TR-osoite on kasvava, mennään oikeaa ajorataa (1) muuten mennään vasenta (2).
    -- Yksiajorataisten teiden kohdalla päätellään samaan tapaan ajosuunta.
    ajorata_ := 1;
    IF (aosa_ > losa_ OR (aosa_ = losa_ AND aet_ > let_)) THEN
        ajorata_ := 2;
        aosa := losa_;
        aet := let_;
        losa := aosa_;
        let := aet_;
    ELSE
        aosa := aosa_;
        aet := aet_;
        let := let_;
        losa := losa_;
    END IF;
    --RAISE NOTICE 'Haetaan geometria tie %, ajorata %', tie_, ajorata_;
    tulos := NULL;
    FOR osa_ IN aosa..losa LOOP
            -- Otetaan osan geometriaviivasta e1 -- e2 pätkä
            SELECT geom FROM tr_osan_ajorata toa
            WHERE toa.tie=tie_ AND toa.osa=osa_ AND toa.ajorata=ajorata_
            INTO osan_geometria;
            IF osan_geometria IS NULL THEN
                CONTINUE;
            END IF;

            osan_projektoitu_pituus := st_length(osan_geometria);

            -- Kuinka pitkä matka keskiarvollisesti 2d-projektoitu metri on oikeassa elämässä, ts. kuinka paljon pitempi tie on kuin sen geometrian pituus.
            SELECT pituus
            FROM tr_osien_pituudet
            WHERE tie = tie_ AND osa = osa_
            INTO osan_oikea_pituus;

            -- Tällä suhdeluvulla saadaan oikea pituus käännettyä projektoiduksi pituudeksi, jota tarvitaan geometrian luontiin.
            keskiarvo_metri := osan_projektoitu_pituus / osan_oikea_pituus;

            -- Päätellään alkuetäisyys tälle osalle
            IF osa_ = aosa THEN
                e1 := aet * keskiarvo_metri;
            ELSE
                e1 := 0;
            END IF;
            -- Päätellään loppuetäisyys tälle osalle
            IF osa_ = losa THEN
                e2 := let * keskiarvo_metri;
            ELSE
                e1 := LEAST(e1, osan_projektoitu_pituus);
                e2 := osan_projektoitu_pituus;
            END IF;
            -- RAISE NOTICE 'Haetaan geometriaa tien % osan % valille % - %', tie_, osa_, e1, e2;
            -- Lisätään jos geometria löytyi (osa on olemassa)
            IF e1 != e2 THEN
                osan_patka := ST_LineSubstring(osan_geometria, LEAST(1,e1/osan_projektoitu_pituus), LEAST(1,e2/osan_projektoitu_pituus));
                IF ajorata_ = 1 THEN
                    tulos := tulos || osan_patka;
                ELSIF ajorata_ = 2 THEN
                    IF ST_GeometryType(osan_patka)='ST_MultiLineString' THEN
                        osan_patka = kaanna_multilinestring(osan_patka);
                    END IF;
                    tulos := ST_Reverse(osan_patka) || tulos;
                END IF;
            END IF;
        END LOOP;
    viiva := ST_Collect(tulos);
    IF ST_GeometryType(viiva)='ST_GeometryCollection' THEN
        IF ST_NumGeometries(viiva)=1 THEN
            viiva := ST_GeometryN(viiva, 1);
        ELSE
            viiva := yhdista_multilinestring(viiva);
        END IF;
    END IF;
    RETURN viiva;
END;
$$ LANGUAGE plpgsql;

-- wrapperi rajapinnan pitämiseksi samana
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER)
  RETURNS SETOF geometry
AS $$
BEGIN
  IF aosa_=losa_ AND aet_=let_ THEN
    RETURN NEXT tierekisteriosoitteelle_piste(tie_, aosa_, aet_);
  ELSE
    RETURN NEXT tieosoitteelle_viiva(tie_, aosa_, aet_, losa_, let_);
  END IF;
END;
$$ LANGUAGE plpgsql;

-- Tierekisteriosoitteelle viiva, ajoradan mukaan
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva_ajr(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER, ajr_ INTEGER)
  RETURNS SETOF geometry
AS $$
DECLARE
  tmp_osa INTEGER;
  tmp_et INTEGER;
  g GEOMETRY;
BEGIN
 IF (ajr_ = 1 AND (aosa_ > losa_ OR (aosa_ = losa_ AND aet_ > let_)))
    OR
    (ajr_ = 2 AND (aosa_ < losa_ OR (aosa_ = losa_ AND aet_ < let_)))
  THEN
   -- Jos halutaan 1 ajoradan geometria, mutta osat ovat laskevassa järjestyksessä
   -- tai halutaan 2 ajoradan geometria, mutta osat ovat nousevassa järjestyksessä
   -- => vaihdetaan alku ja loppu
   tmp_osa := aosa_;
   tmp_et := aet_;
   aosa_ := losa_;
   aet_ := let_;
   losa_ := tmp_osa;
   let_ := tmp_et;
 END IF;
 FOR g IN SELECT tierekisteriosoitteelle_viiva(tie_, aosa_, aet_, losa_, let_)
 LOOP
   RETURN NEXT g;
 END LOOP;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_piste(tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER) RETURNS geometry AS $$
DECLARE
  osan_geometria GEOMETRY;
  osan_kohta FLOAT;
  tulos GEOMETRY;
  osan_projektoitu_pituus FLOAT;
  osan_oikea_pituus INTEGER;
  keskiarvo_metri FLOAT;
BEGIN
  SELECT geom
  FROM tr_osan_ajorata
  WHERE tie=tie_ AND osa=aosa_
  ORDER BY ajorata
  LIMIT 1
  INTO osan_geometria;

  osan_projektoitu_pituus := st_length(osan_geometria);

  SELECT pituus FROM tr_osien_pituudet WHERE tie=tie_ AND osa=aosa_ INTO osan_oikea_pituus;
  keskiarvo_metri := osan_projektoitu_pituus / osan_oikea_pituus;

  osan_kohta := LEAST(1, aet_/osan_projektoitu_pituus*keskiarvo_metri);
  tulos := ST_LineSubstring(osan_geometria, osan_kohta, osan_kohta);
  IF ST_GeometryType(tulos)='ST_GeometryCollection' AND ST_NumGeometries(tulos)=1 THEN
    tulos := ST_GeometryN(tulos, 1);
  END IF;
  RETURN tulos;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION laske_tr_osan_kohta(osan_geometria GEOMETRY, piste GEOMETRY, tie INTEGER, osa INTEGER)
    RETURNS tr_osan_kohta AS $$
DECLARE
    lahin_piste                GEOMETRY;
    aet                        INTEGER;
    osan_projektoitu_pituus    FLOAT;
    etaisyys_pisteeseen        NUMERIC;
    tieosan_alku               GEOMETRY;
    tieosan_loppu              GEOMETRY;
    alun_etaisyys_pisteesta    NUMERIC;
    lopun_etaisyys_pisteesta   NUMERIC;
    osan_geometrian_viiva      RECORD;
    lahin_viiva                INTEGER;
    viivan_etaisyys_pisteeseen NUMERIC;
    lyhin_etaisyys_pisteeseen  NUMERIC;
    laskuri                    INTEGER;
    osan_oikea_pituus          INTEGER;
    keskiarvo_metri            FLOAT;
BEGIN

    laskuri := 0;
    lahin_viiva := 0;
    viivan_etaisyys_pisteeseen := 0;
    lyhin_etaisyys_pisteeseen := -1;
    etaisyys_pisteeseen := 0;

    osan_projektoitu_pituus := St_Length(osan_geometria);
    SELECT pituus FROM tr_osien_pituudet tap WHERE tap.tie=laske_tr_osan_kohta.tie AND tap.osa=laske_tr_osan_kohta.osa INTO osan_oikea_pituus;
    keskiarvo_metri :=  1 / (osan_projektoitu_pituus / osan_oikea_pituus);

    --RAISE NOTICE 'TYYPPI: % ', St_GeometryType(osan_geometria);

    IF St_GeometryType(osan_geometria) = 'ST_MultiLineString' THEN
        -- Joskus osan geometria on multilinestring eli tienosa voi koostua useista erillisistä viivoista.
        -- Tämä on otettava huomioon, kun päätellään pisteen suhdetta tienosaan kokonaisuutena.
        -- Etsitään geoemtriasta se viiva, jota lähinnä kohdistettava piste on ja
        -- pistettä lähinnä oleva kohta tässä viivassa.
        FOR osan_geometrian_viiva IN (SELECT (St_Dump(osan_geometria)).path[1] as int,
                                             (ST_Dump(osan_geometria)).geom    as geom)
            LOOP
                viivan_etaisyys_pisteeseen := (SELECT ST_Distance84(osan_geometrian_viiva.geom, piste));
                IF lyhin_etaisyys_pisteeseen = -1 OR lyhin_etaisyys_pisteeseen > viivan_etaisyys_pisteeseen THEN
                    lyhin_etaisyys_pisteeseen := viivan_etaisyys_pisteeseen;
                    lahin_viiva := osan_geometrian_viiva.int;
                    lahin_piste := (SELECT ST_ClosestPoint(osan_geometrian_viiva.geom, piste));
                END IF;
            END LOOP;
    ELSE -- ST_LineString
    -- Kun geometria on linestring, on vain yksi viiva
        lahin_viiva = 1;
        lahin_piste := (SELECT ST_ClosestPoint(osan_geometria, piste));
    END IF;

    --RAISE NOTICE 'Lähin viiva: %', lahin_viiva;

    -- Lasketaan edellä selvitettyjen tietojen perusteella pisteen etäisyys tien alkupäästä.
    -- Huomioidaan laskennassa myös tienosan kaikki pistettä lähinnä olevaa viivaa edeltävät viivat.
    WHILE laskuri < lahin_viiva
        LOOP
            laskuri := laskuri + 1;
            IF laskuri = lahin_viiva THEN
                -- Jos piste on viivan alussa, etäisyys pisteeseen on 0. Muuten lasketaan etäisyys pisteeseen leikkaamalla
                -- viiva pisteen kohdalta ja laskemalla leikatun viivan pituus.
                IF st_startpoint(ST_GeometryN(osan_geometria, laskuri))::point != lahin_piste::point THEN
                    etaisyys_pisteeseen := etaisyys_pisteeseen +
                                           ((SELECT ST_Length(ST_GeometryN(ST_Split(
                                                                              ST_Snap(ST_GeometryN(osan_geometria, laskuri), lahin_piste, 0.1),
                                                                              lahin_piste), 1))) * keskiarvo_metri);
                END IF;
            ELSE
                etaisyys_pisteeseen := etaisyys_pisteeseen + (St_Length(ST_GeometryN(osan_geometria, laskuri)) * keskiarvo_metri);
            END IF;
            --RAISE NOTICE 'Laskuri: %', laskuri;
            --RAISE NOTICE 'Etaisyys pisteeseen: %', etaisyys_pisteeseen;
        END LOOP;

    -- Jos tarkasteltava piste on aivan tienosan päässä, etäisyydeksi palautuu koko osan pituus
    -- riippumatta siitä onko piste tienosan alku- vai loppupäässä. Alkupässä aet pitäisi olla 0, eikä sama kuin osan pituus.
    -- Tarkistetaan siksi pisteen suhde myös tienosan alkuun ja loppuun, jos edellä saatiin aet-arvoksi tienosan pituus.
    aet := etaisyys_pisteeseen;

    IF aet = osan_oikea_pituus THEN
        tieosan_alku := tierekisteriosoitteelle_piste(tie, osa, 0);
        tieosan_loppu := tierekisteriosoitteelle_piste(tie, osa, aet);
        alun_etaisyys_pisteesta := ST_Distance84(lahin_piste, tieosan_alku);
        lopun_etaisyys_pisteesta := ST_Distance84(lahin_piste, tieosan_loppu);

        IF  alun_etaisyys_pisteesta < lopun_etaisyys_pisteesta THEN
            aet = 0;
        END IF;

    END IF;

    RETURN ROW(aet, lahin_piste);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION yrita_tierekisteriosoite_pisteelle2(
  piste geometry, threshold INTEGER)
  RETURNS tr_osoite AS $$
DECLARE
  osa_ RECORD;
  kohta tr_osan_kohta;
BEGIN
  SELECT tie,osa,ajorata,geom,ST_Distance84(piste, geom) as d
  FROM tr_osan_ajorata
  WHERE geom IS NOT NULL AND
        ST_Intersects(piste, envelope)
  ORDER BY d, ajorata ASC LIMIT 1
  INTO osa_;

  -- Jos osa löytyy, ota etäisyys
  IF osa_ IS NULL THEN
    RETURN NULL;
  ELSE
    kohta := laske_tr_osan_kohta(osa_.geom, piste, osa_.tie, osa_.osa);
    RETURN ROW(osa_.tie, osa_.osa, kohta.etaisyys, null::INTEGER, null::INTEGER, kohta.piste::GEOMETRY);
  END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION laheiset_osoitteet_pisteelle(piste GEOMETRY, tarkkuus INTEGER)
  RETURNS laheinen_osoiterivi[] AS $$
DECLARE
  r RECORD;
  lr laheinen_osoiterivi;
  kohta tr_osan_kohta;
  rivit laheinen_osoiterivi[];
BEGIN
  rivit := ARRAY[]::laheinen_osoiterivi[];
  FOR r IN SELECT tie,osa,ajorata,geom,ST_Distance84(piste, geom) as d, geom
           FROM tr_osan_ajorata
           WHERE geom IS NOT NULL AND
                 ST_Intersects(piste, envelope)
           ORDER BY d ASC
  LOOP
    IF r.d <= tarkkuus THEN
      kohta := laske_tr_osan_kohta(r.geom, piste, r.tie,r.osa);
      lr := (r.tie, r.osa, kohta.etaisyys, r.ajorata, r.d, r.geom);
      rivit := rivit || lr;
    END IF;
  END LOOP;
  RETURN rivit;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteelle(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
  osoite tr_osoite;
BEGIN
  osoite := yrita_tierekisteriosoite_pisteelle2(piste, treshold);
  IF osoite IS NULL THEN
    RAISE EXCEPTION 'pisteelle % ei löydy tietä', piste;
  ELSE
    RETURN osoite;
  END IF;
END;
$$ LANGUAGE plpgsql;

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
BEGIN
    SELECT a.tie,
           a.osa                                                       as alkuosa,
           a.ajorata,
           b.osa                                                       as loppuosa,
           a.geom                                                      as alkuosa_geom,
           b.geom                                                      as loppuosa_geom,
           ST_Length(a.geom):: INTEGER                                 as alkuosa_geom_pituus,
           ST_Length(b.geom):: INTEGER                                 as loppuosa_geom_pituus,
           (ST_Distance84(apiste, a.geom) + ST_Distance84(bpiste, b.geom)) as d
    FROM tr_osan_ajorata a
             JOIN tr_osan_ajorata b
                  ON b.tie = a.tie AND b.ajorata = a.ajorata
    WHERE a.geom IS NOT NULL
      AND b.geom IS NOT NULL
      AND ST_Intersects(apiste, a.envelope)
      AND ST_Intersects(bpiste, b.envelope)
    ORDER BY d, ajorata ASC
    LIMIT 1
    INTO r;
    IF r IS NULL THEN
        RETURN NULL;
    ELSE

        aosa := r.alkuosa;
        alkukohta := laske_tr_osan_kohta(r.alkuosa_geom, apiste, r.tie, r.alkuosa);
        aet := alkukohta.etaisyys;
        losa := r.loppuosa;
        loppukohta := laske_tr_osan_kohta(r.loppuosa_geom, bpiste, r.tie, r.loppuosa);
        let := loppukohta.etaisyys;

        geomertria := tieosoitteelle_viiva(r.tie, aosa, aet, losa, let);
        --RAISE NOTICE 'Lopputulos % / % / % / % / % . Geometria: %', r.tie, aosa, aet, losa, let, geomertria;
        RETURN ROW (r.tie, aosa, aet, losa, let, geomertria);

    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION yrita_tierekisteriosoite_pisteille_max(
  apiste geometry, bpiste geometry, max_pituus NUMERIC)
  RETURNS tr_osoite AS $$
DECLARE
  r RECORD;
  aosa INTEGER;
  aet INTEGER;
  alkukohta tr_osan_kohta;
  losa INTEGER;
  let INTEGER;
  loppukohta tr_osan_kohta;
  geom GEOMETRY;
  tmp_osa INTEGER;
  tmp_et INTEGER;
  min_pituus NUMERIC;
  pituus NUMERIC;
BEGIN
  -- Minimipituus on linnuntie (teleportaatiota ei sallittu)
  -- miinus 15 metriä (varotoimi jos GPS pisteitä raportoitu ja niissä epätarkkuutta)
  min_pituus := ST_Distance84(apiste, bpiste) - 15.0;
  FOR r IN SELECT a.tie,a.osa as alkuosa, a.ajorata, b.osa as loppuosa,
                        a.geom as alkuosa_geom, b.geom as loppuosa_geom,
                        (ST_Distance84(apiste, a.geom) + ST_Distance84(bpiste, b.geom)) as d
           FROM tr_osan_ajorata a JOIN tr_osan_ajorata b
               ON b.tie=a.tie AND b.ajorata=a.ajorata
           WHERE a.geom IS NOT NULL AND
                 b.geom IS NOT NULL AND
                 ST_Intersects(apiste, a.envelope) AND
                 ST_Intersects(bpiste, b.envelope)
           ORDER BY d ASC
  LOOP
    aosa := r.alkuosa;
    alkukohta := laske_tr_osan_kohta(r.alkuosa_geom, apiste, r.tie, r.alkuosa);
    aet := alkukohta.etaisyys;
    losa := r.loppuosa;
    loppukohta := laske_tr_osan_kohta(r.loppuosa_geom, bpiste, r.tie, r.loppuosa);
    let := loppukohta.etaisyys;
    -- Varmista TR-osoitteen suunta ajoradan mukaan
    --RAISE NOTICE 'ajorata %', r.ajorata;
    IF (r.ajorata = 1 AND (aosa > losa OR (aosa=losa AND aet > let))) OR
       (r.ajorata = 2 AND (aosa < losa OR (aosa=losa AND aet < let))) THEN
      tmp_osa := aosa;
      aosa := losa;
      losa := tmp_osa;
      tmp_et := aet;
      aet := let;
      let := tmp_et;
    END IF;
    geom := tieosoitteelle_viiva(r.tie, aosa, aet, losa, let);
    pituus := ST_Length(geom);
    IF(pituus >= min_pituus AND pituus <= max_pituus) THEN
      RETURN ROW(r.tie, aosa, aet, losa, let, geom);
    END IF;
  END LOOP;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;



-- kuvaus: Yritä hakea TR osoite pisteille. Heitä poikkeus, jos ei löydy.
CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteille(
  alkupiste geometry,
  loppupiste geometry,
  treshold INTEGER) RETURNS tr_osoite AS $$
DECLARE
  osoite tr_osoite;
BEGIN
  osoite := yrita_tierekisteriosoite_pisteille2(alkupiste, loppupiste, treshold);
  IF osoite IS NULL THEN
    RAISE EXCEPTION 'pisteillä ei yhteistä tietä';
  END IF;
  RETURN osoite;
END;
$$ LANGUAGE plpgsql;

-- Hakee pisimmän mahdollisen pituuden tieosalle
CREATE OR REPLACE FUNCTION hae_tieosan_pituus(tie_ INT, osa_ INT)
  RETURNS INT AS $$
DECLARE
  ajr0_pituus INT;
  ajr1_pituus INT;
  ajr2_pituus INT;
BEGIN
  SELECT pituus
  FROM tr_ajoratojen_pituudet tr
  WHERE tr.tie = tie_ AND tr.osa = osa_ AND tr.ajorata = 0
  INTO ajr0_pituus;

  SELECT pituus
  FROM tr_ajoratojen_pituudet tr
  WHERE tr.tie = tie_ AND tr.osa = osa_ AND tr.ajorata = 1
  INTO ajr1_pituus;

  SELECT pituus
  FROM tr_ajoratojen_pituudet tr
  WHERE tr.tie = tie_ AND tr.osa = osa_ AND tr.ajorata = 2
  INTO ajr2_pituus;

  RETURN (
    coalesce(ajr0_pituus, 0) +
    GREATEST(coalesce(ajr1_pituus, 0),
             coalesce(ajr2_pituus, 0)));
END;
$$ LANGUAGE plpgsql;

-- paivittaa tr-rutiinien käyttämät taulut
CREATE OR REPLACE FUNCTION paivita_tr_taulut()
  RETURNS VOID AS $$

DECLARE
BEGIN
  -- Poista vanhat pituudet
  DELETE FROM tr_osien_pituudet;
  -- Laske uudet pituudet
  INSERT INTO tr_osien_pituudet
    SELECT
      tie,
      osa,
      hae_tieosan_pituus(tie, osa) AS pituus
    FROM
      (SELECT
         tie,
         osa
       FROM tr_ajoratojen_pituudet
       GROUP BY tie, osa
       ORDER BY tie, osa) AS osat;
  -- Päivitä osien envelopet
  UPDATE tr_osan_ajorata
  SET envelope = ST_Expand(ST_Envelope(geom), 250);
END;
$$ LANGUAGE plpgsql;

-- Hakee annetuille pisteille (geometrycollection) viivan jokaiselle
-- pistevälille. Palauttaa jokaiselle välille alkupisteen, loppupisteen
-- ja tieverkolle projisoidun geometrian. Jos projisoitua geometriaa ei
-- löydy, palautetaan NULL.
CREATE OR REPLACE FUNCTION tieviivat_pisteille(
  pisteet geometry,
  threshold INTEGER) RETURNS SETOF RECORD AS $$
DECLARE
  alku geometry;
  loppu geometry;
  i INTEGER;
  pisteita INTEGER;
BEGIN
  i := 1;
  pisteita := ST_NumGeometries(pisteet);
  WHILE i < pisteita LOOP
    alku := ST_GeometryN(pisteet, i);
    loppu := ST_GeometryN(pisteet, i+1);
    --RAISE NOTICE 'alku: %, loppu: %', st_astext(alku), st_astext(loppu);
    RETURN NEXT (alku, loppu,
                 (SELECT ytp.geometria
                  FROM yrita_tierekisteriosoite_pisteille2(alku, loppu, threshold) ytp));
    i := i + 1;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Hakee annetuille pisteille viivan jokaiselle pistevälille, huomioiden
-- pisteiden välisen ajan. Maksimi hyväksyttävä geometrisoitu pituus on
-- pisteiden välinen aika sekunteina kertaa max nopeus metreissä sekuntia.
-- Maksiminopeus annetaan kilometrinä tunnissa, se jaetaan 3.6:lla jotta saadaan m/s.
CREATE OR REPLACE FUNCTION tieviivat_pisteille_aika(pisteet piste_aika[], max_nopeus INTEGER) RETURNS SETOF RECORD AS $$
DECLARE
  alku piste_aika;
  loppu piste_aika;
  alkupiste GEOMETRY;
  loppupiste GEOMETRY;
  aika NUMERIC;
  i INTEGER;
  pisteita INTEGER;
BEGIN
  i := 1;
  pisteita := array_length(pisteet, 1);
  WHILE i < pisteita LOOP
    alku := pisteet[i];
    loppu := pisteet[i+1];
    aika := EXTRACT(EPOCH FROM age(loppu.aika, alku.aika));
    alkupiste := ST_MakePoint(alku.x, alku.y);
    loppupiste := ST_MakePoint(loppu.x, loppu.y);
    RETURN NEXT (alkupiste, loppupiste,
                 (SELECT ytp.geometria
                  FROM yrita_tierekisteriosoite_pisteille_max(alkupiste, loppupiste, (max_nopeus / 3.6) * aika) ytp));
    i := i + 1;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Hakee alkupisteen tiegeometriasta, joka on linestring tai multilinestring
CREATE OR REPLACE FUNCTION alkupiste(g geometry) RETURNS geometry AS $$
DECLARE
  line geometry;
BEGIN
  IF ST_GeometryType(g)='ST_MultiLineString' THEN
    line := ST_GeometryN(g, 1);
  ELSE
    line := g;
  END IF;
  RETURN ST_StartPoint(line);
END;
$$ LANGUAGE plpgsql;

-- Hakee loppupisteen tiegeometriasta
CREATE OR REPLACE FUNCTION loppupiste(g geometry) RETURNS geometry AS $$
DECLARE
  line geometry;
BEGIN
  IF ST_GeometryType(g)='ST_MultiLineString' THEN
    line := ST_GeometryN(g, ST_NumGeometries(g));
  ELSE
    line := g;
  END IF;
  RETURN ST_EndPoint(line);
END;
$$ LANGUAGE plpgsql;
