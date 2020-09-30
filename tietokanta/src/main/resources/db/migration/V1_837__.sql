-- Vähä inserttejä helpottavaa settia
--DROP TABLE pot2;
--DROP TABLE pot2_massa;
--DROP TABLE pot2_murske;
--DROP TABLE pot2_kulutuskerros_toimenpide;
--DROP TABLE pot2_paallystekerros;
--DROP TABLE pot2_massa_runkoaine;
--DROP TABLE pot2_massa_lisaaine;
--DROP TABLE pot2_massa_sideaine;


CREATE TABLE pot2_kulutuskerros_toimenpide
(
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT
);
INSERT INTO pot2_kulutuskerros_toimenpide (nimi, lyhenne, koodi)
VALUES
('Paksuudeltaan vakio laatta', 'LTA', 12),
('Massapintaus', 'MP', 21),
('Kuumennuspintaus', 'MPK', 22),
('MP kuumalle, kuumajyrsitylle tas. pinnalle', 'MPKJ', 23),
('REMIX-pintaus', 'REM', 31),
('2-kerroksinen remix-pintaus', 'REM+', 32),
('PAB-O/V:n remix-pintaus', 'REMO', 33),
('ART-pintaus', 'ART', 34),
('Novachip-massapintaus', 'NC', 35),
('Karhinta', 'KAR', 41),
('Hienojyrsintä', 'HJYR', 51),
('Sirotepintaus', 'SIP', 61),
('Urapaikkaus', 'UP', 71),
('Uraremix', 'UREM', 72),
-- koodit puuttuvat! tarkistettava Velhosta tai YHA:st,
('Soratien pintaus', 'SOP', 666),
('Pientareen päällystys', 'Piennar', 667);


CREATE TABLE pot2_kantava_kerros_toimenpide
(
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT
);
INSERT INTO pot2_kantava_kerros_toimenpide (nimi, lyhenne, koodi)
VALUES
('Massanvaihto', 'MV', 1),
('Bitumiemusiostabilointi', 'BEST', 11),
('Vaahtobitumistabilointi', 'VBST', 12),
('Remix-stabilointi', 'REST', 13),
('Sementtistabilointi', 'SST', 14),
('Masuunihiekkastabilointi', 'MHST', 15),
('Komposiittistabilointi', 'KOST', 16),
('Murske', 'MS', 23),
('Sekoitusjyrsintä', 'SJYR', 24),
('Kuumennustasaus', 'TASK', 31),
('Massatasaus', 'TAS', 32),
('Tasausjyrsintä', 'TJYR', 41),
('Laatikkojyrsintä', 'LJYR', 42),
('Reunajyrsintä', 'RJYR', 43),
-- Uusia koodeja, joita ei ole varmistettu YHA:N tai Velhon kanssa
('REMIX-tasaus', 'REM-TAS', 666),
('Verkko (teräs)', 'Verkko (teräs)', 667),
('Verkko (lasikuitu)', 'Verkko (lasikuitu)', 668),
('Verkko (muovi)', 'Verkko (muovi)', 669),
('Verkko (lujitekangas)', 'Verkko (lujitekangas)', 670),
('Verkko (muu)', 'Verkko (muu)', 671);




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
    DoP_nro           TEXT, -- DoP_nroa ei ole aina saatavilla heti? Siksi ei NOT NULL

    -- muokkausmetatiedot
    poistettu         BOOLEAN   DEFAULT FALSE,
    muokkaaja         INTEGER REFERENCES kayttaja (id),
    muokattu          TIMESTAMP,
    luoja             INTEGER REFERENCES kayttaja (id),
    luotu             TIMESTAMP DEFAULT NOW()
);
CREATE INDEX massa_urakka_idx ON pot2_massa (urakka_id);
COMMENT ON TABLE pot2_massa IS
    E'Uusien päällystysilmoitusten hallinnassa käytettävän materiaalikirjaston taulu, jonne kirjataan urakan päällystemassat ja niiden ominaisuudet.';

