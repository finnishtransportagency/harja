CREATE OR REPLACE FUNCTION eka_piste(g geometry) RETURNS geometry AS $$
BEGIN
  IF GeometryType(g)='MULTILINESTRING' THEN
    RETURN ST_StartPoint(ST_GeometryN(g,1));
  ELSE
    RETURN ST_StartPoint(g);
  END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION vika_piste(g geometry) RETURNS geometry AS $$
BEGIN
  IF GeometryType(g)='MULTILINESTRING' THEN
    RETURN ST_EndPoint(ST_GeometryN(g,ST_NumGeometries(g)));
  ELSE
    RETURN ST_EndPoint(g);
  END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION etsi_jatkopatka(nykyinen INTEGER, viiva geometry, jatkopatkat geometry) RETURNS geometry AS $$
DECLARE
  tmp geometry;
  minimi geometry;
  etaisyys FLOAT;
BEGIN
  etaisyys := 'Infinity' :: FLOAT;
  FOR i IN 1..ST_NumGeometries(jatkopatkat) LOOP
     IF i!=nykyinen THEN
       tmp := ST_GeometryN(jatkopatkat, i);
       --IF ST_Distance(vika_piste(viiva), eka_piste(tmp))<etaisyys THEN
       IF vika_piste(viiva)=eka_piste(tmp) THEN
         etaisyys := ST_Distance(vika_piste(viiva), eka_piste(tmp));
         minimi := tmp;
       END IF;
     END IF;
  END LOOP;
  --RAISE NOTICE 'jatkopatka nykyiselle % on %', nykyinen, st_astext(viiva);
  RETURN ST_LineMerge(ST_Collect(viiva, minimi));
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION yhdista_viivat_jarjestyksessa(viiva geometry) RETURNS geometry AS $$
DECLARE
  jarjestetty geometry;
BEGIN
    IF GeometryType(viiva)='LINESTRING' THEN
       RETURN viiva;
    END IF;
    -- Yhdistä viivat alkaen ensimmäisestä
    jarjestetty := ST_GeometryN(viiva,1);
    FOR i IN 1..ST_NumGeometries(viiva) LOOP
        jarjestetty := etsi_jatkopatka(i, jarjestetty, viiva);
    END LOOP;
  RETURN jarjestetty;
END;
$$ LANGUAGE plpgsql;

--
-- HAE solmukohdat
-- SELECT solmu, count(*) FROM (select st_startpoint(st_geometryn(geometria,1)) as solmu from tieverkko UNION ALL select st_endpoint(st_geometryn(geometria,st_numgeometries(geometria))) as solmu from tieverkko) s GROUP BY solmu HAVING COUNT(*) > 1;
-- koska osien loppu/alku jakavat saman pisteen, ovat ne solmukohtia
-- niiden perusteella pitäisi päätellä, mikä on ensimmäinen geometriapala
-- tälle tieosalle
--
-- Tällä pitää tehdä päätös onko se 2-ajorataisten joukon ensimmäinen
-- vai 1-ajorataisten
--
-- TAI SITTENKIN: alku/loppu ovat ne, joilla ei ole tämän osan geometrioissa samaa muuta pistettä
-- ota 1. piste 0-ajorataa
-- ota 1. piste (1|2)-ajorataa
-- ota kaikki pisteet
-- valitse aloituspisteeksi eka pisteistä se, joka ei ole jaettu minkään muun
-- pisteen kanssa

-- HOX:::: Merkkaa 9h to tunneiksi, tr geometrian selvittely


CREATE OR REPLACE FUNCTION keraa_geometriat(tie_ INTEGER, osa_ INTEGER, ajorata_ INTEGER) RETURNS geometry AS $$
DECLARE
  g geometry;
  alkuajorata INTEGER;
