-- Refaktoroi tietyöilmoituksen taulua

-- Tehdään henkilötyyppi, jolla saadaan toistuvat kentät yhtenäistettyä
CREATE TYPE tietyon_henkilo AS (
  etunimi varchar(32),
  sukunimi varchar(32),
  matkapuhelin varchar(32),
  sahkoposti varchar(128)
);


-- Ajoneuvorajoitukset omaan tyyppiin
CREATE TYPE tietyon_ajoneuvorajoitukset AS (
  "max-korkeus"                  DECIMAL,
  "max-leveys"                   DECIMAL,
  "max-pituus"                   DECIMAL,
  "max-paino"                    DECIMAL
);

DELETE FROM tietyoilmoitus;

-- Muuta yhteystietokentät
ALTER TABLE tietyoilmoitus
  DROP COLUMN ilmoittaja,
  DROP COLUMN ilmoittaja_etunimi,
  DROP COLUMN ilmoittaja_sukunimi,
  DROP COLUMN ilmoittaja_sahkoposti,
  DROP COLUMN ilmoittaja_matkapuhelin,
  DROP COLUMN urakoitsijayhteyshenkilo,
  DROP COLUMN urakoitsijayhteyshenkilo_etunimi,
  DROP COLUMN urakoitsijayhteyshenkilo_sukunimi,
  DROP COLUMN urakoitsijayhteyshenkilo_matkapuhelin,
  DROP COLUMN urakoitsijayhteyshenkilo_sahkoposti,
  DROP COLUMN tilaajayhteyshenkilo,
  DROP COLUMN tilaajayhteyshenkilo_etunimi,
  DROP COLUMN tilaajayhteyshenkilo_sukunimi,
  DROP COLUMN tilaajayhteyshenkilo_matkapuhelin,
  DROP COLUMN tilaajayhteyshenkilo_sahkoposti;

ALTER TABLE tietyoilmoitus
  ADD COLUMN "ilmoittaja-id" INTEGER REFERENCES kayttaja (id),
  ADD COLUMN ilmoittaja tietyon_henkilo,
  ADD COLUMN "urakoitsijayhteyshenkilo-id" INTEGER REFERENCES kayttaja (id),
  ADD COLUMN urakoitsijayhteyshenkilo tietyon_henkilo,
  ADD COLUMN "tilaajayhteyshenkilo-id" INTEGER REFERENCES kayttaja (id),
  ADD COLUMN tilaajayhteyshenkilo tietyon_henkilo;

-- Muuta rajoitusket
ALTER TABLE tietyoilmoitus
 DROP COLUMN ajoneuvo_max_korkeus,
 DROP COLUMN ajoneuvo_max_leveys,
 DROP COLUMN ajoneuvo_max_pituus,
 DROP COLUMN ajoneuvo_max_paino;

ALTER TABLE tietyoilmoitus
 ADD COLUMN ajoneuvorajoitukset tietyon_ajoneuvorajoitukset;

-- Huomautukset arrayksi
ALTER TABLE tietyoilmoitus
  DROP COLUMN huomautukset;
ALTER TABLE tietyoilmoitus
  ADD COLUMN huomautukset tietyon_huomautukset[];
