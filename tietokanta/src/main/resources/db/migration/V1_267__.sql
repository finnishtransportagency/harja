-- kuvaus: laske piste tierekisteriosoitteelle
CREATE OR REPLACE FUNCTION tierekisteriosoitteelle_piste(
  tie_ INTEGER, aosa_ INTEGER, aet_ INTEGER)
  RETURNS geometry
AS $$
DECLARE
   result geometry;
   suhde float;
BEGIN
   SELECT ST_Line_Interpolate_Point(geom, LEAST(1, (aet_::float)/ST_Length(geom)))
    FROM tieverkko_paloina WHERE tie=tie_ AND osa=aosa_ LIMIT 1 INTO result;

    RETURN result;
END;
$$ LANGUAGE plpgsql;
