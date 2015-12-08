
-- kuvaus: tierekisteriosoitteelle_viiva multilinestring korjaus
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER)
  RETURNS SETOF geometry
AS $$
DECLARE
BEGIN
   IF aosa_ = losa_ THEN
	RETURN QUERY SELECT ST_Line_Substring(geom, aet_/tr_pituus::FLOAT, let_/tr_pituus::FLOAT)
	FROM tieverkko_paloina
	WHERE tie=tie_
	  AND osa=aosa_;
   ELSE
        RETURN QUERY WITH q as (SELECT ST_LineMerge(ST_Union((CASE WHEN osa=aosa_ THEN ST_Line_Substring(geom, LEAST(1, aet_/ST_Length(geom)), 1)
				      WHEN osa=losa_ THEN ST_Line_Substring(geom, 0, LEAST(1,let_/ST_Length(geom)))
				      ELSE geom END) ORDER BY osa)) AS geom
		     FROM tieverkko_paloina
		      	WHERE tie = 20
			AND osa >= 1
			AND osa <= 5
			GROUP BY ajorata)
	  SELECT geom FROM q WHERE GeometryType(geom)='LINESTRING';
   END IF;
END;
$$ LANGUAGE plpgsql;
