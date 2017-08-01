-- VV-materiaalille pakolliset tiedot
UPDATE vv_materiaali SET luoja = (SELECT id FROM kayttaja WHERE kayttajanimi = 'harja') WHERE luoja IS NULL;
UPDATE vv_materiaali SET luotu = NOW() WHERE luotu IS NULL;

ALTER TABLE vv_materiaali ALTER COLUMN luoja SET NOT NULL;
ALTER TABLE vv_materiaali ALTER COLUMN luotu SET NOT NULL;
ALTER TABLE vv_materiaali ALTER COLUMN "urakka-id" SET NOT NULL;
ALTER TABLE vv_materiaali ALTER COLUMN nimi SET NOT NULL;