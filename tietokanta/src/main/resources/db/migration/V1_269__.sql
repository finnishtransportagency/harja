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
  ajoratavalinta INTEGER;
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
         b.osa AS bosa
    FROM tieverkko_paloina a, 
         tieverkko_paloina b 
   WHERE ST_DWithin(a.geom, alkupiste, treshold) 
     AND ST_DWithin(b.geom, loppupiste, treshold) 
     AND a.tie=b.tie
ORDER BY ST_Length(ST_ShortestLine(alkupiste, a.geom)) +
          ST_Length(ST_ShortestLine(loppupiste, b.geom))
   LIMIT 1
    INTO tienosavali;

    alkuet := tr_osan_etaisyys(alkupiste, tienosavali.tie, treshold);
    loppuet := tr_osan_etaisyys(loppupiste, tienosavali.tie, treshold);        
  
  -- sortataan alku- ja loppupiste ja tienosavälit siten että alkuosa on ensimmäisenä osoitteessa
  IF (tienosavali.aosa=tienosavali.bosa AND alkuet>loppuet) OR tienosavali.aosa > tienosavali.bosa THEN
    aosa := tienosavali.bosa;
    bosa := tienosavali.aosa;
    apiste := loppupiste;
    bpiste := alkupiste;
    ajoratavalinta := 2;
  ELSE
    aosa := tienosavali.aosa;
    bosa := tienosavali.bosa;
    apiste := alkupiste;
    bpiste := loppupiste;
    ajoratavalinta := 1;
  END IF;

  IF aosa=bosa THEN
    SELECT ST_Line_Substring(geom, LEAST(ST_Line_Locate_Point(geom, apiste), ST_Line_Locate_Point(geom, bpiste)),
				    GREATEST(ST_Line_Locate_Point(geom, apiste),ST_Line_Locate_Point(geom, bpiste)))
      FROM tieverkko_paloina tv
     WHERE tv.tie = tienosavali.tie
       AND tv.osa = aosa
       AND (tv.ajorata = ajoratavalinta OR tv.ajorata=0)
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
     AND (tv.ajorata = ajoratavalinta OR tv.ajorata = 0)
    INTO reitti;
  END IF;
  
  IF reitti IS NULL THEN
     RAISE EXCEPTION 'pisteillä ei yhteistä tietä';
  END IF;
  
  RETURN ROW(tienosavali.tie, 
             tienosavali.aosa, 
             alkuet, 
             tienosavali.bosa, 
             loppuet,
             CASE WHEN ajoratavalinta=1 THEN reitti
                  ELSE ST_Reverse(reitti) 
             END);
END;
$$ LANGUAGE plpgsql;
