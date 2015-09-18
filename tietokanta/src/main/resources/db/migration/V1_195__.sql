-- Kuvaus: tierekisteriosoitteen haku yhdellä pisteellä, korjaus

CREATE MATERIALIZED VIEW tieverkko_paloina AS SELECT osoite3, tie, ajorata, osa, tiepiiri, (ST_Dump(geometria)).geom AS geom, (ST_Dump(geometria)).path[1] FROM tieverkko;

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
   SELECT SUM(ST_Length(geom)) 
     FROM tieverkko_paloina 
    WHERE tie=alkuosa.tie 
      AND osa=alkuosa.osa 
      AND ajorata=alkuosa.ajorata 
      AND path<alkuosa.path 
   INTO palojenpit;
   
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
  ap NUMERIC;
  bp NUMERIC;
BEGIN
  -- molempien päiden tierekisteriosoitteet
  atr := tierekisteriosoite_pisteelle(alkupiste, treshold);
  btr := tierekisteriosoite_pisteelle(loppupiste, treshold);

     -- valitaan se tie ja tienosaväli jota lähellä alku- ja loppupisteet ovat yhdessä
     WITH u AS (SELECT a.tie, 
                       a.osa AS aosa, 
                       b.osa AS bosa, 
                       a.ajorata AS ajorata
                FROM tieverkko_paloina a, 
                     tieverkko_paloina b 
               WHERE ST_DWithin(a.geom, alkupiste, treshold) 
                 AND ST_DWithin(b.geom, loppupiste, treshold) 
                 AND a.tie=b.tie 
                 AND a.ajorata=b.ajorata
               LIMIT 1)
  -- kootaan osien geometriat yhdeksi viivaksi
  SELECT ST_MakeLine(tv.geom ORDER BY osa) 
    FROM tieverkko_paloina tv, u 
   WHERE tv.tie=u.tie 
     AND tv.ajorata=u.ajorata
     AND tv.osa>=LEAST(u.aosa,u.bosa)
     AND tv.osa<=GREATEST(u.aosa,u.bosa)
   INTO reitti;

  IF reitti IS NULL THEN
     RAISE EXCEPTION 'pisteillä ei yhteistä tietä';
  END IF;
  
  -- projisoidaan alku- ja loppupisteet tälle viivalle ja leikataan viiva niiden mukaan alusta ja lopusta
  ap := ST_Line_Locate_Point(reitti, alkupiste);
  bp := ST_Line_Locate_Point(reitti, loppupiste);
  
  RETURN ROW(atr.tie, 
             atr.aosa, 
             atr.aet, 
             btr.aosa, 
             btr.aet, 
             ST_Line_Substring(reitti, LEAST(ap,bp), GREATEST(ap,bp)));
END;
$$ LANGUAGE plpgsql;
