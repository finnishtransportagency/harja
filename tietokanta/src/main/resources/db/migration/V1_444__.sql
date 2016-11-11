-- Välitystyyppinen kuittaus viestilokia varten

CREATE TYPE viestisuunta AS ENUM (
  'sisaan',
  'ulos');

CREATE TYPE viestikanava AS ENUM (
  'sahkoposti',
  'sms',
  'ulkoinen_jarjestelma',
  'harja'
);

ALTER TYPE kuittaustyyppi RENAME TO kuittaustyyppi_;
CREATE TYPE kuittaustyyppi AS ENUM (
  'vastaanotto',
  'lopetus',
  'vastaus',
  'muutos',
  'aloitus',
  'valitys'
);

-- Päivitä kuittaustyyppi enum tauluun
ALTER TABLE ilmoitustoimenpide RENAME COLUMN kuittaustyyppi TO kuittaustyyppi_;
ALTER TABLE ilmoitustoimenpide ADD COLUMN kuittaustyyppi kuittaustyyppi;
UPDATE ilmoitustoimenpide SET kuittaustyyppi = kuittaustyyppi_ :: TEXT :: kuittaustyyppi;

DROP TRIGGER tg_aseta_ilmoituksen_tila ON ilmoitustoimenpide;
DROP FUNCTION aseta_ilmoituksen_tila();

ALTER TABLE ilmoitustoimenpide DROP COLUMN kuittaustyyppi_;
DROP TYPE kuittaustyyppi_;

-- Lisää loput sarakkeet
ALTER TABLE ilmoitustoimenpide
  ADD COLUMN suunta viestisuunta,
  ADD COLUMN kanava viestikanava;

UPDATE ilmoitustoimenpide
SET suunta = 'sisaan'::viestisuunta
WHERE suunta IS NULL;

ALTER TABLE ilmoitustoimenpide ALTER COLUMN suunta SET NOT NULL;