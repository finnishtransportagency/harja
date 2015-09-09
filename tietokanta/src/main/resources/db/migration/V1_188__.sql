CREATE TYPE tr_osoite AS (tie INTEGER, aosa INTEGER, aet INTEGER, losa INTEGER, let INTEGER);

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteille(
  alkupiste geometry, loppupiste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
   alkuosa RECORD;
   loppuosa RECORD;
   alkuet NUMERIC;
   loppuet NUMERIC;
BEGIN
   SELECT osoite3, tie, ajorata, osa, tiepiiri, hoitoluokka, geometria
     FROM tieverkko
     WHERE ST_DWithin(geometria, alkupiste, treshold)
     ORDER BY ST_Length(ST_ShortestLine(geometria, alkupiste)) ASC
     LIMIT 1
   INTO alkuosa;

   IF alkuosa IS NULL THEN
     RAISE EXCEPTION 'alkupisteelle ei löydy tietä';
   END IF;

   SELECT osoite3, tie, ajorata, osa, tiepiiri, hoitoluokka, geometria
     FROM tieverkko
     WHERE ST_DWithin(geometria, loppupiste, treshold)
     ORDER BY ST_Length(ST_ShortestLine(geometria, loppupiste)) ASC
     LIMIT 1
   INTO loppuosa;

   IF loppuosa IS NULL THEN
     RAISE EXCEPTION 'alkupisteelle ei löydy tietä';
   END IF;
  
   IF alkuosa.tie != loppuosa.tie THEN
     RAISE EXCEPTION 'alku- ja loppupisteiden tulee sijaita samalla tiellä';
   END IF;

   SELECT ST_Length(ST_Line_Substring(alkuosa.geometria, 0, ST_Line_Locate_Point(ST_LineMerge(alkuosa.geometria), alkupiste))) INTO alkuet;
   SELECT ST_Length(ST_Line_Substring(loppuosa.geometria, 0, ST_Line_Locate_Point(ST_LineMerge(loppuosa.geometria), loppupiste))) INTO loppuet;

   RETURN ROW(alkuosa.tie, alkuosa.osa, alkuet, loppuosa.osa, loppuet)::tr_osoite;
END;
$$ LANGUAGE plpgsql;