CREATE TABLE pot2_murske
(
    id                SERIAL PRIMARY KEY,
    urakka_id         INTEGER NOT NULL REFERENCES urakka (id),
    nimi              TEXT,
    massatyyppi       TEXT NOT NULL,
    max_raekoko       INTEGER NOT NULL CHECK (max_raekoko IN (5, 8, 11, 16, 22, 31)),
    asfalttiasema     TEXT,
    kuulamyllyluokka  kuulamyllyluokka NOT NULL,
    litteyslukuluokka TEXT,
    DoP_nro           TEXT, -- DoP_nroa ei ole aina saatavilla heti? Siksi ei NOT NULL

    -- muokkausmetatiedot
    poistettu         BOOLEAN   DEFAULT FALSE,
    muokkaaja         INTEGER REFERENCES kayttaja (id),
    muokattu          TIMESTAMP,
    luoja             INTEGER REFERENCES kayttaja (id),
    luotu             TIMESTAMP DEFAULT NOW()
);
CREATE INDEX murske_urakka_idx ON pot2_murske (urakka_id);
COMMENT ON TABLE pot2_murske IS
    E'Uusien päällystysilmoitusten hallinnassa käytettävän materiaalikirjaston taulu, jonne kirjataan urakan murskeet ja niiden ominaisuudet.';

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
    poistettu           BOOLEAN NOT NULL DEFAULT FALSE,
    muokkaaja           INTEGER REFERENCES kayttaja (id),
    muokattu            TIMESTAMP,
    luoja               INTEGER NOT NULL REFERENCES kayttaja (id),
    luotu               TIMESTAMP DEFAULT NOW()
);
CREATE INDEX pot2_yllapitokohde_idx ON pot2 (yllapitokohde);
COMMENT ON TABLE pot2 IS
    E'Uusien päällystysilmoitusten taulu, tarkoitus ottaa tuotantokäyttöön kesällä 2021.';

-- Päällystekerrostaulua käytetään kulutuskerrokseen sekä mahdolliseen alempaan päällystekerrokseen
CREATE TABLE pot2_paallystekerros
(
    id                   SERIAL PRIMARY KEY,
    kohdeosa_id          INTEGER NOT NULL REFERENCES yllapitokohdeosa (id),
    pot2_id              INTEGER NOT NULL REFERENCES pot2 (id),
    tr_numero            INTEGER NOT NULL CHECK (tr_numero > 0),
    tr_alkuetaisyys      INTEGER NOT NULL CHECK (tr_alkuetaisyys > 0),
    tr_alkuosa           INTEGER NOT NULL CHECK (tr_alkuosa > 0),
    tr_loppuetaisyys     INTEGER NOT NULL CHECK (tr_loppuetaisyys > 0),
    tr_loppuosa          INTEGER NOT NULL CHECK (tr_loppuosa > 0),
    tr_ajorata           INTEGER NOT NULL CHECK (tr_ajorata > 0),
    tr_kaista            INTEGER NOT NULL CHECK (tr_kaista > 0),
    toimenpide           INTEGER NOT NULL REFERENCES pot2_kulutuskerros_toimenpide(koodi),
    materiaali           INTEGER NOT NULL REFERENCES pot2_massa(id),

    leveys NUMERIC,
    massamenekki         NUMERIC,
    pinta_ala NUMERIC,
    kokonaismassamaara NUMERIC,
    piennar BOOLEAN NOT NULL DEFAULT FALSE,
    lisatieto TEXT
);
CREATE INDEX pot2_paallystekerros_kohdeosa_idx ON pot2_paallystekerros (pot2_id);
COMMENT ON TABLE pot2_paallystekerros IS
    E'Päällysteen kulutuskerros tai alempi päällystekerros. Aiemmin, eli POT1-aikana suuri osa tästä tiedosta löytyi taulusta paallystysilmoitus.osoitteet. Tyypillisesti sis. mm. päällystemassoja ja näihin liittyviä toimenpiteitä.';

CREATE TABLE pot2_kantava_kerros
(
    id                   SERIAL PRIMARY KEY,
    pot2_id              INTEGER NOT NULL REFERENCES pot2 (id),
    tr_numero            INTEGER NOT NULL CHECK (tr_numero > 0),
    tr_alkuetaisyys      INTEGER NOT NULL CHECK ( tr_alkuetaisyys > 0),
    tr_alkuosa           INTEGER NOT NULL CHECK ( tr_alkuosa > 0),
    tr_loppuetaisyys     INTEGER NOT NULL CHECK ( tr_loppuetaisyys > 0),
    tr_loppuosa          INTEGER NOT NULL CHECK ( tr_loppuosa > 0),
    tr_ajorata           INTEGER NOT NULL CHECK ( tr_ajorata > 0),
    tr_kaista            INTEGER NOT NULL CHECK ( tr_kaista > 0),
    toimenpide           INTEGER NOT NULL REFERENCES pot2_kantava_kerros_toimenpide(koodi),
    toimenpide_tiedot    TEXT,
    materiaali           INTEGER NOT NULL REFERENCES pot2_murske(id)
);
CREATE INDEX pot2_kantava_kerros_idx ON pot2_kantava_kerros (pot2_id);
COMMENT ON TABLE pot2_kantava_kerros IS
    E'Päällysteen kantava kerros. Aiemmin, eli POT1-aikana suuri osa tästä tiedosta löytyi taulusta paallystysilmoitus.ilmoitustiedot.alustatoimet. Tyypillisesti sis. mm. murskeita, verkkoja, näihin liittyviä toimenpiteitä';