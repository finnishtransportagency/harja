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

DROP TABLE tietyoilmoitus;
DROP TYPE tietyon_kaistajarjestelyt;
DROP TYPE tietyon_tyoaika;

CREATE TYPE tietyon_tyoaika AS (alkuaika TIME, loppuaika TIME, paivat viikonpaiva []);

CREATE TYPE tietyon_kaistajarjestelytyyppi AS ENUM (
  'ajokaistaSuljettu',
  'ajorataSuljettu',
  'tieSuljettu',
  'muu');

CREATE TYPE tietyon_kaistajarjestelyt AS (
  jarjestely tietyon_kaistajarjestelytyyppi,
  selite TEXT
);


CREATE TABLE tietyoilmoitus (
  id                                    SERIAL PRIMARY KEY,
  "tloik-id"                            INTEGER,
  paatietyoilmoitus                     INTEGER REFERENCES tietyoilmoitus (id),
  "tloik-paatietyoilmoitus-id"          INTEGER,

  luotu                                 TIMESTAMP,
  luoja                                 INTEGER REFERENCES kayttaja (id),
  muokattu                              TIMESTAMP,
  muokkaaja                             INTEGER REFERENCES kayttaja (id),
  poistettu                             TIMESTAMP,
  poistaja                              INTEGER REFERENCES kayttaja (id),

  "ilmoittaja-id"                       INTEGER REFERENCES kayttaja (id),
  ilmoittaja                            tietyon_henkilo,

  "urakka-id"                           INTEGER REFERENCES urakka (id),
  "urakan-nimi"                         VARCHAR(256),
  urakkatyyppi                          urakkatyyppi,

  "urakoitsijayhteyshenkilo-id"         INTEGER REFERENCES kayttaja (id),
  urakoitsijayhteyshenkilo              tietyon_henkilo,

  "tilaaja-id"                          INTEGER REFERENCES organisaatio (id),
  "tilaajan-nimi"                       VARCHAR(128),

  "tilaajayhteyshenkilo-id"             INTEGER REFERENCES kayttaja (id),
  tilaajayhteyshenkilo                  tietyon_henkilo,

  tyotyypit                             tietyon_tyypin_kuvaus [],
  "luvan-diaarinumero"                  VARCHAR(32),
  osoite                                tr_osoite,
  "tien-nimi"                           VARCHAR(256),
  kunnat                                VARCHAR,
  "alkusijainnin-kuvaus"                VARCHAR,
  "loppusijainnin-kuvaus"               VARCHAR,

  alku                                  TIMESTAMP,
  loppu                                 TIMESTAMP,
  tyoajat                               tietyon_tyoaika [],

  vaikutussuunta                        tietyon_vaikutussuunta,
  kaistajarjestelyt                     tietyon_kaistajarjestelyt,

  nopeusrajoitukset                     tietyon_nopeusrajoitus [],
  tienpinnat                            tietyon_tienpinta [],

  "kiertotien-mutkaisuus"               tietyon_mutkat,
  kiertotienpinnat                      tietyon_tienpinta [],

  liikenteenohjaus                      tietyon_liikenteenohjaus,
  liikenteenohjaaja                     tietyon_liikenteenohjaaja,

  "viivastys-normaali-liikenteessa"     INTEGER,
  "viivastys-ruuhka-aikana"             INTEGER,

  ajoneuvorajoitukset                   tietyon_ajoneuvorajoitukset,

  huomautukset                          tietyon_huomautukset[],
  "ajoittaiset-pysatykset"              BOOLEAN,
  "ajoittain-suljettu-tie"              BOOLEAN,
  "pysaytysten-alku"                    TIMESTAMP,
  "pysaytysten-loppu"                   TIMESTAMP,
  lisatietoja                           TEXT,
  "urakoitsija-id"                      INTEGER REFERENCES organisaatio (id),
  "urakoitsijan-nimi"                   VARCHAR(128)
  );

-- FIXME: tyoaika ei ole timestamp-timestamp, vaan alku- ja loppukellonaika
