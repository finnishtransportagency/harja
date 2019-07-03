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


-- Tatun uusi yritys tieosista
CREATE OR REPLACE FUNCTION tr_osoitteelle_viiva3(
  tie_ INTEGER,
  aosa_ INTEGER, aet_ INTEGER,
  losa_ INTEGER, let_ INTEGER) RETURNS geometry AS $$
DECLARE
  osan_pituus FLOAT;
  ajorata_ INTEGER; -- 1 on oikea ajorata tien kasvusuuntaan, 2 on vasen
  aosa INTEGER;
  aet INTEGER;
  losa INTEGER;
  let INTEGER;
  -- Osan looppauksen jutut
  osa_ INTEGER;
  e1 INTEGER;
  e2 INTEGER;
  osan_geometria GEOMETRY;
  osan_patka GEOMETRY;
  -- Tuloksena syntyvä geometria
  tulos GEOMETRY[];
  viiva GEOMETRY;
BEGIN
  tulos := ARRAY[]::GEOMETRY[];
  -- Päätellään kumpaa ajorataa ollaan menossa
  -- Jos TR-osoite on kasvava, mennään oikeaa ajorataa (1)
  -- muuten mennään vasenta (2).
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
  RAISE NOTICE 'Haetaan geometria tie %, ajorata %', tie_, ajorata_;
  tulos := NULL;
  FOR osa_ IN aosa..losa LOOP
    -- Otetaan osan geometriaviivasta e1 -- e2 pätkä
    SELECT geom FROM tr_osan_ajorata toa
    WHERE toa.tie=tie_ AND toa.osa=osa_ AND toa.ajorata=ajorata_
    INTO osan_geometria;
    IF osan_geometria IS NULL THEN
      CONTINUE;
    END IF;
    osan_pituus := st_length(osan_geometria);
    -- Päätellään alkuetäisyys tälle osalle
    IF osa_ = aosa THEN
      e1 := aet;
    ELSE
      e1 := 0;
    END IF;
    -- Päätellään loppuetäisyys tälle osalle
    IF osa_ = losa THEN
      e2 := let;
    ELSE
      e1 := LEAST(e1, osan_pituus);
      e2 := osan_pituus;
    END IF;
    RAISE NOTICE 'Haetaan geometriaa tien % osan % valille % - %', tie_, osa_, e1, e2;
    -- Lisätään jos geometria löytyi (osa on olemassa)
    IF e1 != e2 THEN
      osan_patka := ST_LineSubstring(osan_geometria, LEAST(1,e1/osan_pituus), LEAST(1,e2/osan_pituus));
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
    RETURN NEXT tr_osoitteelle_viiva3(tie_, aosa_, aet_, losa_, let_);
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
BEGIN
  SELECT geom
  FROM tr_osan_ajorata
  WHERE tie=tie_ AND osa=aosa_
  ORDER BY ajorata
  LIMIT 1
  INTO osan_geometria;
  osan_kohta := LEAST(1, aet_/ST_Length(osan_geometria));
  tulos := ST_LineSubstring(osan_geometria, osan_kohta, osan_kohta);
  IF ST_GeometryType(tulos)='ST_GeometryCollection' AND ST_NumGeometries(tulos)=1 THEN
    tulos := ST_GeometryN(tulos, 1);
  END IF;
  RETURN tulos;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION laske_tr_osan_kohta(osan_geometria GEOMETRY, piste GEOMETRY)
  RETURNS tr_osan_kohta AS $$
DECLARE
  aet INTEGER;
  lahin_piste GEOMETRY;
BEGIN
  SELECT ST_ClosestPoint(osan_geometria, piste)
  INTO lahin_piste;

  SELECT ST_Length(ST_GeometryN(ST_Split(ST_Snap(osan_geometria, lahin_piste, 0.1), lahin_piste), 1))
  INTO aet;

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
  SELECT tie,osa,ajorata,geom,ST_Distance(piste, geom) as d
  FROM tr_osan_ajorata
  WHERE geom IS NOT NULL AND
        ST_Intersects(piste, envelope)
  ORDER BY d ASC LIMIT 1
  INTO osa_;
  -- Jos osa löytyy, ota etäisyys
  IF osa_ IS NULL THEN
    RETURN NULL;
  ELSE
    kohta := laske_tr_osan_kohta(osa_.geom, piste);
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
  FOR r IN SELECT tie,osa,ajorata,geom,ST_Distance(piste, geom) as d, geom
           FROM tr_osan_ajorata
           WHERE geom IS NOT NULL AND
                 ST_Intersects(piste, envelope)
           ORDER BY d ASC
  LOOP
    IF r.d <= tarkkuus THEN
      kohta := laske_tr_osan_kohta(r.geom, piste);
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

