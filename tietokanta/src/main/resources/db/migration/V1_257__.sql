-- kuvaus: tierekisteriosoitteelle_viiva multilinestring korjaus
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_viiva(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER, losa_ INTEGER, let_ INTEGER)
  RETURNS geometry
AS $$
DECLARE
   rval geometry;
BEGIN
   IF aosa_ = losa_ THEN
	SELECT ST_Line_Substring(geom, aet_/tr_pituus::FLOAT, let_/tr_pituus::FLOAT)
	FROM tieverkko_paloina
	WHERE tie=tie_
	  AND osa=aosa_
	INTO rval;
   ELSE
        WITH q as (SELECT ST_LineMerge(ST_Union((CASE WHEN osa=aosa_ THEN ST_Line_Substring(geom, LEAST(1, aet_/ST_Length(geom)), 1)
				      WHEN osa=losa_ THEN ST_Line_Substring(geom, 0, LEAST(1,let_/ST_Length(geom)))
				      ELSE geom END) ORDER BY osa)) AS geom
		     FROM tieverkko_paloina
		      	WHERE tie = 20
			AND osa >= 1
			AND osa <= 5
			GROUP BY ajorata)
	  SELECT geom FROM q WHERE GeometryType(geom)='LINESTRING' LIMIT 1 INTO rval;
   END IF;

   IF rval IS NULL THEN
     RAISE EXCEPTION 'Virheellinen tierekisteriosoite';
   END IF;

   RETURN rval;
END;
$$ LANGUAGE plpgsql;
