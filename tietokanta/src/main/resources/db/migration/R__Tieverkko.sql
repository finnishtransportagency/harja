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
      e1 := 1;
    END IF;
    -- Päätellään loppuetäisyys tälle osalle
    IF osa_ = losa THEN
      e2 := let;
    ELSE
      e2 := osan_pituus;
    END IF;
    RAISE NOTICE 'Haetaan geometriaa tien % osan % valille % - %', tie_, osa_, e1, e2;
    -- Lisätään jos geometria löytyi (osa on olemassa)
    IF e1 != e2 THEN
      osan_patka := ST_LineSubstring(osan_geometria, LEAST(1,e1/osan_pituus), LEAST(1,e2/osan_pituus));
      IF ajorata_ = 1 THEN
        tulos := tulos || osan_patka;
      ELSIF ajorata_ = 2 THEN
        tulos := ST_Reverse(osan_patka) || tulos;
      END IF;
    END IF;
  END LOOP;
  viiva := ST_Collect(tulos);
  RETURN viiva;
END;
$$ LANGUAGE plpgsql;


-- wrapperi rajapinnan pitämiseksi samana
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER)
  RETURNS SETOF geometry
AS $$
BEGIN
  RETURN NEXT tr_osoitteelle_viiva2(tie_, aosa_, aet_, losa_, let_);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION yrita_tierekisteriosoite_pisteelle2(
  piste geometry, threshold INTEGER)
  RETURNS tr_osoite AS $$
DECLARE
  osa_ RECORD;
  aet INTEGER;
BEGIN
  SELECT tie,osa,ajorata,geom,ST_Distance(piste, geom) as d
    FROM tr_osan_ajorata
   WHERE geom IS NOT NULL AND
         ST_Distance(piste, geom) < threshold
   ORDER BY d ASC LIMIT 1
   INTO osa_;
  -- Jos osa löytyy, ota etäisyys
  IF osa_ IS NULL THEN
    RETURN NULL;
  ELSE
    RAISE NOTICE 'löytyi %', ST_AsText(osa_.geom);
    aet := CAST((ST_LineLocatePoint(osa_.geom, piste) * ST_Length(osa_.geom)) AS INTEGER);
    RETURN ROW(osa_.tie, osa_.osa, aet, 0, 0, ST_ClosestPoint(osa_.geom, piste)::GEOMETRY);
  END IF;
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
    RAISE EXCEPTION 'pisteelle ei löydy tietä';
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
  losa INTEGER;
  let INTEGER;
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
	 ST_Distance(apiste, a.geom) < threshold AND
         ST_Distance(bpiste, b.geom) < threshold
   ORDER BY d ASC LIMIT 1
   INTO r;
  IF r IS NULL THEN
    RETURN NULL;
  ELSE
    aosa := r.alkuosa;
    aet := CAST((ST_LineLocatePoint(r.alkuosa_geom, apiste) * ST_Length(r.alkuosa_geom)) AS INTEGER);
    losa := r.loppuosa;
    let := CAST((ST_LineLocatePoint(r.loppuosa_geom, bpiste) * ST_Length(r.loppuosa_geom)) AS INTEGER);
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

-- paivittaa tr-rutiinien käyttämät taulut
CREATE OR REPLACE FUNCTION paivita_tr_taulut() RETURNS VOID AS $$
DECLARE
BEGIN
  DELETE FROM tr_osien_pituudet;
  INSERT INTO tr_osien_pituudet
    SELECT tie, osa, ST_Length(geom) AS pituus
      FROM tr_osan_ajorata
     WHERE ajorata=1 AND geom IS NOT NULL
    ORDER BY tie,osa;
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
