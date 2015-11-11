-- Lisää hoitoluokka-tauluun tietolajitunniste

CREATE TYPE hoitoluokan_tietolajitunniste AS ENUM ('talvihoito', 'soratie', 'viherhoito');

ALTER TABLE hoitoluokka ADD COLUMN tietolajitunniste hoitoluokan_tietolajitunniste;

CREATE OR REPLACE FUNCTION hoitoluokka_pisteelle(
  piste geometry, tietolaji hoitoluokan_tietolajitunniste, treshold INTEGER)
  RETURNS INTEGER
AS $$
DECLARE
  hl RECORD;
BEGIN
  SELECT *
  FROM hoitoluokka
  WHERE ST_DWithin(geometria, piste, treshold) AND tietolajitunniste = tietolaji
  ORDER BY ST_Length(ST_ShortestLine(geometria, piste)) ASC
  LIMIT 1
  INTO hl;

  IF hl IS NULL THEN
    RETURN NULL;
  END IF;

  RETURN hl.hoitoluokka;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE reittipiste RENAME COLUMN hoitoluokka to talvihoitoluokka;
ALTER TABLE reittipiste ADD COLUMN soratiehoitoluokka INTEGER;

UPDATE hoitoluokka SET tietolajitunniste = 'talvihoito'::hoitoluokan_tietolajitunniste;