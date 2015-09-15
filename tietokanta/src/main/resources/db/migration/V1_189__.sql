-- Kuvaus: tierekisteriosoitteen haku tieosoiteverkosta

CREATE TYPE tr_osoite AS (tie INTEGER, aosa INTEGER, aet INTEGER, losa INTEGER, let INTEGER, geometria geometry);

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteille(
  alkupiste geometry, loppupiste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
   alkuosa RECORD;
   loppuosa RECORD;
   alkuet NUMERIC;
   loppuet NUMERIC;
   alkupatka geometry;
   loppupatka geometry;
   kokonaiset geometry;
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
     RAISE EXCEPTION 'loppupisteelle ei löydy tietä';
   END IF;
  
   IF alkuosa.tie != loppuosa.tie THEN
     RAISE EXCEPTION 'alku- ja loppupisteiden tulee sijaita samalla tiellä';
   END IF;

   -- ensimmäisen osan loppupätkä projektiopisteestä eteenpäin
   SELECT ST_Line_Substring(alkuosa.geometria, ST_Line_Locate_Point(ST_LineMerge(alkuosa.geometria), alkupiste), 1) INTO alkupatka;
   -- loppuosan alkupätkä alusta projektiopisteeseen
   SELECT ST_Line_Substring(loppuosa.geometria, 0, ST_Line_Locate_Point(ST_LineMerge(loppuosa.geometria), loppupiste)) INTO loppupatka;

   -- väliin jäävien osien geometriat kerätään yhteen
   SELECT ST_Union(geometria) FROM tieverkko
     WHERE tie=alkuosa.tie
       AND osa>alkuosa.osa
       AND osa<loppuosa.osa
     INTO kokonaiset;

   -- alkuosan alkuetäisyys = alusta projektiopisteeseen
   SELECT ST_Length(ST_Line_Substring(alkuosa.geometria, 0, ST_Line_Locate_Point(ST_LineMerge(alkuosa.geometria), alkupiste))) INTO alkuet;
   -- loppuosan loppuetäisyys = loppupätkän alusta projektiopisteeseen
   SELECT ST_Length(loppupatka) INTO loppuet;

   -- mergetään alkupätkä + väliin jäävät osat + loppupätkä = valittu tieosuus
   RETURN ROW(alkuosa.tie, alkuosa.osa, alkuet::INTEGER, loppuosa.osa, loppuet::INTEGER, ST_MakeLine(ARRAY[alkupatka,ST_LineMerge(kokonaiset),loppupatka]));
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
   SELECT osoite3, tie, ajorata, osa, tiepiiri, hoitoluokka, geometria
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
