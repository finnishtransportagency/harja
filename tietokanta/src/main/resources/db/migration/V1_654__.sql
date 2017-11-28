-- Lisää kenttiä tierekisteriosoite tyyppiin
CREATE TYPE TR_KAISTA AS ENUM (
  '1',
  '11',
  '12',
  '13',
  '14',
  '15',
  '32',
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
ADD ATTRIBUTE ajorata TR_KAISTA;

ALTER TYPE TR_OSOITE
ADD ATTRIBUTE kaista INTEGER;

ALTER TYPE TR_OSOITE
ADD ATTRIBUTE puoli INTEGER;

ALTER TYPE TR_OSOITE
ADD ATTRIBUTE KARTTAPVM DATE;

CREATE TYPE TIELUPAHAKIJATYYPPI AS ENUM ('kunta', 'kotitalous', 'elinkeinoelama', 'valtio', 'muu');

CREATE TABLE tielupa (
  -- hakemuksen perustiedot
  id                                      SERIAL PRIMARY KEY,
  "ulkoinen-tunniste"                     INTEGER                              NOT NULL,
  tyyppi                                  VARCHAR(128)                         NOT NULL,
  "paatoksen-diaarinumero"                VARCHAR(128)                         NOT NULL,
  saapumispvm                             DATE,
  myontamispvm                            DATE,
  "voimassaolon-alkupvm"                  DATE,
  "voimassaolon-loppupvm"                 DATE,
  otsikko                                 TEXT                                 NOT NULL,
  "katselmus-url"                         TEXT,
  ely                                     INTEGER REFERENCES organisaatio (id) NOT NULL,
  urakka                                  INTEGER REFERENCES urakka (id),
  "urakan-nimi"                           VARCHAR(512),
  kunta                                   VARCHAR(256)                         NOT NULL,
  "kohde-lahiosoite"                      VARCHAR(512),
  "kohde-postinumero"                     VARCHAR(5),
  "kohde-postitoimipaikka"                VARCHAR(512),
  "tien-nimi"                             VARCHAR(512),
  sijainnit                               TR_OSOITE [],

  -- hakijan tiedot
  "hakija-nimi"                           VARCHAR(512)                         NOT NULL,
  "hakija-osasto"                         VARCHAR(512),
  "hakija-postinosoite"                   VARCHAR(512)                         NOT NULL,
  "hakija-postinumero"                    VARCHAR(5)                           NOT NULL,
  "hakija-puhelinnumero"                  VARCHAR(32),
  "hakija-sahkopostiosoite"               VARCHAR(512)                         NOT NULL,
  "hakija-tyyppi"                         TIELUPAHAKIJATYYPPI,
  "hakija-maakoodi"                       VARCHAR(128),

  -- urakoitsijan tiedot
  "urakoitsija-nimi"                      VARCHAR(512)                         NOT NULL,
  "urakoitsija-yhteyshenkilo"             VARCHAR(512),
  "urakoitsija-puhelinnumero"             VARCHAR(32),
  "urakoitsija-sahkopostiosoite"          VARCHAR(512),

  -- liikenteenohjauksesta vastaavan tiedot
  "liikenneohjaajan-nimi"                 VARCHAR(512)                         NOT NULL,
  "liikenneohjaajan-yhteyshenkilo"        VARCHAR(512),
  "liikenneohjaajan-puhelinnumero"        VARCHAR(32),
  "liikenneohjaajan-sahkopostiosoite"     VARCHAR(512),

  -- tienpitoviranomaisen tiedot
  "tienpitoviranomainen-yhteyshenkilo"    VARCHAR(512),
  "tienpitoviranomainen-puhelinnumero"    VARCHAR(32),
  "tienpitoviranomainen-sahkopostiosoite" VARCHAR(512),
  "tienpitoviranomainen-lupapaallikko"    VARCHAR(512),
  "tienpitoviranomainen-kasittelija"      VARCHAR(512),

  -- valmistumisilmoitus
  "valmistumisilmoitus-vaaditaan"         BOOLEAN,
  "valmistumisilmoitus-palautettu"        BOOLEAN,
  "valmistumisilmoitus"                   TEXT
);

CREATE TABLE johto_ja_kaapelilupa (
  id                      SERIAL PRIMARY KEY,
  tielupa                 INTEGER REFERENCES tielupa (id) NOT NULL,
  "maakaapelia-yhteensa"  DECIMAL,
  "ilmakaapelia-yhteensa" DECIMAL,
  "tienylityksia"         INTEGER,
  "silta-asennuksia"      INTEGER
);

CREATE TABLE tieluvan_kaapeliasennus (
  id                       SERIAL PRIMARY KEY,
  johto_ja_kaapelilupa     INTEGER REFERENCES johto_ja_kaapelilupa (id) NOT NULL,
  laite                    VARCHAR(128)                                 NOT NULL,
  asennustyyppi            VARCHAR(128)                                 NOT NULL,
  ohjeet                   TEXT,
  kommentit                TEXT,
  sijainti                 TR_OSOITE,
  "maakaapelia-metreissa"  DECIMAL,
  "ilmakaapelia-metreissa" DECIMAL,
  nopeusrajoitus           INTEGER,
  liikennemaara            DECIMAL
);

CREATE TABLE liittymalupa (
  id                                              SERIAL PRIMARY KEY,
  tielupa                                         INTEGER REFERENCES tielupa (id) NOT NULL,
  "myonnetty-kayttotarkoitus"                     VARCHAR(256),
  "haettu-kayttotarkoitus"                        VARCHAR(256),
  "liittyman-siirto"                              BOOLEAN,
  "tarkoituksen-kuvaus"                           TEXT,
  tilapainen                                      BOOLEAN,
  "sijainnin-kuvaus"                              TEXT,
  "arvioitu-kokonaisliikenne"                     INTEGER,
  "arvioitu-kuorma-autoliikenne"                  INTEGER,
  "nykyisen-liittyman-numero"                     INTEGER,
  "nykyisen-liittyman-paivays"                    DATE,
  "kiinteisto-rn"                                 VARCHAR(128),
  "muut-kulkuyhteydet"                            TEXT,
  "valmistumisen-takaraja"                        DATE,
  kyla                                            VARCHAR(256),

  -- liittymäohje
  "liittymaohje-liittymakaari"                    DECIMAL,
  "liittymaohje-leveys-metreissa"                 INTEGER,
  "liittymaohje-rumpu"                            BOOLEAN,
  "liittymaohje-rummun-halkaisija-millimetreissa" DECIMAL,
  "liittymaohje-rummun-etaisyys-metreissa"        INTEGER,
  "liittymaohje-odotustila-metreissa"             INTEGRAATIO,
  "liittymaohje-nakemapisteen-etaisyys"           INTEGER,
  "liittymaohje-liikennemerkit"                   TEXT,
  "liittymaohje-lisaohjeet"                       TEXT
);

CREATE TABLE mainoslupa (
  id                        SERIAL PRIMARY KEY,
  tielupa                   INTEGER REFERENCES tielupa (id) NOT NULL,
  "mainostettava-asia"      TEXT,
  "sijainnin-kuvaus"        TEXT,
  "korvaava-paatos"         BOOLEAN,
  "tiedoksi-elykeskukselle" BOOLEAN,
  "asemakaava-alueella"     BOOLEAN,
  "suoja-alueen-leveys"     INTEGER,
  "lisatiedot"              TEXT,
  mainosilmoitus            BOOLEAN DEFAULT FALSE
);

CREATE TABLE tieluvan_mainokset (
  id             SERIAL PRIMARY KEY,
  mainosilmoitus INTEGER REFERENCES mainosilmoitus (id) NOT NULL,
  "sijainti"     TR_OSOITE
);

CREATE TABLE opastelupa (
  id                            SERIAL PRIMARY KEY,
  tielupa                       INTEGER REFERENCES tielupa (id) NOT NULL,
  "kohteen-nimi"                TEXT,
  "palvelukohteen-opastaulu"    BOOLEAN,
  "palvelukohteen-osoiteviitta" BOOLEAN,
  osoiteviitta                  BOOLEAN,
  ennakkomerkki                 BOOLEAN,
  "opasteen-teksti"             TEXT,
  "osoiteviitan-tunnus"         TEXT,
  lisatiedot                    TEXT,
  "kohteen-url-osoite"          TEXT,
  jatkolupa                     BOOLEAN,
  "alkuperainen-lupanro"        INTEGER,
  "alkuperaisen-luvan-alkupvm"  DATE,
  "alkuperaisen-luvan-loppupvm" DATE,
  "nykyinen-opastus"            TEXT
);

CREATE TABLE tieluvan_opasteet (
  id            SERIAL PRIMARY KEY,
  opastelupa    INTEGER REFERENCES opastelupa (id) NOT NULL,
  tulostenumero INTEGER,
  kuvaus        TEXT,
  sijainti      TR_OSOITE
);

CREATE TABLE suoja_aluerakentamislupa (
  id                                     SERIAL PRIMARY KEY,
  tielupa                                INTEGER REFERENCES tielupa (id) NOT NULL,
  "rakennettava-asia"                    TEXT,
  "lisatiedot"                           TEXT,
  "esitetty-etaisyys-tien-keskilinjaan"  DECIMAL,
  "vahimmaisetaisyys-tien-keskilinjasta" DECIMAL,
  "valitoimenpiteet"                     TEXT,
  "suoja-alueen-leveys"                  DECIMAL,
  "suoja-alue"                           BOOLEAN,
  "nakema-alue"                          BOOLEAN,
  "kiinteisto-rn"                        VARCHAR(128)
);

CREATE TABLE tilapainen_myyntilupa (
  id                      SERIAL PRIMARY KEY,
  tielupa                 INTEGER REFERENCES tielupa (id) NOT NULL,
  aihe                    TEXT,
  "alueen-nimi"           TEXT,
  "aikaisempi-myyntilupa" TEXT,
  opastusmerkit           TEXT
);

CREATE TABLE tilapainen_liikennemerkkijarjestely (
  id                               SERIAL PRIMARY KEY,
  tielupa                          INTEGER REFERENCES tielupa (id) NOT NULL,
  aihe                             TEXT,
  "sijainnin-kuvaus"               TEXT,
  "tapahtuman-tiedot"              TEXT,
  "nopeusrajoituksen-syy"          TEXT,
  "lisatiedot-nopeusrajoituksesta" TEXT,
  "muut-liikennemerkit"            TEXT
);

CREATE TABLE liikennemerkkijarjestely (
  id                                  SERIAL PRIMARY KEY,
  tilapainen_liikennemerkkijarjestely INTEGER REFERENCES tilapainen_liikennemerkkijarjestely (id) NOT NULL,
  "alkuperainen-nopeusrajoitus"       TEXT,
  "alennettu-nopeusrajoitus"          TEXT,
  "nopeusrajoituksen-pituus"          TEXT,
  sijainti                            TR_OSOITE
);

CREATE TABLE tietyolupa (
  id                                    SERIAL PRIMARY KEY,
  tielupa                               INTEGER REFERENCES tielupa (id) NOT NULL,
  "tyon-sisalto"                        TEXT,
  "tyon-saa-aloittaa"                   DATE,
  "viimeistely-oltava"                  DATE,
  "ohjeet-tyon-suorittamiseen"          TEXT,
  "los-puuttuu"                         BOOLEAN,
  "ilmoitus-tieliikennekeskukseen"      BOOLEAN,
  "tilapainen-nopeusrajoitus"           BOOLEAN,
  "los-lisatiedot"                      TEXT,
  "tieliikennekusksen-sahkopostiosoite" TEXT
);

CREATE TABLE vesihuoltolupa (
  id                 SERIAL PRIMARY KEY,
  tielupa            INTEGER REFERENCES tielupa (id) NOT NULL,
  tienylityksia      INTEGER,
  "silta-asennuksia" INTEGER
);

CREATE TABLE tieluvan_johtoasennus (
  id             SERIAL PRIMARY KEY,
  vesihuoltolupa INTEGER REFERENCES vesihuoltolupa (id) NOT NULL,
  laite          VARCHAR(128)                           NOT NULL,
  tyyppi         VARCHAR(128)                           NOT NULL,
  ohjeet         TEXT,
  kommentit      TEXT,
  toiminnot      TEXT,
  sijainti       TR_OSOITE
);

CREATE TABLE tielupa_liite (
  tielupa INTEGER REFERENCES tielupa (id),
  liite   INTEGER REFERENCES liite (id)
);
