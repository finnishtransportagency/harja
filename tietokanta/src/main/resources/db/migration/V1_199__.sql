-- Kuvaus: tierekisteriosoitteen haku yhdellä pisteellä, korjaus

CREATE MATERIALIZED VIEW tieverkko_paloina AS SELECT osoite3, tie, ajorata, osa, tiepiiri, (ST_Dump(geometria)).geom AS geom, (ST_Dump(geometria)).path[1] FROM tieverkko;

CREATE INDEX tieverkko_paloina_geom_index ON tieverkko_paloina USING GIST (geom);
CREATE INDEX tieverkko_paloina_tieosa_index ON tieverkko_paloina (tie,osa);

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

CREATE OR REPLACE FUNCTION tr_osan_etaisyys(
  piste geometry, tienro INTEGER, treshold INTEGER)
  RETURNS INTEGER
AS $$
DECLARE
   alkuosa RECORD;
   alkuet NUMERIC;
   palojenpit NUMERIC;
BEGIN
   SELECT osoite3, tie, ajorata, osa, tiepiiri, path, geom
      FROM tieverkko_paloina
      WHERE ST_DWithin(geom, piste, treshold)
        AND tie=tienro
      ORDER BY ST_Length(ST_ShortestLine(geom, piste)) ASC
      LIMIT 1
   INTO alkuosa;
   
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
   
   RETURN (palojenpit+alkuet)::INTEGER;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteille(
  alkupiste geometry, loppupiste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
  reitti geometry;
  apiste geometry;
  bpiste geometry;
  aosa INTEGER;
  bosa INTEGER;
  tienosavali RECORD;
  ap NUMERIC;
  bp NUMERIC;
  alkuet INTEGER;
  loppuet INTEGER;
  tmp geometry;
BEGIN
   -- valitaan se tie ja tienosaväli jota lähellä alku- ja loppupisteet ovat yhdessä lähimpänä
  SELECT a.tie, 
         a.osa AS aosa, 
         b.osa AS bosa,
         a.ajorata AS ajorataa,
         b.ajorata AS ajoratab
    FROM tieverkko_paloina a, 
         tieverkko_paloina b 
   WHERE ST_DWithin(a.geom, alkupiste, treshold) 
     AND ST_DWithin(b.geom, loppupiste, treshold) 
     AND a.tie=b.tie
ORDER BY ST_Length(ST_ShortestLine(alkupiste, a.geom)) +
          ST_Length(ST_ShortestLine(loppupiste, b.geom))
   LIMIT 1
    INTO tienosavali;
  
  -- sortataan alku- ja loppupiste ja tienosavälit siten että alkuosa on ensimmäisenä osoitteessa
  IF tienosavali.aosa > tienosavali.bosa THEN
    aosa := tienosavali.bosa;
    bosa := tienosavali.aosa;
    apiste := loppupiste;
    bpiste := alkupiste;
  ELSE
    aosa := tienosavali.aosa;
    bosa := tienosavali.bosa;
    apiste := alkupiste;
    bpiste := loppupiste;
  END IF;

  IF aosa=bosa THEN
    SELECT ST_LineMerge(ST_Union(ST_Line_Substring(geom, LEAST(ST_Line_Locate_Point(geom, apiste), ST_Line_Locate_Point(geom, bpiste)),
				                          GREATEST(ST_Line_Locate_Point(geom, apiste),ST_Line_Locate_Point(geom, bpiste))) ORDER BY path)) 
      FROM tieverkko_paloina tv
     WHERE tv.tie = tienosavali.tie
       AND tv.osa = aosa
       AND tv.ajorata = tienosavali.ajorataa
    INTO reitti;
  ELSE     
  -- kootaan osien geometriat yhdeksi viivaksi
  SELECT ST_LineMerge(ST_Union((CASE 
				 WHEN tv.osa=aosa 
				    THEN ST_Line_Substring(tv.geom, ST_Line_Locate_Point(tv.geom, apiste), 1)
				 WHEN tv.osa=bosa 
				    THEN ST_Line_Substring(tv.geom, 0, ST_Line_Locate_Point(tv.geom, bpiste))
				 ELSE tv.geom 
				 END)
				ORDER BY tv.osa)) 
    FROM tieverkko_paloina tv
   WHERE tv.tie=tienosavali.tie
     AND tv.osa>=aosa
     AND tv.osa<=bosa
    INTO reitti;
  END IF;
  
  IF reitti IS NULL THEN
     RAISE EXCEPTION 'pisteillä ei yhteistä tietä';
  END IF;

  alkuet := tr_osan_etaisyys(alkupiste, tienosavali.tie, treshold);
  loppuet := tr_osan_etaisyys(loppupiste, tienosavali.tie, treshold);
  
  RETURN ROW(tienosavali.tie, 
             aosa, 
             alkuet, 
             bosa, 
             loppuet,
             reitti);
END;
$$ LANGUAGE plpgsql;
