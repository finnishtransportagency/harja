CREATE TYPE TR_OSOITE_LAAJENNETTU AS (
  tie       INTEGER,
  aosa      INTEGER,
  aet       INTEGER,
  losa      INTEGER,
  let       INTEGER,
  ajorata   INTEGER,
  kaista    INTEGER,
  puoli     INTEGER,
  karttapvm DATE,
  geometria GEOMETRY);

CREATE TYPE TIELUPATYYPPI AS ENUM (
  'johto-ja-kaapelilupa',
  'liittymalupa',
  'mainoslupa',
  'mainosilmoitus',
  'opastelupa',
  'suoja-aluerakentamislupa',
  'tilapainen-myyntilupa',
  'tilapainen-liikennemerkkijarjestely',
  'tietyolupa',
  'vesihuoltolupa'
);

CREATE TYPE TIELUVAN_KAAPELIASENNUS AS (
  laite                    TEXT,
  asennustyyppi            TEXT,
  kommentit                TEXT,
  "maakaapelia-metreissa"  DECIMAL,
  "ilmakaapelia-metreissa" DECIMAL,
  nopeusrajoitus           INTEGER,
  liikennemaara            DECIMAL,
  sijainti                 TR_OSOITE_LAAJENNETTU
);

CREATE TYPE TIELUVAN_JOHTOASENNUS AS (
  laite         TEXT,
  asennustyyppi TEXT,
  kommentit     TEXT,
  toiminnot     TEXT,
  sijainti      TR_OSOITE_LAAJENNETTU
);

CREATE TYPE TIELUVAN_OPASTE AS (
  tulostenumero INTEGER,
  kuvaus        TEXT,
  sijainti      TR_OSOITE_LAAJENNETTU
);

CREATE TYPE TIELUVAN_LIIKENNEMERKKIJARJESTELY AS (
  "alkuperainen-nopeusrajoitus" TEXT,
  "alennettu-nopeusrajoitus"    TEXT,
  "nopeusrajoituksen-pituus"    TEXT,
  sijainti                      TR_OSOITE_LAAJENNETTU
);

CREATE TYPE SUOJA_ALUE_RAKENTEEN_SIJOITUS AS ENUM (
  'suoja-alue',
  'nakemisalue'
);

