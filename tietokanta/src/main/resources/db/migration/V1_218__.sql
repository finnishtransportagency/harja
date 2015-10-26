--- Lukkotaulut
CREATE TABLE lukko (
  id       SERIAL PRIMARY KEY,
  tunniste VARCHAR(20),
  lukko    CHAR(36),
  lukittu  TIMESTAMP
);
ALTER TABLE lukko ADD CONSTRAINT uniikki_tunniste UNIQUE (tunniste);

-- Lukitseminen
CREATE OR REPLACE FUNCTION aseta_lukko(tarkistettava_tunniste VARCHAR(20), uusi_lukko CHAR(36), aikaraja integer)
  RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
  rivi_id        INTEGER;
  asetettu_lukko CHAR(36);
  lukko_asetettu TIMESTAMP;
BEGIN
  SELECT
    id,
    lukko,
    lukittu
  INTO rivi_id, asetettu_lukko, lukko_asetettu
  FROM lukko
  WHERE tunniste = tarkistettava_tunniste;

  IF rivi_id IS NULL
  THEN
    INSERT INTO lukko (tunniste, lukko, lukittu) VALUES (tarkistettava_tunniste, uusi_lukko, now());
    RETURN TRUE;
  ELSE
    IF asetettu_lukko IS NULL OR
       (aikaraja IS NOT NULL AND (EXTRACT(EPOCH FROM (current_timestamp - lukko_asetettu)) > aikaraja))
    THEN
      UPDATE lukko
      SET lukko = uusi_lukko, lukittu = now()
      WHERE tunniste = tarkistettava_tunniste;
      RETURN TRUE;
    ELSE
      RETURN FALSE;
    END IF;
  END IF;
  RETURN FALSE;
END
$$;

-- Lukon avaus
CREATE OR REPLACE FUNCTION avaa_lukko(tarkistettava_tunniste VARCHAR(20))
  RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
  rivi_id INTEGER;
BEGIN
  SELECT id
  INTO rivi_id
  FROM lukko
  WHERE tunniste = tarkistettava_tunniste;

  IF rivi_id IS NULL
  THEN
    RETURN FALSE;
  ELSE
    UPDATE lukko
    SET lukittu = NULL, lukko = NULL
    WHERE tunniste = tarkistettava_tunniste;
    RETURN TRUE;
  END IF;
  RETURN FALSE;
END
$$;

