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
  gtmp geometry;
  itmp INTEGER;
  alkuosatie INTEGER;
  aosa INTEGER;
  losa INTEGER;
  alkuet NUMERIC;
  loppuet NUMERIC;
  ap NUMERIC;
  bp NUMERIC;
BEGIN
  WITH aat AS (SELECT *            -- alkupisteen ympäristön lähellä olevat tieosuudet
		FROM tieverkko
		WHERE ST_DWithin(geometria, alkupiste, treshold)),
      beet AS (SELECT *            -- loppupisteen ympäristön lähellä olevat tieosuudet
		FROM tieverkko
		WHERE ST_DWithin(geometria, loppupiste, treshold)),
  -- unioni tieosista jotka ovat samalla tiellä
  yhteiset AS (SELECT aat.* FROM aat,beet WHERE aat.tie = beet.tie
		UNION
		SELECT beet.* FROM beet,aat WHERE aat.tie = beet.tie),
  -- alkupisteen lähin tieosa
  alkuos AS (SELECT *
		   FROM yhteiset
		   WHERE ST_DWithin(geometria, alkupiste, treshold)
		   ORDER BY ST_Length(ST_ShortestLine(geometria, alkupiste)) ASC
		   LIMIT 1),
  -- loppupisteen lähin tieosa
  loppuos AS (SELECT *
		   FROM yhteiset
		   WHERE ST_DWithin(geometria, loppupiste, treshold)
		   ORDER BY ST_Length(ST_ShortestLine(geometria, loppupiste)) ASC
		   LIMIT 1)
   SELECT alkuos.osa, loppuos.osa, alkuos.tie, ST_LineMerge(alkuos.geometria), ST_LineMerge(loppuos.geometria)
     INTO aosa, losa, alkuosatie, alkuosageo, loppuosageo 
   FROM alkuos, loppuos;

   IF alkuosageo IS NULL THEN
     RAISE EXCEPTION 'pisteille ei löydy lähintä yhteistä tietä';
   END IF;

   IF aosa>losa THEN
      gtmp := alkuosageo;
      alkuosageo := loppuosageo;
      loppuosageo := gtmp;

      itmp := aosa;
      aosa = losa;
      losa := itmp;

      gtmp := alkupiste;
      alkupiste := loppupiste;
      loppupiste := gtmp;
   END IF;
   
   -- väliin jäävien osien geometriat kerätään yhteen
   SELECT ST_Union(geometria) FROM tieverkko
     WHERE tie=alkuosatie
       AND osa>aosa
       AND osa<losa
     INTO kokonaiset;

   -- RAISE NOTICE 'alkuosageo %', ST_AsText(alkuosageo);
   -- RAISE NOTICE 'loppuosageo %', ST_AsText(loppuosageo);

   IF GeometryType(alkuosageo)='MULTILINESTRING' THEN
     alkuosageo := ST_GeometryN(alkuosageo, 1);
   END IF;

   IF GeometryType(loppuosageo)='MULTILINESTRING' THEN
     loppuosageo := ST_GeometryN(loppuosageo, 1);
   END IF;
   
   ap := ST_Line_Locate_Point(alkuosageo, alkupiste);
   bp := ST_Line_Locate_Point(loppuosageo, loppupiste);

   IF kokonaiset IS NULL THEN
        IF aosa=losa THEN
	   -- kokonaisia ei ole, aosa==losa, katkaise ainokainen geometria alku- ja loppupisteisiin
	   alkupatka := ST_Line_Substring(alkuosageo, LEAST(ap,bp), GREATEST(ap,bp));
           alkuet := ST_Length(ST_Line_Substring(alkuosageo, 0, ap));
           loppuet := ST_Length(ST_Line_Substring(alkuosageo, 0, bp));
           RETURN ROW(alkuosatie, aosa, alkuet::INTEGER, losa, loppuet::INTEGER, alkupatka);
        ELSE
           alkupatka := ST_Line_Substring(alkuosageo, ap, 1);
           loppupatka := ST_Line_Substring(loppuosageo, 0, bp);
           alkuet := ST_Length(ST_Line_Substring(alkuosageo, 0, ap));
           loppuet := ST_Length(loppupatka);
           RETURN ROW(alkuosatie, aosa, alkuet::INTEGER, losa, loppuet::INTEGER, ST_MakeLine(ARRAY[alkupatka,loppupatka]));
        END IF;
   ELSE
	-- ensimmäisen osan loppupätkä projektiopisteestä eteenpäin
	alkupatka := ST_Line_Substring(alkuosageo, ap, 1);
	-- loppuosan alkupätkä alusta projektiopisteeseen
	loppupatka := ST_Line_Substring(loppuosageo, 0, bp);

       -- alkuosan alkuetäisyys = alusta projektiopisteeseen
       alkuet := ST_Length(ST_Line_Substring(alkuosageo, 0, ap));
       -- loppuosan loppuetäisyys = loppupätkän alusta projektiopisteeseen
       loppuet := ST_Length(loppupatka);
       -- mergetään alkupätkä + väliin jäävät osat + loppupätkä = valittu tieosuus
       RETURN ROW(alkuosatie, aosa, alkuet::INTEGER, losa, loppuet::INTEGER, ST_MakeLine(ARRAY[alkupatka,ST_LineMerge(kokonaiset),loppupatka]));
   END IF;

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
