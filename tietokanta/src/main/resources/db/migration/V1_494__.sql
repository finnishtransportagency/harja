-- Tee kuittaustyypeistä teksti enumin sijaan

DROP TRIGGER IF EXISTS tg_aseta_ilmoituksen_tila
ON ilmoitustoimenpide;

DROP FUNCTION IF EXISTS aseta_ilmoituksen_tila();

ALTER TABLE ilmoitustoimenpide
  RENAME COLUMN kuittaustyyppi TO kuittaustyyppi_temp;
ALTER TABLE ilmoitustoimenpide
  ADD COLUMN kuittaustyyppi TEXT;

UPDATE ilmoitustoimenpide
SET
  kuittaustyyppi = kuittaustyyppi_temp;

ALTER TABLE ilmoitustoimenpide
  DROP COLUMN kuittaustyyppi_temp;

DROP TYPE kuittaustyyppi;