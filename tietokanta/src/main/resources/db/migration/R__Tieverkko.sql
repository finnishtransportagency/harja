
-- paivittaa tr-rutiinien käyttämät taulut
CREATE OR REPLACE FUNCTION paivita_tr_taulut() RETURNS VOID AS $$
BEGIN
  DELETE FROM tieverkko_geom;
  INSERT INTO tieverkko_geom SELECT tie, ST_LineMerge(ST_Union(geometria)), 0::BIT FROM tieverkko WHERE (ajorata=0 OR ajorata=1) GROUP BY tie;
  INSERT INTO tieverkko_geom SELECT tie, ST_LineMerge(ST_Union(geometria)), 1::BIT FROM tieverkko WHERE (ajorata=0 OR ajorata=2) GROUP BY tie;

  DELETE FROM tr_osien_pituudet;
  INSERT INTO tr_osien_pituudet SELECT tie, osa, SUM(tr_pituus) AS pituus
                                  FROM tieverkko
				 WHERE (ajorata=0 OR ajorata=1)
				 GROUP BY tie, osa ORDER BY tie,osa;

END;
$$ LANGUAGE plpgsql;

-- kaantää (multi)linestringin toisinpäin, myös multilinestringin viivajärjestyksen
CREATE OR REPLACE FUNCTION kaanna_viiva(viiva geometry) RETURNS geometry AS $$
DECLARE
  tmp geometry;
BEGIN
  IF GeometryType(viiva)='MULTILINESTRING' THEN
    SELECT ST_Collect(ST_Reverse(g.geom))
      FROM (SELECT geom FROM ST_Dump(viiva) ORDER BY path[1] DESC) AS g INTO tmp;
      RETURN tmp;
  ELSE
    RETURN ST_Reverse(viiva);
  END IF;
END;
$$ LANGUAGE plpgsql;

-- palauttaa 0 jos tr-osoite on kasvava, 1 jos laskeva
CREATE OR REPLACE FUNCTION tr_osoitteen_suunta(aosa INTEGER, aet INTEGER, losa INTEGER, let INTEGER) RETURNS BIT AS $$
BEGIN
   RETURN (CASE
     WHEN (aosa=losa AND aet<=let) THEN 0
     WHEN (aosa=losa AND aet>let) THEN 1
     WHEN aosa<losa THEN 0
     WHEN aosa>losa THEN 1
   END);
END;
$$ LANGUAGE plpgsql;

-- laskee matkan tien alusta kun osa ja etäisyys osan alusta on annettu
CREATE OR REPLACE FUNCTION etaisyys_alusta(tie_ INTEGER, osa_ INTEGER, et INTEGER) RETURNS INTEGER AS $$
DECLARE
  tmp INTEGER;
BEGIN
  SELECT SUM(pituus) FROM tr_osien_pituudet WHERE tie=tie_ AND osa<osa_ INTO tmp;
  IF tmp IS NULL THEN
    RETURN et;
  ELSE
    RETURN et+tmp;
  END IF;
END;
$$ LANGUAGE plpgsql;

-- laskee tien kokonaispituuden
CREATE OR REPLACE FUNCTION tien_kokonaispituus(tie_ INTEGER) RETURNS INTEGER AS $$
DECLARE
  tmp INTEGER;
BEGIN
  SELECT SUM(pituus) FROM tr_osien_pituudet WHERE tie=tie_ INTO tmp;
  RETURN tmp;
END;
$$ LANGUAGE plpgsql;

-- palauttaa tierekisteriosoitteelle geometrian
CREATE OR REPLACE FUNCTION tr_osoitteelle_viiva2(tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER) RETURNS geometry AS $$
DECLARE
  tmp geometry;
  kok_pituus INTEGER;
  apituus INTEGER;
  bpituus INTEGER;
  suunta_ BIT;
