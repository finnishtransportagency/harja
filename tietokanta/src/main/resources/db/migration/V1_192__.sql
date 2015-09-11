-- Kuvaus: hoitoluokkataulu
ALTER TABLE tieverkko DROP COLUMN IF EXISTS hoitoluokka;

CREATE TABLE hoitoluokka (
   ajorata INTEGER,
   aosa INTEGER,
   tie INTEGER,
   piirinro INTEGER,
   let INTEGER,
   losa INTEGER,
   aet INTEGER,
   osa INTEGER,
   hoitoluokka INTEGER,
   geometria geometry
);

CREATE INDEX hoitoluokka_geom_index ON hoitoluokka USING GIST ( geometria ); 

CREATE OR REPLACE FUNCTION hoitoluokka_pisteelle(
  piste geometry, treshold INTEGER)
  RETURNS INTEGER
AS $$
DECLARE
   hl RECORD;
BEGIN
   SELECT *
     FROM hoitoluokka
     WHERE ST_DWithin(geometria, piste, treshold)
     ORDER BY ST_Length(ST_ShortestLine(geometria, piste)) ASC
     LIMIT 1
   INTO hl;

   IF hl IS NULL THEN
     RAISE EXCEPTION 'pisteelle ei löydy tietä';
   END IF;

   RETURN hl.hoitoluokka;
END;
$$ LANGUAGE plpgsql;
