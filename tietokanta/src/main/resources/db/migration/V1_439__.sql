ALTER TABLE ilmoitus
  RENAME COLUMN ilmoittaja_tyyppi TO ilmoittaja_tyyppi_temp;
ALTER TABLE ilmoitus
  ADD ilmoittaja_tyyppi TEXT;

UPDATE ilmoitus
SET
  ilmoittaja_tyyppi = ilmoittaja_tyyppi_temp;

ALTER TABLE ilmoitus
  DROP COLUMN ilmoittaja_tyyppi_temp;

DROP TYPE ilmoittajatyyppi;
