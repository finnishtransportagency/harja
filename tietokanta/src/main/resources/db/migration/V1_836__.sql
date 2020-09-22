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
    yllapitokohde       INTEGER NOT NULL REFERENCES yllapitokohde (id),

    takuupvm            DATE,
    tila                PAALLYSTYSTILA,
    paatos_tekninen_osa PAALLYSTYSILMOITUKSEN_PAATOSTYYPPI,
    lahetetty_yhaan     TIMESTAMP,

    -- muokkausmetatiedot
    poistettu           BOOLEAN DEFAULT FALSE,
    muokkaaja           INTEGER REFERENCES kayttaja (id),
    muokattu            TIMESTAMP,
    luoja               INTEGER REFERENCES kayttaja (id),
    luotu               TIMESTAMP DEFAULT NOW()
);
CREATE INDEX pot2_yllapitokohde_idx ON pot2 (yllapitokohde);

CREATE TYPE kuulamyllyluokka AS ENUM
    ('AN5', 'AN7', 'AN10', 'AN14', 'AN19', 'AN30', 'AN22', 'Ei kuulamyllyä');

CREATE TABLE pot2_massa
(
    id                SERIAL PRIMARY KEY,
    urakka_id         INTEGER NOT NULL REFERENCES urakka (id),
    nimi              TEXT,
    massatyyppi       TEXT NOT NULL,
    max_raekoko       INTEGER NOT NULL CHECK (max_raekoko IN (5, 8, 11, 16, 22, 31)),
    asfalttiasema     TEXT,
    kuulamyllyluokka  kuulamyllyluokka NOT NULL,
    litteyslukuluokka TEXT,
    DoP_nro           TEXT, --Pelkäänpä että DoP_nroa ei ole aina saatavilla heti, joten NOT NULL olisi vaarallinen käyttäjälähtöisyyden näkökulmasta?

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
    pot2_id              INTEGER NOT NULL REFERENCES pot2 (id),
    kohdeosa_id          INTEGER NOT NULL REFERENCES yllapitokohdeosa (id),
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

CREATE INDEX pot2_paallystystiedot_idx ON pot2_paallystystiedot (pot2_id);

CREATE TABLE pot2_massa_runkoaine
(
    id                 SERIAL PRIMARY KEY,
    pot2_massa_id      INTEGER NOT NULL REFERENCES pot2_massa (id),
    kiviaine_esiintyma TEXT,
    kuulamyllyarvo     NUMERIC(3,1),
    muotoarvo          NUMERIC(3,1),
    massaprosentti     INTEGER,
    erikseen_lisattava_fillerikiviaines TEXT -- Kalkkifilleri (KF), Lentotuhka (LT), Muu fillerikiviaines
);
CREATE INDEX pot2_massa_runkoaine_idx ON pot2_massa_runkoaine (pot2_massa_id);


CREATE TABLE pot2_massa_sideaine
(
    id            SERIAL PRIMARY KEY,
    pot2_massa_id INTEGER NOT NULL REFERENCES pot2_massa (id),
    tyyppi        TEXT,
    pitoisuus     NUMERIC,
    "lopputuote?"   BOOLEAN DEFAULT FALSE
);
CREATE INDEX pot2_massa_sideaine_idx ON pot2_massa_sideaine (pot2_massa_id);

CREATE TABLE pot2_massa_lisaaine
(
    id            SERIAL PRIMARY KEY,
    pot2_massa_id INTEGER NOT NULL REFERENCES pot2_massa (id),
    nimi          TEXT,
    pitoisuus     NUMERIC
);
CREATE INDEX pot2_massa_lisaaine_idx ON pot2_massa_lisaaine (pot2_massa_id);

CREATE TABLE pot2_runkoaine
(
    id SERIAL PRIMARY KEY,
    nimi    TEXT NOT NULL,
    "kuulamyllyarvo?" BOOLEAN,
    "litteysluku?" BOOLEAN,
    "massaprosentti?" BOOLEAN

);
INSERT INTO pot2_runkoaine (nimi, "kuulamyllyarvo?", "litteysluku?", "massaprosentti?")
    VALUES ('Kiviaines', TRUE, TRUE, TRUE);
INSERT INTO pot2_runkoaine (nimi, "kuulamyllyarvo?", "litteysluku?", "massaprosentti?")
    VALUES ('Asfalttirouhe', TRUE, TRUE, TRUE);
INSERT INTO pot2_runkoaine (nimi, "kuulamyllyarvo?", "litteysluku?", "massaprosentti?")
    VALUES ('Erikseen lisättävä filleriaines', FALSE, FALSE, TRUE);
INSERT INTO pot2_runkoaine (nimi, "kuulamyllyarvo?", "litteysluku?", "massaprosentti?")
    VALUES ('Maku, Masuunikuonajauhe', TRUE, TRUE, TRUE);
INSERT INTO pot2_runkoaine (nimi, "kuulamyllyarvo?", "litteysluku?", "massaprosentti?")
    VALUES ('Fku, Ferrokromikuona (OKTO)', TRUE, TRUE, TRUE);
INSERT INTO pot2_runkoaine (nimi, "kuulamyllyarvo?", "litteysluku?", "massaprosentti?")
    VALUES ('TeKu, Teräskuona', TRUE, TRUE, TRUE);
INSERT INTO pot2_runkoaine (nimi, "kuulamyllyarvo?", "litteysluku?", "massaprosentti?")
    VALUES ('Muu', FALSE, FALSE, TRUE);