CREATE TABLE tielupa (
  -- hakemuksen perustiedot
  id                                                              SERIAL PRIMARY KEY,
  luotu                                                           TIMESTAMP DEFAULT NOW(),
  muokattu                                                        TIMESTAMP,
  "ulkoinen-tunniste"                                             INTEGER                              NOT NULL,
  tyyppi                                                          TIELUPATYYPPI                        NOT NULL,
  "paatoksen-diaarinumero"                                        TEXT                                 NOT NULL,
  saapumispvm                                                     DATE,
  myontamispvm                                                    DATE,
  "voimassaolon-alkupvm"                                          DATE,
  "voimassaolon-loppupvm"                                         DATE,
  otsikko                                                         TEXT                                 NOT NULL,
  "katselmus-url"                                                 TEXT,
  ely                                                             INTEGER REFERENCES organisaatio (id) NOT NULL,
  urakka                                                          INTEGER REFERENCES urakka (id),
  "urakan-nimi"                                                   TEXT,
  kunta                                                           TEXT                                 NOT NULL,
  "kohde-lahiosoite"                                              TEXT,
  "kohde-postinumero"                                             TEXT,
  "kohde-postitoimipaikka"                                        TEXT,
  "tien-nimi"                                                     TEXT,
  sijainnit                                                       TR_OSOITE_LAAJENNETTU [],

  -- hakijan tiedot
  "hakija-nimi"                                                   TEXT                                 NOT NULL,
  "hakija-osasto"                                                 TEXT,
  "hakija-postinosoite"                                           TEXT                                 NOT NULL,
  "hakija-postinumero"                                            TEXT                                 NOT NULL,
  "hakija-puhelinnumero"                                          TEXT,
  "hakija-sahkopostiosoite"                                       TEXT                                 NOT NULL,
  "hakija-tyyppi"                                                 TEXT,
  "hakija-maakoodi"                                               TEXT,

  -- urakoitsijan tiedot
  "urakoitsija-nimi"                                              TEXT                                 NOT NULL,
  "urakoitsija-yhteyshenkilo"                                     TEXT,
  "urakoitsija-puhelinnumero"                                     TEXT,
  "urakoitsija-sahkopostiosoite"                                  TEXT,

  -- liikenteenohjauksesta vastaavan tiedot
  "liikenneohjaajan-nimi"                                         TEXT                                 NOT NULL,
  "liikenneohjaajan-yhteyshenkilo"                                TEXT,
  "liikenneohjaajan-puhelinnumero"                                TEXT,
  "liikenneohjaajan-sahkopostiosoite"                             TEXT,

  -- tienpitoviranomaisen tiedot
  "tienpitoviranomainen-yhteyshenkilo"                            TEXT,
  "tienpitoviranomainen-puhelinnumero"                            TEXT,
  "tienpitoviranomainen-sahkopostiosoite"                         TEXT,
  "tienpitoviranomainen-lupapaallikko"                            TEXT,
  "tienpitoviranomainen-kasittelija"                              TEXT,

  -- valmistumisilmoitus
  "valmistumisilmoitus-vaaditaan"                                 BOOLEAN,
  "valmistumisilmoitus-palautettu"                                BOOLEAN,
  "valmistumisilmoitus"                                           TEXT,

  -- johto- ja kaapeliluvan tiedot
  "johtolupa-maakaapelia-yhteensa"                                DECIMAL,
  "johtolupa-ilmakaapelia-yhteensa"                               DECIMAL,
  "johtolupa-tienalituksia"                                       INTEGER,
  "johtolupa-tienylityksia"                                       INTEGER,
  "johtolupa-silta-asennuksia"                                    INTEGER,

  -- liittymäluvan tiedot
  "liittymalupa-myonnetty-kayttotarkoitus"                        TEXT,
  "liittymalupa-haettu-kayttotarkoitus"                           TEXT,
  "liittymalupa-liittyman-siirto"                                 BOOLEAN,
  "liittymalupa-tarkoituksen-kuvaus"                              TEXT,
  "liittymalupa-tilapainen"                                       BOOLEAN,
  "liittymalupa-sijainnin-kuvaus"                                 TEXT,
  "liittymalupa-arvioitu-kokonaisliikenne"                        INTEGER,
  "liittymalupa-arvioitu-kuorma-autoliikenne"                     INTEGER,
  "liittymalupa-nykyisen-liittyman-numero"                        INTEGER,
  "liittymalupa-nykyisen-liittyman-paivays"                       DATE,
  "liittymalupa-kiinteisto-rn"                                    TEXT,
  "liittymalupa-muut-kulkuyhteydet"                               TEXT,
  "liittymalupa-valmistumisen-takaraja"                           DATE,
  "liittymalupa-kyla"                                             TEXT,

  -- liittymäluvan liittymäohje
  "liittymalupa-liittymaohje-liittymakaari"                       DECIMAL,
  "liittymalupa-liittymaohje-leveys-metreissa"                    INTEGER,
  "liittymalupa-liittymaohje-rumpu"                               BOOLEAN,
  "liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa"    DECIMAL,
  "liittymalupa-liittymaohje-rummun-etaisyys-metreissa"           INTEGER,
  "liittymalupa-liittymaohje-odotustila-metreissa"                INTEGER,
  "liittymalupa-liittymaohje-nakemapisteen-etaisyys"              INTEGER,
  "liittymalupa-liittymaohje-liittymisnakema"                     INTEGER,
  "liittymalupa-liittymaohje-liikennemerkit"                      TEXT,
  "liittymalupa-liittymaohje-lisaohjeet"                          TEXT,

  -- mainosluvan tiedot
  "mainoslupa-mainostettava-asia"                                 TEXT,
  "mainoslupa-sijainnin-kuvaus"                                   TEXT,
  "mainoslupa-korvaava-paatos"                                    BOOLEAN,
  "mainoslupa-tiedoksi-elykeskukselle"                            BOOLEAN,
  "mainoslupa-asemakaava-alueella"                                BOOLEAN,
  "mainoslupa-suoja-alueen-leveys"                                INTEGER,
  "mainoslupa-lisatiedot"                                         TEXT,

  -- opasteluvan tiedot
  "opastelupa-kohteen-nimi"                                       TEXT,
  "opastelupa-palvelukohteen-opastaulu"                           BOOLEAN,
  "opastelupa-palvelukohteen-osoiteviitta"                        BOOLEAN,
  "opastelupa-osoiteviitta"                                       BOOLEAN,
  "opastelupa-ennakkomerkki"                                      BOOLEAN,
  "opastelupa-opasteen-teksti"                                    TEXT,
  "opastelupa-osoiteviitan-tunnus"                                TEXT,
  "opastelupa-lisatiedot"                                         TEXT,
  "opastelupa-kohteen-url-osoite"                                 TEXT,
  "opastelupa-jatkolupa"                                          BOOLEAN,
  "opastelupa-alkuperainen-lupanro"                               INTEGER,
  "opastelupa-alkuperaisen-luvan-alkupvm"                         DATE,
  "opastelupa-alkuperaisen-luvan-loppupvm"                        DATE,
  "opastelupa-nykyinen-opastus"                                   TEXT,

  -- suoja-alueen rakentamislupa
  "suoja-aluerakentamislupa-rakennettava-asia"                    TEXT,
  "suoja-aluerakentamislupa-lisatiedot"                           TEXT,
  "suoja-aluerakentamislupa-esitetty-etaisyys-tien-keskilinjaan"  DECIMAL,
  "suoja-aluerakentamislupa-vahimmaisetaisyys-tien-keskilinjasta" DECIMAL,
  "suoja-aluerakentamislupa-suoja-alueen-leveys"                  DECIMAL,
  "suoja-aluerakentamislupa-sijoitus"                             SUOJA_ALUE_RAKENTEEN_SIJOITUS,
  "suoja-aluerakentamislupa-kiinteisto-rn"                        TEXT,

  -- tilapäinen myyntilupa
  "myyntilupa-aihe"                                               TEXT,
  "myyntilupa-alueen-nimi"                                        TEXT,
  "myyntilupa-aikaisempi-myyntilupa"                              TEXT,
  "myyntilupa-opastusmerkit"                                      TEXT,

  -- tilapäinen liikennemerkkijärjestely
  "liikennemerkkijarjestely-aihe"                                 TEXT,
  "liikennemerkkijarjestely-sijainnin-kuvaus"                     TEXT,
  "liikennemerkkijarjestely-tapahtuman-tiedot"                    TEXT,
  "liikennemerkkijarjestely-nopeusrajoituksen-syy"                TEXT,
  "liikennemerkkijarjestely-lisatiedot-nopeusrajoituksesta"       TEXT,
  "liikennemerkkijarjestely-muut-liikennemerkit"                  TEXT,

  -- tyolupa
  "tyolupa-tyon-sisalto"                                          TEXT,
  "tyolupa-tyon-saa-aloittaa"                                     DATE,
  "tyolupa-viimeistely-oltava"                                    DATE,
  "tyolupa-ohjeet-tyon-suorittamiseen"                            TEXT,
  "tyolupa-los-puuttuu"                                           BOOLEAN,
  "tyolupa-ilmoitus-tieliikennekeskukseen"                        BOOLEAN,
  "tyolupa-tilapainen-nopeusrajoitus"                             BOOLEAN,
  "tyolupa-los-lisatiedot"                                        TEXT,
  "tyolupa-tieliikennekusksen-sahkopostiosoite"                   TEXT,

  -- vesihuoltolupa
  "vesihuoltolupa-tienylityksia"                                  INTEGER,
  "vesihuoltolupa-tienalituksia"                                  INTEGER,
  "vesihuoltolupa-silta-asennuksia"                               INTEGER,

  mainokset                                                       TR_OSOITE_LAAJENNETTU [],
  opasteet                                                        TIELUVAN_OPASTE [],
  liikennemerkkijarjestelyt                                       TIELUVAN_LIIKENNEMERKKIJARJESTELY [],
  johtoasennukset                                                 TIELUVAN_JOHTOASENNUS [],
  kaapeliasennukset                                               TIELUVAN_KAAPELIASENNUS []
);

CREATE TABLE tielupa_liite (
  tielupa INTEGER REFERENCES tielupa (id),
  liite   INTEGER REFERENCES liite (id)
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-tielupa');