BEGIN
  SELECT a.alkuajorata
    FROM tr_osan_alkuajorata a WHERE a.tie=tie_ AND a.osa=osa_ AND a.ajorata=ajorata_
    INTO alkuajorata;
  -- Yhdistetään 0-ajorata sekä valittu ajorata sen mukaan kummalla tieosa alkaa
  IF alkuajorata = 0 THEN
    SELECT yhdista_viivat_jarjestyksessa(st_collect((g.f).geom))
      FROM (SELECT st_dump(geometria) AS f
              FROM tieverkko
             WHERE tie=tie_ AND osa=osa_ AND ajorata=0
            UNION ALL
            SELECT st_dump(geometria) AS f
 	      FROM tieverkko
 	     WHERE tie=tie_ AND osa=osa_ AND ajorata=ajorata_) AS g
      INTO g;
  ELSIF alkuajorata = ajorata_ THEN
    SELECT yhdista_viivat_jarjestyksessa(st_collect((g.f).geom))
      FROM (SELECT st_dump(geometria) AS f
              FROM tieverkko
             WHERE tie=tie_ AND osa=osa_ AND ajorata=ajorata_
            UNION ALL
            SELECT st_dump(geometria) AS f
 	      FROM tieverkko
 	     WHERE tie=tie_ AND osa=osa_ AND ajorata=0) AS g
      INTO g;
  ELSE
    RAISE NOTICE 'Tie %, osa %, ajorata %: ei tietoa kummalla ajoradalla osa alkaa', tie_, osa_, ajorata_;
  END IF;
  RETURN g;
END;
$$ LANGUAGE plpgsql;


-- VANHA VERSION
--CREATE OR REPLACE FUNCTION keraa_geometriat(tie_ INTEGER, osa_ INTEGER, ajorata_ INTEGER) RETURNS geometry AS $$
--DECLARE
--  g geometry;
--BEGIN
--  SELECT yhdista_viivat_jarjestyksessa(st_collect((g.f).geom))
--    FROM (SELECT st_dump(geometria) AS f
--            FROM tieverkko
--           WHERE tie=tie_ AND osa=osa_ AND ajorata=0
--          UNION ALL
--           SELECT st_dump(geometria) AS f
--	    FROM tieverkko
--	   WHERE tie=tie_ AND osa=osa_ AND ajorata=ajorata_) AS g
--	    INTO g;
--  RETURN g;
--END;
--$$ LANGUAGE plpgsql;



-- paivittaa tr-rutiinien käyttämät taulut
CREATE OR REPLACE FUNCTION paivita_tr_taulut() RETURNS VOID AS $$
DECLARE
BEGIN
  DELETE FROM tieverkko_geom;

  INSERT INTO tieverkko_geom SELECT g.tie,st_linemerge(st_union(keraa_geometriat(tie, osa, 1) ORDER BY osa)),0::bit FROM
    (SELECT DISTINCT tie,osa FROM tieverkko ORDER BY tie,osa) AS g GROUP BY g.tie;
  INSERT INTO tieverkko_geom SELECT g.tie,st_linemerge(st_union(keraa_geometriat(tie, osa, 2) ORDER BY osa)),1::bit FROM
    (SELECT DISTINCT tie,osa FROM tieverkko ORDER BY tie,osa) AS g GROUP BY g.tie;

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
  kok_pituus FLOAT;
  apituus FLOAT;
  bpituus FLOAT;
  apituus_clamp FLOAT;
  bpituus_clamp FLOAT;
  suunta_ BIT;
