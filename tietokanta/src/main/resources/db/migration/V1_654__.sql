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
  ohjeet                   TEXT,
  kommentit                TEXT,
  sijainti                 TR_OSOITE_LAAJENNETTU,
  "maakaapelia-metreissa"  DECIMAL,
  "ilmakaapelia-metreissa" DECIMAL,
  nopeusrajoitus           INTEGER,
  liikennemaara            DECIMAL
);

CREATE TYPE TIELUVAN_JOHTOASENNUS AS (
  laite     VARCHAR(128),
  tyyppi    VARCHAR(128),
  ohjeet    TEXT,
  kommentit TEXT,
  toiminnot TEXT,
  sijainti  TR_OSOITE_LAAJENNETTU
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
  sijainti                      TR_OSOITE
);

CREATE TABLE tielupa (
  -- hakemuksen perustiedot
  id                                                              SERIAL PRIMARY KEY,
  "ulkoinen-tunniste"                                             INTEGER                              NOT NULL,
  tyyppi                                                          TIELUPATYYPPI                        NOT NULL,
  "paatoksen-diaarinumero"                                        VARCHAR(128)                         NOT NULL,
  saapumispvm                                                     DATE,
  myontamispvm                                                    DATE,
  "voimassaolon-alkupvm"                                          DATE,
  "voimassaolon-loppupvm"                                         DATE,
  otsikko                                                         TEXT                                 NOT NULL,
  "katselmus-url"                                                 TEXT,
  ely                                                             INTEGER REFERENCES organisaatio (id) NOT NULL,
  urakka                                                          INTEGER REFERENCES urakka (id),
  "urakan-nimi"                                                   VARCHAR(512),
  kunta                                                           VARCHAR(256)                         NOT NULL,
  "kohde-lahiosoite"                                              VARCHAR(512),
  "kohde-postinumero"                                             VARCHAR(5),
  "kohde-postitoimipaikka"                                        VARCHAR(512),
  "tien-nimi"                                                     VARCHAR(512),
  sijainnit                                                       TR_OSOITE_LAAJENNETTU [],

  -- hakijan tiedot
  "hakija-nimi"                                                   VARCHAR(512)                         NOT NULL,
  "hakija-osasto"                                                 VARCHAR(512),
  "hakija-postinosoite"                                           VARCHAR(512)                         NOT NULL,
  "hakija-postinumero"                                            VARCHAR(5)                           NOT NULL,
  "hakija-puhelinnumero"                                          VARCHAR(32),
  "hakija-sahkopostiosoite"                                       VARCHAR(512)                         NOT NULL,
  "hakija-tyyppi"                                                 VARCHAR(256),
  "hakija-maakoodi"                                               VARCHAR(128),

  -- urakoitsijan tiedot
  "urakoitsija-nimi"                                              VARCHAR(512)                         NOT NULL,
  "urakoitsija-yhteyshenkilo"                                     VARCHAR(512),
  "urakoitsija-puhelinnumero"                                     VARCHAR(32),
  "urakoitsija-sahkopostiosoite"                                  VARCHAR(512),

  -- liikenteenohjauksesta vastaavan tiedot
  "liikenneohjaajan-nimi"                                         VARCHAR(512)                         NOT NULL,
  "liikenneohjaajan-yhteyshenkilo"                                VARCHAR(512),
  "liikenneohjaajan-puhelinnumero"                                VARCHAR(32),
  "liikenneohjaajan-sahkopostiosoite"                             VARCHAR(512),

  -- tienpitoviranomaisen tiedot
  "tienpitoviranomainen-yhteyshenkilo"                            VARCHAR(512),
  "tienpitoviranomainen-puhelinnumero"                            VARCHAR(32),
  "tienpitoviranomainen-sahkopostiosoite"                         VARCHAR(512),
  "tienpitoviranomainen-lupapaallikko"                            VARCHAR(512),
  "tienpitoviranomainen-kasittelija"                              VARCHAR(512),

  -- valmistumisilmoitus
  "valmistumisilmoitus-vaaditaan"                                 BOOLEAN,
  "valmistumisilmoitus-palautettu"                                BOOLEAN,
  "valmistumisilmoitus"                                           TEXT,

  -- johto- ja kaapeliluvan tiedot
  "johtolupa-maakaapelia-yhteensa"                                DECIMAL,
  "johtolupa-ilmakaapelia-yhteensa"                               DECIMAL,
  "johtolupa-tienylityksia"                                       INTEGER,
  "johtolupa-silta-asennuksia"                                    INTEGER,

  -- liittymäluvan tiedot
  "liittymalupa-myonnetty-kayttotarkoitus"                        VARCHAR(256),
  "liittymalupa-haettu-kayttotarkoitus"                           VARCHAR(256),
  "liittymalupa-liittyman-siirto"                                 BOOLEAN,
  "liittymalupa-tarkoituksen-kuvaus"                              TEXT,
  "liittymalupa-tilapainen"                                       BOOLEAN,
  "liittymalupa-sijainnin-kuvaus"                                 TEXT,
  "liittymalupa-arvioitu-kokonaisliikenne"                        INTEGER,
  "liittymalupa-arvioitu-kuorma-autoliikenne"                     INTEGER,
  "liittymalupa-nykyisen-liittyman-numero"                        INTEGER,
  "liittymalupa-nykyisen-liittyman-paivays"                       DATE,
  "liittymalupa-kiinteisto-rn"                                    VARCHAR(128),
  "liittymalupa-muut-kulkuyhteydet"                               TEXT,
  "liittymalupa-valmistumisen-takaraja"                           DATE,
  "liittymalupa-kyla"                                             VARCHAR(256),

  -- liittymäluvan liittymäohje
  "liittymalupa-liittymaohje-liittymakaari"                       DECIMAL,
  "liittymalupa-liittymaohje-leveys-metreissa"                    INTEGER,
  "liittymalupa-liittymaohje-rumpu"                               BOOLEAN,
  "liittymalupa-liittymaohje-rummun-halkaisija-millimetreissa"    DECIMAL,
  "liittymalupa-liittymaohje-rummun-etaisyys-metreissa"           INTEGER,
  "liittymalupa-liittymaohje-odotustila-metreissa"                INTEGER,
  "liittymalupa-liittymaohje-nakemapisteen-etaisyys"              INTEGER,
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
  "suoja-aluerakentamislupa-valitoimenpiteet"                     TEXT,
  "suoja-aluerakentamislupa-suoja-alueen-leveys"                  DECIMAL,
  "suoja-aluerakentamislupa-suoja-alue"                           BOOLEAN,
  "suoja-aluerakentamislupa-nakema-alue"                          BOOLEAN,
  "suoja-aluerakentamislupa-kiinteisto-rn"                        VARCHAR(128),

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
