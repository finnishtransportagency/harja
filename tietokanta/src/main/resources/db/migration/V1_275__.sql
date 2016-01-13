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
  itmp INTEGER;
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

  IF tienosavali.aosa > tienosavali.bosa THEN
    aosa := tienosavali.bosa;
    bosa := tienosavali.aosa;
    apiste := loppupiste;
    bpiste := alkupiste;
    ajoratavalinta := 2;
  ELSEIF tienosavali.aosa = tienosavali.bosa THEN
    aosa := tienosavali.aosa;
    bosa := tienosavali.bosa;
    IF alkuet>loppuet THEN
      ajoratavalinta := 2;
    ELSE
      ajoratavalinta := 1;
    END IF;
  ELSE
    aosa := tienosavali.aosa;
    bosa := tienosavali.bosa;
    ajoratavalinta := 1;
    apiste := alkupiste;
    bpiste := loppupiste;
  END IF;

  IF aosa=bosa THEN
    SELECT ST_Line_Substring(geom, LEAST(ST_Line_Locate_Point(geom, alkupiste), ST_Line_Locate_Point(geom, loppupiste)),
				    GREATEST(ST_Line_Locate_Point(geom, alkupiste),ST_Line_Locate_Point(geom, loppupiste)))
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

-- kuvaus: tierekisteriosoittelle_viiva, kaistan päättely
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER)
  RETURNS SETOF geometry
AS $$
DECLARE
   ajoratavalinta INTEGER;
   aos INTEGER;
   los INTEGER;
   aet INTEGER;
   let INTEGER;
BEGIN
   IF aosa_ = losa_ THEN
        IF aet_ < let_ THEN
	  ajoratavalinta := 1;
	  aet := aet_;
	  let := let_;
	ELSE
	  ajoratavalinta := 2;
	  aet := let_;
	  let := aet_;
	END IF;
	IF ajoratavalinta=2 THEN
	RETURN QUERY SELECT ST_Reverse(ST_Line_Substring(geom, aet/tr_pituus::FLOAT, let/tr_pituus::FLOAT))
	FROM tieverkko_paloina
	WHERE tie=tie_
	  AND osa=aosa_
	  AND (ajorata=0 OR ajorata=ajoratavalinta);
	ELSE
	RETURN QUERY SELECT ST_Line_Substring(geom, aet/tr_pituus::FLOAT, let/tr_pituus::FLOAT)
	FROM tieverkko_paloina
	WHERE tie=tie_
	  AND osa=aosa_
	  AND (ajorata=0 OR ajorata=ajoratavalinta);
	END IF;
   ELSE
        IF aosa_ < losa_ THEN
	  ajoratavalinta := 1;
	  aos := aosa_;
	  los := losa_;
	  aet := aet_;
	  let := let_;
	ELSE
	  ajoratavalinta := 2;
	  aos := losa_;
	  los := aosa_;
	  aet := let_;
	  let := aet_;
	END IF;
	IF ajoratavalinta=2 THEN
        RETURN QUERY WITH q as (SELECT ST_LineMerge(ST_Union((CASE WHEN (osa=aos AND ajorata!=0) THEN ST_Line_Substring(geom, LEAST(1, aet/ST_Length(geom)), 1)
				      WHEN osa=los THEN ST_Line_Substring(geom, 0, LEAST(1,let/ST_Length(geom)))
				      ELSE geom END) ORDER BY osa)) AS geom
		     FROM tieverkko_paloina
		      	WHERE tie = tie_
			AND osa >= aos
			AND osa <= los
			AND (ajorata=0 OR ajorata=ajoratavalinta)
			)
	  SELECT ST_Reverse(geom) FROM q;
	ELSE
        RETURN QUERY WITH q as (SELECT ST_LineMerge(ST_Union((CASE WHEN (osa=aos AND ajorata!=0) THEN ST_Line_Substring(geom, LEAST(1, aet/ST_Length(geom)), 1)
				      WHEN osa=los THEN ST_Line_Substring(geom, 0, LEAST(1,let/ST_Length(geom)))
				      ELSE geom END) ORDER BY osa)) AS geom
		     FROM tieverkko_paloina
		      	WHERE tie = tie_
			AND osa >= aos
			AND osa <= los
			AND (ajorata=0 OR ajorata=ajoratavalinta)
			)
	  SELECT geom FROM q;
	END IF;
   END IF;
END;
$$ LANGUAGE plpgsql;
