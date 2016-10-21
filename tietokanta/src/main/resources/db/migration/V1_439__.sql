<<<<<<< HEAD
UPDATE sanktio
SET sakkoryhma = 'muistutus' :: sanktiolaji
WHERE maara IS NULL;

UPDATE sanktio
SET maara = NULL
WHERE sakkoryhma = 'muistutus' :: sanktiolaji;

DELETE FROM sanktio WHERE sakkoryhma IS NULL;

ALTER TABLE sanktio
  ALTER COLUMN sakkoryhma SET NOT NULL,
  ADD CONSTRAINT sakoille_on_maara
CHECK ((maara IS NOT NULL AND sakkoryhma != 'muistutus' :: sanktiolaji) OR
       (maara IS NULL AND sakkoryhma = 'muistutus' :: sanktiolaji))
=======
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
>>>>>>> develop
