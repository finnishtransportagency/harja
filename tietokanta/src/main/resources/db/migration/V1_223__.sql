-- tierekisteriosoitteelle piste
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_piste(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER)
  RETURNS geometry
AS $$
DECLARE
   result geometry;
   suhde float;
BEGIN
    WITH tiegeom AS (SELECT (ST_Dump(geometria)).geom as geometria, tr_pituus
      FROM tieverkko
    WHERE tie=tie_
      AND osa=aosa_)
   SELECT ST_Line_Interpolate_Point(geometria, LEAST(1, (aet_::float)/tr_pituus))
    FROM tiegeom LIMIT 1 INTO result;

    RETURN result;
END;
$$ LANGUAGE plpgsql;
