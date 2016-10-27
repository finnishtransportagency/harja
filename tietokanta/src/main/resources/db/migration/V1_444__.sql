ALTER TABLE sanktio
  ALTER COLUMN sakkoryhma SET NOT NULL,
  ADD CONSTRAINT sakoille_on_maara
CHECK ((maara IS NOT NULL AND sakkoryhma != 'muistutus' :: sanktiolaji) OR
       (maara IS NULL AND sakkoryhma = 'muistutus' :: sanktiolaji));

UPDATE sanktiotyyppi
   SET sanktiolaji = ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'muistutus'::sanktiolaji]
 WHERE nimi = 'Liikenneympäristön hoito';

UPDATE sanktiotyyppi
SET sanktiolaji = ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'muistutus'::sanktiolaji]
WHERE nimi = 'Sorateiden hoito ja ylläpito';