BEGIN
  suunta_ := tr_osoitteen_suunta(aosa_,aet_,losa_,let_);
  SELECT geom FROM tieverkko_geom WHERE tie=tie_ AND suunta=suunta_ INTO tmp;
  kok_pituus := tien_kokonaispituus(tie_);
  apituus := etaisyys_alusta(tie_, aosa_, aet_);
  bpituus := etaisyys_alusta(tie_, losa_, let_);
  IF suunta_=1::bit THEN
     RETURN kaanna_viiva(ST_LineSubstring(tmp, bpituus::FLOAT/kok_pituus::FLOAT, apituus::FLOAT/kok_pituus::FLOAT));
  ELSE
     RETURN ST_LineSubstring(tmp, apituus::FLOAT/kok_pituus::FLOAT, bpituus::FLOAT/kok_pituus::FLOAT);
  END IF;
END;
$$ LANGUAGE plpgsql;

-- laskee geometrialle projisoidun pisteen etäisyyden geometrian alusta
CREATE OR REPLACE FUNCTION projektion_etaisyys(apiste geometry, viiva geometry) RETURNS FLOAT AS $$
DECLARE
  tmp geometry;
  etaisyys FLOAT;
  pit FLOAT;
BEGIN
  etaisyys := 0;
  IF GeometryType(viiva)='MULTILINESTRING' THEN
     FOR i IN 1..ST_NumGeometries(viiva) LOOP
        tmp := ST_GeometryN(viiva, i);
        pit := ST_LineLocatePoint(tmp, apiste);
        IF pit=1 OR pit=0 THEN
           etaisyys := etaisyys + ST_Length(tmp);
        ELSE
           etaisyys := etaisyys + ST_Length(ST_LineSubstring(tmp, 0, pit));
           RETURN (etaisyys::FLOAT / ST_Length(viiva)::FLOAT);
        END IF;
     END LOOP;
     RETURN etaisyys::FLOAT / ST_Length(viiva)::FLOAT;
  ELSE
     etaisyys := ST_Length(ST_LineSubstring(viiva, 0, ST_LineLocatePoint(viiva, apiste)));
     RETURN etaisyys::FLOAT / ST_Length(viiva)::FLOAT;
  END IF;
END;
$$ LANGUAGE plpgsql;

-- laskee annetun etäisyyden osan (ja etäisyyden ko. osan alusta)
CREATE OR REPLACE FUNCTION etaisyyden_osa(tie_ INTEGER, etaisyys INTEGER) RETURNS RECORD AS $$
DECLARE
  tmp RECORD;
BEGIN
    SELECT k.osa, CAST(k.p AS INTEGER) FROM (SELECT osa, sum(pituus) OVER (PARTITION BY tie ORDER BY osa) AS p 
                              FROM tr_osien_pituudet 
                             WHERE tie=tie_) AS k 
     WHERE k.p<etaisyys
     ORDER BY osa DESC 
     LIMIT 1
     INTO tmp;
     
     IF tmp.osa IS NULL THEN
       SELECT MIN(osa) AS osa, 0::INTEGER AS p FROM tr_osien_pituudet WHERE tie=tie_ INTO tmp;
       RETURN tmp;
     ELSE
       SELECT osa AS osa, tmp.p AS p FROM tr_osien_pituudet WHERE osa>tmp.osa LIMIT 1 INTO tmp;
       RETURN tmp;
     END IF;
END;
$$ LANGUAGE plpgsql;

DROP FUNCTION IF EXISTS yrita_tierekisteriosoite_pisteille(geometry,geometry,integer);

CREATE OR REPLACE FUNCTION yrita_tierekisteriosoite_pisteille(apiste geometry, bpiste geometry, treshold INTEGER) RETURNS tr_osoite AS $$
DECLARE
  tie_ RECORD;
  otie_ RECORD;
  alkuet FLOAT;
  loppuet FLOAT;
  alkuet2 INTEGER;
  loppuet2 INTEGER;
  tmp RECORD;
  tmp2 RECORD;