CREATE OR REPLACE FUNCTION yrita_tierekisteriosoite_pisteille2(apiste geometry, bpiste geometry, threshold INTEGER) RETURNS tr_osoite AS $$
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
BEGIN
  SELECT a.tie,a.osa as alkuosa, a.ajorata, b.osa as loppuosa,
               a.geom as alkuosa_geom, b.geom as loppuosa_geom,
               (ST_Distance(apiste, a.geom) + ST_Distance(bpiste, b.geom)) as d
  FROM tr_osan_ajorata a JOIN tr_osan_ajorata b
      ON b.tie=a.tie AND b.ajorata=a.ajorata
  WHERE a.geom IS NOT NULL AND
        b.geom IS NOT NULL AND
        ST_Intersects(apiste, a.envelope) AND
        ST_Intersects(bpiste, b.envelope)
  ORDER BY d ASC LIMIT 1
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
    -- Varmista TR-osoitteen suunta ajoradan mukaan
    RAISE NOTICE 'ajorata %', r.ajorata;
    IF (r.ajorata = 1 AND (aosa > losa OR (aosa=losa AND aet > let))) OR
       (r.ajorata = 2 AND (aosa < losa OR (aosa=losa AND aet < let))) THEN
      tmp_osa := aosa;
      aosa := losa;
      losa := tmp_osa;
      tmp_et := aet;
      aet := let;
      let := tmp_et;
    END IF;
    geom := tr_osoitteelle_viiva3(r.tie, aosa, aet, losa, let);
    RETURN ROW(r.tie, aosa, aet, losa, let, geom);
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
  -- miinus 10 metriä (varotoimi jos GPS pisteitä raportoitu ja niissä epätarkkuutta)
  min_pituus := ST_Distance(apiste, bpiste) - 10.0;
  FOR r IN SELECT a.tie,a.osa as alkuosa, a.ajorata, b.osa as loppuosa,
                        a.geom as alkuosa_geom, b.geom as loppuosa_geom,
                        (ST_Distance(apiste, a.geom) + ST_Distance(bpiste, b.geom)) as d
           FROM tr_osan_ajorata a JOIN tr_osan_ajorata b
               ON b.tie=a.tie AND b.ajorata=a.ajorata
           WHERE a.geom IS NOT NULL AND
                 b.geom IS NOT NULL AND
                 ST_Intersects(apiste, a.envelope) AND
                 ST_Intersects(bpiste, b.envelope)
           ORDER BY d ASC
  LOOP
    aosa := r.alkuosa;
    alkukohta := laske_tr_osan_kohta(r.alkuosa_geom, apiste);
    aet := alkukohta.etaisyys;
    losa := r.loppuosa;
    loppukohta := laske_tr_osan_kohta(r.loppuosa_geom, bpiste);
    let := loppukohta.etaisyys;
    -- Varmista TR-osoitteen suunta ajoradan mukaan
    RAISE NOTICE 'ajorata %', r.ajorata;
    IF (r.ajorata = 1 AND (aosa > losa OR (aosa=losa AND aet > let))) OR
       (r.ajorata = 2 AND (aosa < losa OR (aosa=losa AND aet < let))) THEN
      tmp_osa := aosa;
      aosa := losa;
      losa := tmp_osa;
      tmp_et := aet;
      aet := let;
      let := tmp_et;
    END IF;
    geom := tr_osoitteelle_viiva3(r.tie, aosa, aet, losa, let);
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
-- pisteiden välinen aika sekunteina kertaa 30 m/s (108 km/h).
CREATE OR REPLACE FUNCTION tieviivat_pisteille_aika(pisteet piste_aika[]) RETURNS SETOF RECORD AS $$
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
                  FROM yrita_tierekisteriosoite_pisteille_max(alkupiste, loppupiste, 30.0 * aika) ytp));
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
