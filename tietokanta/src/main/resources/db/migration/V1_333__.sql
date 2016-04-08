CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteelle_noex(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
BEGIN
   RETURN tierekisteriosoite_pisteelle(piste, treshold);
EXCEPTION
   WHEN OTHERS THEN RETURN NULL;
END;
$$ LANGUAGE plpgsql;
