-- Lisää kenttiä tierekisteriosoite tyyppiin
CREATE TYPE TR_KAISTA AS ENUM (
  '1',
  '11',
  '12',
  '13',
  '14',
  '15',
  '16',
  '17',
  '18',
  '19',
  '21',
  '22',
  '23',
  '24',
  '25',
  '26',
  '27',
  '28',
  '29');

ALTER TYPE TR_OSOITE
ADD ATTRIBUTE ajorata INTEGER;

ALTER TYPE TR_OSOITE
ADD ATTRIBUTE kaista TR_AJORATA;

ALTER TYPE TR_OSOITE
ADD ATTRIBUTE puoli INTEGER;

ALTER TYPE TR_OSOITE
ADD ATTRIBUTE KARTTAPVM DATE;

CREATE TYPE TIELUPAHAKIJATYYPPI AS ENUM ('kunta', 'kotitalous', 'elinkeinoelama', 'valtio', 'muu');

-- tieluvan perustaulu
CREATE TABLE tielupa (
  -- hakemuksen perustiedot
  id                             SERIAL PRIMARY KEY,
  "ulkoinen-tunniste"            INTEGER                              NOT NULL,
  tyyppi                         VARCHAR(128)                         NOT NULL,
  "paatoksen-diaarinumero"       VARCHAR(128)                         NOT NULL,
  saapumispvm                    DATE,
  myontamispvm                   DATE,
  "voimassaolon-alkupvm"         DATE,
  "voimassaolon-loppupvm"        DATE,
  otsikko                        VARCHAR(2048)                        NOT NULL,
  "katselmus-url"                TEXT,
  ely                            INTEGER REFERENCES organisaatio (id) NOT NULL,
  urakka                         INTEGER REFERENCES urakka (id),
  "urakan-nimi"                  VARCHAR(512),
  kunta                          VARCHAR(256)                         NOT NULL,
  "kohde-lahiosoite"             VARCHAR(512),
  "kohde-postinumero"            VARCHAR(5),
  "kohde-postitoimipaikka"       VARCHAR(512),
  "tien-nimi"                    VARCHAR(512),
  sijainnit                      TR_OSOITE [],

  -- hakijan tiedot
  "hakija-nimi"                  VARCHAR(512)                         NOT NULL,
  "hakija-osasto"                VARCHAR(512),
  "hakija-postinosoite"          VARCHAR(512)                         NOT NULL,
  "hakija-postinumero"           VARCHAR(5)                           NOT NULL,
  "hakija-puhelinnumero"         VARCHAR(16),
  "hakija-sahkopostiosoite"      VARCHAR(16)                          NOT NULL,
  "hakija-tyyppi"                TIELUPAHAKIJATYYPPI,
  "hakija-maakoodi"              VARCHAR(128),

  -- urakoitsijan tiedot
  "urakoitsija-nimi"             VARCHAR(512)                         NOT NULL,
  "urakoitsija-yhteyshenkilo"    VARCHAR(512)                         NOT NULL,
  "urakoitsija-puhelinnumero"    VARCHAR(16),
  "urakoitsija-sahkopostiosoite" VARCHAR(16)                          NOT NULL);