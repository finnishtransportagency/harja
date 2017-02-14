-- Tee kuittaustyypeistä teksti enumin sijaan

ALTER TABLE ilmoitustoimenpide
 RENAME COLUMN kuittaustyyppi TO kuittaustyyppi_temp;
ALTER TABLE ilmoitustoimenpide
 ADD COLUMN kuittaustyyppi text;

UPDATE ilmoitustoimenpide
SET
 kuittaustyyppi = kuittaustyyppi_temp;

ALTER TABLE ilmoitustoimenpide
 DROP COLUMN kuittaustyyppi_temp;

DROP TYPE kuittaustyyppi;
