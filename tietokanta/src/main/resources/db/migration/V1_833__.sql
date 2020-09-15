-- Vähä inserttejä helpottavaa settia
--DROP TABLE pot2;
--DROP TABLE pot2_massa;
--DROP TABLE pot2_tyomenetelma;
--DROP TABLE pot2_paallystystiedot;
--DROP TABLE pot2_massa_runkoaine;
--DROP TABLE pot2_massa_lisaaine;
--DROP TABLE pot2_massa_sideaine;


-- Päällystysilmoitus2 eli POT2
CREATE TABLE pot2
(
    id                  SERIAL PRIMARY KEY,
    urakka_id           INTEGER REFERENCES urakka (id),
    yllapitokohde       INTEGER REFERENCES yllapitokohde (id),

    takuupvm            DATE,
    tila                PAALLYSTYSTILA,
    paatos_tekninen_osa PAALLYSTYSILMOITUKSEN_PAATOSTYYPPI,
    lahetetty_yhaan     TIMESTAMP,

    -- muokkausmetatiedot
    poistettu           BOOLEAN   DEFAULT FALSE,
    muokkaaja           INTEGER REFERENCES kayttaja (id),
    muokattu            TIMESTAMP,
    luoja               INTEGER REFERENCES kayttaja (id),
    luotu               TIMESTAMP DEFAULT NOW()
);
CREATE INDEX pot2_urakka_idx ON pot2 (urakka_id);

CREATE TABLE pot2_massa
(
    id                SERIAL PRIMARY KEY,
    urakka_id         INTEGER REFERENCES urakka (id),
    pot2_id           INTEGER REFERENCES pot2 (id),
    nimi              TEXT,
    massatyyppi       TEXT,
    max_raekoko       INTEGER,
    asfalttiasema     TEXT,
    kuulamyllyluokka  TEXT,
    litteyslukuluokka TEXT,
    DoP_nro           NUMERIC,

    -- muokkausmetatiedot
    poistettu         BOOLEAN   DEFAULT FALSE,
    muokkaaja         INTEGER REFERENCES kayttaja (id),
    muokattu          TIMESTAMP,
    luoja             INTEGER REFERENCES kayttaja (id),
    luotu             TIMESTAMP DEFAULT NOW()
);
CREATE INDEX massa_urakka_idx ON pot2_massa (urakka_id);

CREATE TABLE pot2_tyomenetelma
(
    koodi   SERIAL PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT
);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Paksuudeltaan vakio laatta', 'LTA', 12);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Massapintaus', 'MP', 21);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Kuumennuspintaus', 'MPK', 22);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('MP kuumalle, kuumajyrsitylle tas. pinnalle', 'MPKJ', 23);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('REMIX-pintaus', 'REM', 31);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('2-kerroksinen remix-pintaus', 'REM+', 32);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('PAB-O/V:n remix-pintaus', 'REMO', 33);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('ART-pintaus', 'ART', 34);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Novachip-massapintaus', 'NC', 35);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Karhinta', 'KAR', 41);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Hienojyrsintä', 'HJYR', 51);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Sirotepintaus', 'SIP', 61);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Urapaikkaus', 'UP', 71);
INSERT INTO pot2_tyomenetelma (nimi, lyhenne, koodi)
    VALUES ('Uraremix', 'UREM', 72);

CREATE TABLE pot2_paallystystiedot
(
    id                   SERIAL PRIMARY KEY,
    pot2_id              INTEGER REFERENCES pot2 (id),
    kohdeosa_id          INTEGER REFERENCES yllapitokohdeosa (id),
    kuulamyllyarvo       NUMERIC,
    raekoko              INTEGER,
    esiintyma            TEXT,
    muotoarvo            NUMERIC,
    pitoisuus            NUMERIC,
    lisaineet            TEXT,
    massamenekki         NUMERIC,
    pot2_tyomenetelma_id INTEGER,
    sideainetyyppi       INTEGER,
    paallystetyyppi      INTEGER,
    verkkotyyppi         INTEGER,
    verkon_sijainti      INTEGER,
    verkon_tarkoitus     INTEGER,
    kasittelymenetelma   INTEGER,
    tekninen_toimenpide  INTEGER,
    tr_alkuetaisyys      INTEGER,
    tr_alkuosa           INTEGER,
    tr_loppuetaisyys     INTEGER,
    tr_loppuosa          INTEGER,
    tr_ajorata           INTEGER,
    tr_kaista            INTEGER
);

CREATE INDEX pot2_paallystystiedot_idx ON pot2_massa (pot2_id);

CREATE TABLE pot2_massa_runkoaine
(
    id                 SERIAL PRIMARY KEY,
    pot2_massa_id      INTEGER REFERENCES pot2_massa (id),
    kiviaine_esiintyma TEXT,
    kuulamyllyarvo     NUMERIC,
    muotoarvo          NUMERIC,
    massaprosentti     NUMERIC,
    erikseen_lisattava_fillerikiviaines TEXT -- Kalkkifilleri (KF), Lentotuhka (LT), Muu fillerikiviaines
);
CREATE INDEX pot2_massa_runkoaine_idx ON pot2_massa_runkoaine (pot2_massa_id);

CREATE TABLE pot2_massa_lisaaine
(
    id            SERIAL PRIMARY KEY,
    pot2_massa_id INTEGER REFERENCES pot2_massa (id),
    nimi          TEXT,
    pitoisuus     NUMERIC
);
CREATE INDEX pot2_massa_lisaaine_idx ON pot2_massa_lisaaine (pot2_massa_id);

CREATE TABLE pot2_massa_sideaine
(
    id            SERIAL PRIMARY KEY,
    pot2_massa_id INTEGER REFERENCES pot2_massa (id),
    tyyppi        TEXT,
    pitoisuus     NUMERIC,
    "lopputuote?"   BOOLEAN DEFAULT FALSE
);
CREATE INDEX pot2_massa_sideaine_idx ON pot2_massa_sideaine (pot2_massa_id);