BEGIN
  suunta_ := tr_osoitteen_suunta(aosa_,aet_,losa_,let_);
  SELECT geom FROM tieverkko_geom WHERE tie=tie_ AND suunta=suunta_ INTO tmp;
  kok_pituus := tien_kokonaispituus(tie_);
  apituus := etaisyys_alusta(tie_, aosa_, aet_);
  bpituus := etaisyys_alusta(tie_, losa_, let_);
  apituus_clamp := LEAST(1, apituus::FLOAT/kok_pituus::FLOAT);
  bpituus_clamp := LEAST(1, bpituus::FLOAT/kok_pituus::FLOAT);
  IF suunta_=1::bit THEN
     tmp := kaanna_viiva(ST_LineSubstring(tmp, bpituus_clamp, apituus_clamp));
  ELSE
     tmp := ST_LineSubstring(tmp, apituus_clamp, bpituus_clamp);
  END IF;
  IF aosa_=losa_ AND aet_=let_ THEN
    -- tässä tapauksessa tulee geometrycollection, puretaan sen ainoa piste ulos
    RETURN ST_GeometryN(tmp, 1);
  ELSE
    RETURN tmp;
  END IF;
END;
$$ LANGUAGE plpgsql;

-- Hae annetun tien osan pituus
CREATE OR REPLACE FUNCTION tr_osan_pituus(tie_ INTEGER, osa_ INTEGER) RETURNS INTEGER AS $$
  SELECT pituus FROM tr_osien_pituudet WHERE tie=tie_ AND osa=osa_;
$$ LANGUAGE SQL IMMUTABLE;

-- Hae annetun tien osan geometria
CREATE OR REPLACE FUNCTION tr_osan_geometria(tie_ INTEGER, osa_ INTEGER, ajorata_ INTEGER) RETURNS GEOMETRY AS $$
 SELECT geometria FROM tieverkko WHERE tie=tie_ AND osa=osa_ AND ajorata=0;
 --FIXME:(ajorata=ajorata_ OR ajorata=0);
$$ LANGUAGE SQL IMMUTABLE;

-- Tatun uusi yritys tieosista
CREATE OR REPLACE FUNCTION tr_osoitteelle_viiva3(
    tie_ INTEGER,
    aosa_ INTEGER, aet_ INTEGER,
    losa_ INTEGER, let_ INTEGER) RETURNS geometry AS $$
DECLARE
  osan_pituus FLOAT;
  ajorata INTEGER; -- 1 on oikea ajorata tien kasvusuuntaan, 2 on vasen
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
  -- st_linemerge(st_collect(viiva1,viiva2))
