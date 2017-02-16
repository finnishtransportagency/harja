<<<<<<< HEAD
-- Mobiiliin laadunseurantatyökaluun lisää uusia vakiohavaintoja

INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Yli-/aliauraus', 't', 'yli-tai-aliauraus');

INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Kevätmuokkauspuute', 't', 'kevatmuokkauspuute');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Sorastuspuute', 't', 'sorastuspuute');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Kelirikkohavainnot', 't', 'kelirikkohavainnot');

INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Yksittäinen reikä', 'f', 'yksittainen-reika');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Reikäjono', 't', 'reikajono');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Reunapainuma', 't', 'reunapainuma');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Purkaumat', 't', 'purkaumat');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Syvät ajourat', 't', 'syvat-ajourat');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Turvallisuutta vaarantava heitto', 't', 'liikenneturvallisuutta-vaarantava-heitto');
INSERT INTO vakiohavainto (nimi, jatkuva, avain) VALUES ('Ajomukavuutta haittaava epätasaisuus', 't', 'ajomukavuutta-haittaava-epatasaisuus-vaarantava-heitto');
=======
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
>>>>>>> develop
