-- Kuvaus: tierekisteriosoitteen haku yhdellä pisteellä, korjaus

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteille(
  alkupiste geometry, loppupiste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
  yhteiset tieverkko;
  alkuosageo geometry;
  loppuosageo geometry;
  alkupatka geometry;
  loppupatka geometry;
  kokonaiset geometry;
  alkuosatie INTEGER;
  aosa INTEGER;
  losa INTEGER;
  alkuet NUMERIC;
  loppuet NUMERIC;
BEGIN
  WITH aat AS (SELECT *            -- alkupisteen ympäristön pisteet
		FROM tieverkko
		WHERE ST_DWithin(geometria, alkupiste, treshold)),
      beet AS (SELECT *            -- loppupisteen ympäristön pisteet
		FROM tieverkko
		WHERE ST_DWithin(geometria, loppupiste, treshold)),
  -- unioni pisteistä jotka ovat samalla tiellä
  yhteiset AS (SELECT aat.* FROM aat,beet WHERE aat.tie = beet.tie
		UNION
		SELECT beet.* FROM beet,aat WHERE aat.tie = beet.tie),
  alkuos AS (SELECT *
		   FROM yhteiset
		   WHERE ST_DWithin(geometria, alkupiste, treshold)
		   ORDER BY ST_Length(ST_ShortestLine(geometria, alkupiste)) ASC
		   LIMIT 1),
  loppuos AS (SELECT *
		   FROM yhteiset
		   WHERE ST_DWithin(geometria, loppupiste, treshold)
		   ORDER BY ST_Length(ST_ShortestLine(geometria, loppupiste)) ASC
		   LIMIT 1)
   SELECT alkuos.osa, loppuos.osa, alkuos.tie, alkuos.geometria, loppuos.geometria 
     INTO aosa, losa, alkuosatie, alkuosageo, loppuosageo 
   FROM alkuos, loppuos;
   
   -- ensimmäisen osan loppupätkä projektiopisteestä eteenpäin
   SELECT ST_Line_Substring(alkuosageo, ST_Line_Locate_Point(ST_LineMerge(alkuosageo), alkupiste), 1) INTO alkupatka;
   -- loppuosan alkupätkä alusta projektiopisteeseen
   SELECT ST_Line_Substring(loppuosageo, 0, ST_Line_Locate_Point(ST_LineMerge(loppuosageo), loppupiste)) INTO loppupatka;

   -- väliin jäävien osien geometriat kerätään yhteen
   SELECT ST_Union(geometria) FROM tieverkko
     WHERE tie=alkuosatie
       AND osa>aosa
       AND osa<losa
     INTO kokonaiset;

   -- alkuosan alkuetäisyys = alusta projektiopisteeseen
   SELECT ST_Length(ST_Line_Substring(alkuosageo, 0, ST_Line_Locate_Point(ST_LineMerge(alkuosageo), alkupiste))) INTO alkuet;
   -- loppuosan loppuetäisyys = loppupätkän alusta projektiopisteeseen
   SELECT ST_Length(loppupatka) INTO loppuet;

   -- mergetään alkupätkä + väliin jäävät osat + loppupätkä = valittu tieosuus
   RETURN ROW(alkuosatie, aosa, alkuet::INTEGER, losa, loppuet::INTEGER, ST_MakeLine(ARRAY[alkupatka,ST_LineMerge(kokonaiset),loppupatka]));
  
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteelle(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
   alkuosa RECORD;
   alkuet NUMERIC;
BEGIN
   SELECT osoite3, tie, ajorata, osa, tiepiiri, geometria
     FROM tieverkko
     WHERE ST_DWithin(geometria, piste, treshold)
     ORDER BY ST_Length(ST_ShortestLine(geometria, piste)) ASC
     LIMIT 1
   INTO alkuosa;

   IF alkuosa IS NULL THEN
     RAISE EXCEPTION 'pisteelle ei löydy tietä';
   END IF;

   SELECT ST_Length(ST_Line_Substring(alkuosa.geometria, 0, ST_Line_Locate_Point(ST_LineMerge(alkuosa.geometria), piste))) INTO alkuet;

   RETURN ROW(alkuosa.tie, alkuosa.osa, alkuet::INTEGER, 0, 0, NULL::geometry);
END;
$$ LANGUAGE plpgsql;
