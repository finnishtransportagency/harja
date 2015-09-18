-- Kuvaus: tierekisteriosoitteen haku yhdellä pisteellä, korjaus

CREATE MATERIALIZED VIEW tieverkko_paloina AS SELECT osoite3, tie, ajorata, osa, tiepiiri, (ST_Dump(geometria)).geom AS geom, (ST_Dump(geometria)).path[1] FROM tieverkko
CREATE INDEX tieverkko_paloina_geom_index ON tieverkko_paloina USING GIST (geom);

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteelle(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
   alkuosa RECORD;
   alkuet NUMERIC;
   palojenpit NUMERIC;
BEGIN
   SELECT osoite3, tie, ajorata, osa, tiepiiri, path, geom
     FROM tieverkko_paloina
     WHERE ST_DWithin(geom, piste, treshold)
     ORDER BY ST_Length(ST_ShortestLine(geom, piste)) ASC
     LIMIT 1
   INTO alkuosa;

   IF alkuosa IS NULL THEN
     RAISE EXCEPTION 'pisteelle ei löydy tietä';
   END IF;
   
   SELECT ST_Length(ST_Line_Substring(alkuosa.geom, 0, ST_Line_Locate_Point(alkuosa.geom, piste))) INTO alkuet;
   SELECT SUM(ST_Length(geom)) FROM tieverkko_paloina WHERE tie=alkuosa.tie AND osa=alkuosa.osa AND ajorata=alkuosa.ajorata AND path<alkuosa.path INTO palojenpit;
   IF palojenpit IS NULL THEN
     palojenpit := 0;
   END IF;
   RETURN ROW(alkuosa.tie, alkuosa.osa, (palojenpit+alkuet)::INTEGER, 0, 0, NULL::geometry);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteille(
  alkupiste geometry, loppupiste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
  tmp tieverkko_paloina[];
  reitti geometry;
  atr tr_osoite;
  btr tr_osoite;
BEGIN
     WITH u AS (SELECT a.tie, a.osa AS aosa, b.osa AS bosa FROM tieverkko_paloina a, tieverkko_paloina b 
                 WHERE ST_DWithin(a.geom, alkupiste, treshold) AND ST_DWithin(b.geom, loppupiste, treshold) AND a.tie=b.tie LIMIT 1)
  SELECT ST_MakeLine(tv.geom) FROM tieverkko_paloina tv, u WHERE tv.tie=u.tie AND tv.osa>=u.aosa AND tv.osa<=u.bosa
    INTO reitti;
  
  atr := tierekisteriosoite_pisteelle(alkupiste, treshold);
  btr := tierekisteriosoite_pisteelle(loppupiste, treshold);

  RETURN ROW(atr.tie, atr.aosa, atr.aet, btr.aosa, btr.aet, ST_Line_Substring(reitti, ST_Line_Locate_Point(reitti, alkupiste), ST_Line_Locate_Point(reitti, loppupiste)));
END;
$$ LANGUAGE plpgsql;