BEGIN
  tulos := ARRAY[]::GEOMETRY[];
  -- Päätellään kumpaa ajorataa ollaan menossa
  -- Jos TR-osoite on kasvava, mennään oikeaa kaistaa (1)
  -- muuten mennään vasenta (2).
  ajorata := 1;
  IF (aosa_ > losa_ OR (aosa_ = losa_ AND aet_ > let_)) THEN
    ajorata := 2;
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
  RAISE NOTICE 'Haetaan geometria tie %, ajorata %', tie_, ajorata;
  tulos := NULL;
  FOR osa_ IN aosa..losa LOOP
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
    --
    -- Otetaan osan geometriaviivasta e1 -- e2 pätkä
    IF ajorata = 1 THEN
      SELECT oikea FROM tr_osan_ajorata toa WHERE toa.tie=tie_ AND toa.osa=osa_ INTO osan_geometria;
    ELSE
      SELECT vasen FROM tr_osan_ajorata toa WHERE toa.tie=tie_ AND toa.osa=osa_ INTO osan_geometria;
    END IF;
    osan_pituus := st_length(osan_geometria);
    osan_patka := ST_LineSubstring(osan_geometria, LEAST(1,e1/osan_pituus), LEAST(1,e2/osan_pituus));
    tulos := tulos || osan_patka;
  END LOOP;
  RETURN ST_Collect(tulos);
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
           RETURN LEAST(1, etaisyys::FLOAT / ST_Length(viiva)::FLOAT);
        END IF;
     END LOOP;
     RETURN LEAST(1, etaisyys::FLOAT / ST_Length(viiva)::FLOAT);
  ELSE
     etaisyys := ST_Length(ST_LineSubstring(viiva, 0, ST_LineLocatePoint(viiva, apiste)));
     RETURN LEAST(1, etaisyys::FLOAT / ST_Length(viiva)::FLOAT);
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
       SELECT osa AS osa, tmp.p AS p FROM tr_osien_pituudet WHERE osa>tmp.osa ORDER BY osa LIMIT 1 INTO tmp;
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
      AND a.suunta=b.suunta
    ORDER BY ST_Length(ST_ShortestLine(apiste, a.geom)) + ST_Length(ST_ShortestLine(bpiste, b.geom))
    LIMIT 1
    INTO tie_;

  IF tie_.tie IS NULL THEN
     RETURN NULL;
  END IF;

  -- rampit on aineistossa ryhmitelty osien mukaan joten käsitellään ne eri tavalla!
  IF tie_.tie>=20000 AND tie_.tie<=29999 THEN
    SELECT a.tie, a.osa, a.geometria AS geom
      FROM tieverkko a, tieverkko b
      WHERE ST_DWithin(a.geometria, apiste, treshold)
        AND ST_DWithin(b.geometria, bpiste, treshold)
        AND a.tie=b.tie
      ORDER BY ST_Length(ST_ShortestLine(apiste, a.geometria)) + ST_Length(ST_ShortestLine(bpiste, b.geometria))
      LIMIT 1
      INTO tie_;

    IF tie_.tie IS NULL THEN
      RETURN NULL;
    END IF;

    alkuet := projektion_etaisyys(apiste, tie_.geom);
    loppuet := projektion_etaisyys(bpiste, tie_.geom);

    alkuet2 := CAST(alkuet * ST_Length(tie_.geom) AS INTEGER);
    loppuet2 := CAST(loppuet * ST_Length(tie_.geom) AS INTEGER);

    IF alkuet<loppuet THEN
       RETURN ROW(tie_.tie, tie_.osa, alkuet2, tie_.osa, loppuet2, ST_LineSubstring(tie_.geom, alkuet, loppuet));
    ELSE
       RETURN ROW(tie_.tie, tie_.osa, alkuet2, tie_.osa, loppuet2, kaanna_viiva(ST_LineSubstring(tie_.geom, loppuet, alkuet)));
    END IF;
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

    alkuet := projektion_etaisyys(bpiste, otie_.geom);
    loppuet := projektion_etaisyys(apiste, otie_.geom);

    alkuet2 := CAST(alkuet * ST_Length(otie_.geom) AS INTEGER);
    loppuet2 := CAST(loppuet * ST_Length(otie_.geom) AS INTEGER);

    tmp := etaisyyden_osa(tie_.tie, alkuet2);
    tmp2 := etaisyyden_osa(tie_.tie, loppuet2);

    RETURN ROW(tie_.tie,tmp.osa,CAST(alkuet2-tmp.p AS INTEGER),tmp2.osa,CAST(loppuet2-tmp2.p AS INTEGER),kaanna_viiva(ST_LineSubstring(otie_.geom, alkuet, loppuet)));
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

  -- ramppi, käsitellää eri tavalla
  IF tie_.tie>=20000 AND tie_.tie<=29999 THEN
    SELECT tie, osa, geometria AS geom FROM tieverkko
      WHERE ST_DWithin(geometria, piste, treshold)
      ORDER BY ST_Length(ST_ShortestLine(piste, geometria))
      LIMIT 1
      INTO tie_;

    alkuet := projektion_etaisyys(piste, tie_.geom);
    alkuet2 := CAST(alkuet * ST_Length(tie_.geom) AS INTEGER);
    RETURN ROW(tie_.tie, tie_.osa, alkuet2, 0, 0, ST_ClosestPoint(tie_.geom, piste)::geometry);

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

CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_piste(tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER) RETURNS geometry AS $$
BEGIN
  RETURN tierekisteriosoitteelle_viiva(tie_, aosa_, aet_, aosa_, aet_);
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
