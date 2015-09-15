-- Kuvaus: tierekisteriosoitteen haku yhdellä pisteellä, korjaus

CREATE OR REPLACE FUNCTION tierekisteriosoite_pisteelle(
  piste geometry, treshold INTEGER)
  RETURNS tr_osoite
AS $$
DECLARE
   alkuosa RECORD;
   alkuet NUMERIC;
BEGIN
   SELECT osoite3, tie, ajorata, osa, tiepiiri, geometria
     FROM tieverkko
     WHERE ST_DWithin(geometria, piste, treshold)
     ORDER BY ST_Length(ST_ShortestLine(geometria, piste)) ASC
     LIMIT 1
   INTO alkuosa;

   IF alkuosa IS NULL THEN
     RAISE EXCEPTION 'pisteelle ei löydy tietä';
   END IF;

   SELECT ST_Length(ST_Line_Substring(alkuosa.geometria, 0, ST_Line_Locate_Point(ST_LineMerge(alkuosa.geometria), piste))) INTO alkuet;

   RETURN ROW(alkuosa.tie, alkuosa.osa, alkuet::INTEGER, 0, 0, NULL::geometry);
END;
$$ LANGUAGE plpgsql;
