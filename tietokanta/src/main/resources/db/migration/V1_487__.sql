-- Luo funktio, joka lisää uuden geometriapäivityksen, jos sellaista ei löydy päivittämisen yhteydessä.

CREATE OR REPLACE FUNCTION paivita_geometriapaivityksen_viimeisin_paivitys(
  geometriapaivitys_  CHARACTER VARYING,
  viimeisin_paivitys_ TIMESTAMP)
  RETURNS VOID AS
$$
DECLARE
  geometriapaivitys_id INTEGER;
BEGIN
  SELECT id
  INTO geometriapaivitys_id
  FROM geometriapaivitys
  WHERE nimi = geometriapaivitys_;

  -- try update
  UPDATE geometriapaivitys
  SET viimeisin_paivitys = viimeisin_paivitys_
  WHERE id = geometriapaivitys_id;

  IF NOT FOUND
  THEN
    INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys)
    VALUES (geometriapaivitys_, viimeisin_paivitys_);
  END IF;
END;
$$
LANGUAGE plpgsql;

ALTER TABLE geometriapaivitys
  ALTER COLUMN nimi TYPE VARCHAR(30);