CREATE OR REPLACE FUNCTION aseta_lukko(tarkistettava_tunniste VARCHAR(30), uusi_lukko CHAR(36), aikaraja BIGINT)
  RETURNS BOOLEAN LANGUAGE plpgsql AS $$
DECLARE
  loytynyt_tunniste VARCHAR(30);
  asetettu_lukko    CHAR(36);
  lukko_asetettu    TIMESTAMP;
BEGIN
  LOCK TABLE lukko IN ACCESS EXCLUSIVE MODE;
  SELECT
    tunniste,
    lukko,
    lukittu
  INTO loytynyt_tunniste, asetettu_lukko, lukko_asetettu
  FROM lukko
  WHERE tunniste = tarkistettava_tunniste;

  IF loytynyt_tunniste IS NULL
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