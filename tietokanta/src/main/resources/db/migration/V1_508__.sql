CREATE TYPE tietyotyyppi AS ENUM (
  'Tienrakennus',
  'Päällystystyö',
  'Viimeistely',
  'Rakenteen parannus',
  'Jyrsintä-/stabilointityö',
  'Tutkimus/mittaus',
  'Alikulkukäytävän rak.',
  'Kaidetyö',
  'Tienvarsilaitteiden huolto',
  'Kevyenliik. väylän rak.',
  'Kaapelityö',
  'Silmukka-anturin asent.',
  'Siltatyö',
  'Valaistustyö',
  'Tasoristeystyö',
  'Liittymä- ja kaistajärj.',
  'Tiemerkintätyö',
  'Vesakonraivaus/niittotyö',
  'Räjäytystyö',
  'Muu, mikä?' );

CREATE TYPE tietyon_tyypin_kuvaus AS (tyyppi tietyotyyppi, kuvaus VARCHAR(256));

CREATE TYPE viikonpaiva AS ENUM (
  'maanantai',
  'tiistai',
  'keskiviikko',
  'torstai',
  'perjantai',
  'lauantai',
  'sunnuntai');

CREATE TYPE tietyon_tyoaika AS (alku TIMESTAMP, loppu TIMESTAMP, paivat viikonpaiva []);

CREATE TYPE tietyon_vaikutussuunta AS ENUM (
  'molemmat',
  'tienumeronKasvusuuntaan',
  'vastenTienumeronKasvusuuntaa');

CREATE TYPE tietyon_kaistajarjestelyt AS ENUM (
  'ajokaistaSuljettu',
  'ajorataSuljettu',
  'tieSuljettu');

CREATE TYPE nopeusrajoitus AS ENUM (
  '30',
  '40',
  '50',
  '60',
  '70',
  '80',
  '90',
  '100');

CREATE TYPE tietyon_nopeusrajoitus AS (rajoitus nopeusrajoitus, matka INTEGER);

CREATE TYPE tietyon_pintamateriaalit AS ENUM (
  'paallystetty',
  'jyrsitty',
  'murske');

CREATE TYPE tietyon_tienpinta AS (materiaali tietyon_pintamateriaalit, matka INTEGER);

CREATE TYPE tietyon_mutkat AS ENUM (
  'loivatMutkat',
  'jyrkatMutkat');

CREATE TYPE tietyon_liikenteenohjaus AS ENUM (
  'ohjataanVuorotellen',
  'ohjataanKaksisuuntaisena');

CREATE TYPE tietyon_liikenteenohjaaja AS ENUM (
  'liikennevalot',
  'liikenteenohjaaja');

CREATE TYPE tietyon_huomautukset AS ENUM (
  'avotuli',
  'tyokoneitaLiikenteenSeassa');

CREATE TABLE tietyoilmoitus (
  id                                    SERIAL PRIMARY KEY,
  tloik_id                              INTEGER,
  paatietyoilmoitus                     INTEGER,
  tloik_paatietyoilmoitus_id            INTEGER,

  luotu                                 TIMESTAMP,
  luoja                                 INTEGER REFERENCES kayttaja (id),
  muokattu                              TIMESTAMP,
  muokkaaja                             INTEGER REFERENCES kayttaja (id),
  poistettu                             TIMESTAMP,
  poistaja                              INTEGER REFERENCES kayttaja (id),

  ilmoittaja                            INTEGER REFERENCES kayttaja (id),
  ilmoittaja_etunimi                    VARCHAR(32),
  ilmoittaja_sukunimi                   VARCHAR(32),
  ilmoittaja_matkapuhelin               VARCHAR(32),
  ilmoittaja_sahkoposti                 VARCHAR(64),

  urakka                                INTEGER REFERENCES urakka (id),
  urakka_nimi                           VARCHAR(256),
  urakkatyyppi                          urakkatyyppi,

  urakoitsijayhteyshenkilo              INTEGER REFERENCES kayttaja (id),
  urakoitsijayhteyshenkilo_etunimi      VARCHAR(32),
  urakoitsijayhteyshenkilo_sukunimi     VARCHAR(32),
  urakoitsijayhteyshenkilo_matkapuhelin VARCHAR(32),
  urakoitsijayhteyshenkilo_sahkoposti   VARCHAR(64),

  tilaaja                               INTEGER REFERENCES organisaatio (id),
  tilaajan_nimi                         VARCHAR(128),

  tilaajayhteyshenkilo                  INTEGER REFERENCES kayttaja (id),
  tilaajayhteyshenkilo_etunimi          VARCHAR(32),
  tilaajayhteyshenkilo_sukunimi         VARCHAR(32),
  tilaajayhteyshenkilo_matkapuhelin     VARCHAR(32),
  tilaajayhteyshenkilo_sahkoposti       VARCHAR(64),

  tyotyypit                             tietyon_tyypin_kuvaus [],
  luvan_diaarinumero                    INTEGER,
  sijainti                              GEOMETRY NOT NULL,
  tr_numero                             INTEGER,
  tr_alkuosa                            INTEGER,
  tr_alkuetaisyys                       INTEGER,
  tr_loppuosa                           INTEGER,
  tr_loppuetaisyys                      INTEGER,
  tien_nimi                             VARCHAR(256),
  kunnat                                VARCHAR,
  alkusijainnin_kuvaus                  VARCHAR,
  loppusijainnin_kuvaus                 VARCHAR,

  alku                                  TIMESTAMP,
  loppu                                 TIMESTAMP,
  tyoajat                               tietyon_tyoaika [],

  vaikutussuunta                        tietyon_vaikutussuunta,
  kaistajarjestelyt                     tietyon_kaistajarjestelyt,

  nopeusrajoitukset                     tietyon_nopeusrajoitus [],
  tienpinnat                            tietyon_tienpinta [],

  kiertotien_mutkaisuus                 tietyon_mutkat,
  kiertotienpinnat                      tietyon_tienpinta [],

  liikenteenohjaus                      tietyon_liikenteenohjaus,
  liikenteenohjaaja                     tietyon_liikenteenohjaaja,

  viivastys_normaali_liikenteessa       INTEGER,
  viivastys_ruuhka_aikana               INTEGER,

  ajoneuvo_max_korkeus                  DECIMAL,
  ajoneuvo_max_leveys                   DECIMAL,
  ajoneuvo_max_pituus                   DECIMAL,
  ajoneuvo_max_paino                    DECIMAL,

  huomautukset                          tietyon_huomautukset,
  ajoittaiset_pysatykset                BOOLEAN,
  ajoittain_suljettu_tie                BOOLEAN,
  pysaytysten_alku                      TIMESTAMP,
  pysaytysten_loppu                     TIMESTAMP,
  lisatietoja                           TEXT,

  FOREIGN KEY (paatietyoilmoitus) REFERENCES tietyoilmoitus (id));