BEGIN
  SELECT a.tie, a.geom
    FROM tieverkko_geom a, tieverkko_geom b
    WHERE ST_DWithin(a.geom, apiste, treshold)
      AND ST_DWithin(b.geom, bpiste, treshold)
      AND a.tie=b.tie
      AND a.suunta=0::bit AND b.suunta=0::bit
    ORDER BY ST_Length(ST_ShortestLine(apiste, a.geom)) + ST_Length(ST_ShortestLine(bpiste, b.geom))
    LIMIT 1
    INTO tie_;

  IF tie_.tie IS NULL THEN
     RETURN NULL;
  END IF;

  alkuet := projektion_etaisyys(apiste, tie_.geom);
  loppuet := projektion_etaisyys(bpiste, tie_.geom);

  IF alkuet<loppuet THEN
    SELECT geom FROM tieverkko_geom WHERE tie=tie_.tie AND suunta=0::bit INTO otie_;
    
    alkuet := projektion_etaisyys(apiste, otie_.geom);
    loppuet := projektion_etaisyys(bpiste, otie_.geom);

    alkuet2 := CAST(alkuet * ST_Length(otie_.geom) AS INTEGER);
    loppuet2 := CAST(loppuet * ST_Length(otie_.geom) AS INTEGER);
    
    tmp := etaisyyden_osa(tie_.tie, alkuet2);
    tmp2 := etaisyyden_osa(tie_.tie, loppuet2);
    
    RETURN ROW(tie_.tie,tmp.osa,CAST(alkuet2-tmp.p AS INTEGER),tmp2.osa,CAST(loppuet2-tmp2.p AS INTEGER),ST_LineSubstring(otie_.geom, alkuet, loppuet));
  ELSE
    SELECT geom FROM tieverkko_geom WHERE tie=tie_.tie AND suunta=1::bit INTO otie_;
    
    alkuet := projektion_etaisyys(apiste, otie_.geom);
    loppuet := projektion_etaisyys(bpiste, otie_.geom);

    alkuet2 := CAST(alkuet * ST_Length(otie_.geom) AS INTEGER);
    loppuet2 := CAST(loppuet * ST_Length(otie_.geom) AS INTEGER);
    
    tmp := etaisyyden_osa(tie_.tie, alkuet2);
    tmp2 := etaisyyden_osa(tie_.tie, loppuet2);
    
    RETURN ROW(tie_.tie,tmp.osa,CAST(alkuet2-tmp.p AS INTEGER),tmp2.osa,CAST(loppuet2-tmp2.p AS INTEGER),kaanna_viiva(ST_LineSubstring(otie_.geom, loppuet, alkuet)));
  END IF;  
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

CREATE OR REPLACE FUNCTION yrita_tierekisteriosoite_pisteelle(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite AS $$
DECLARE
  tie_ RECORD;
  tmp RECORD;
  alkuet FLOAT;
  alkuet2 INTEGER;
BEGIN
  SELECT tie, geom FROM tieverkko_geom
    WHERE ST_DWithin(geom, piste, treshold)
    ORDER BY ST_Length(ST_ShortestLine(piste, geom))
    LIMIT 1
    INTO tie_;

  IF tie_.tie IS NULL THEN
     RETURN NULL;
  END IF;

  alkuet := projektion_etaisyys(piste, tie_.geom);
  alkuet2 := CAST(alkuet * ST_Length(tie_.geom) AS INTEGER);
  tmp := etaisyyden_osa(tie_.tie, alkuet2);
  RETURN ROW(tie_.tie, tmp.osa, CAST(alkuet2-tmp.p AS INTEGER), 0, 0, ST_ClosestPoint(tie_.geom, piste)::geometry);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteelle(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
  osoite tr_osoite;
BEGIN
  osoite := yrita_tierekisteriosoite_pisteelle(piste, treshold);
  IF osoite IS NULL THEN
    RAISE EXCEPTION 'pisteelle ei löydy tietä';
  ELSE
    RETURN osoite;
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
    osoite := yrita_tierekisteriosoite_pisteille(alkupiste, loppupiste, treshold);
    IF osoite IS NULL THEN
      RAISE EXCEPTION 'pisteillä ei yhteistä tietä';
    END IF;
    RETURN osoite;
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
	            FROM yrita_tierekisteriosoite_pisteille(alku, loppu, threshold) ytp));
    i := i + 1;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

SELECT paivita_tr_taulut();
