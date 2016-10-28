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
       (maara IS NULL AND sakkoryhma = 'muistutus' :: sanktiolaji));