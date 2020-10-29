-- Vähä inserttejä helpottavaa settia
--DROP TABLE pot2;
--DROP TABLE pot2_massa;
--DROP TABLE pot2_massatyyppi;
--DROP TABLE pot2_runkoainetyyppi;
--DROP TABLE pot2_sideainetyyppi;
--DROP TABLE pot2_lisaainetyyppi;
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


CREATE TABLE pot2_alusta_toimenpide
(
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT
);
INSERT INTO pot2_alusta_toimenpide (nimi, lyhenne, koodi)
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

CREATE TABLE pot2_massatyyppi (
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT);
INSERT INTO pot2_massatyyppi (nimi, lyhenne, koodi)
VALUES ('BET, Betoni', 'BET', 1),
       ('AA, Avoin asfaltti', 'AA', 11),
       ('AB, Asfalttibetoni', 'AB', 12),
       ('EAB, Asfalttibetoni', 'EAB', 566), --- koodi > 565 = koodi ei tiedossa
       ('ABtiivis', 'ABtiivis', 567),
       ('EA, Epäjatkuva asfaltti (poistunut)', 'EA', 13),
       ('SMA, Kivimastiksiasfaltti', 'SMA', 14),
       ('ABK, Kantavan kerroksen AB', 'ABK', 15),
       ('EABK, Kantavan kerroksen EAB', 'EABK', 568),
       ('ABS, Sidekerroksen AB', 'ABS', 16),
       ('VA, Valuasfaltti', 'VA', 17),
       ('PAB-B, Pehmeät asfalttibetonit', 'PAB-B', 21),
       ('EPAB-B, Pehmeät E asfalttibetonit', 'EPAB-B', 569),
       ('PAB-V, Pehmeät asfalttibetonit', 'PAB-V', 22),
       ('EPAB-V, Pehmeät asfalttibetonit', 'EPAB-V', 570),
       ('PAB-O, Pehmeät asfalttibetonit', 'PAB-O', 23),
       ('Komposiittiasfaltti', 'Komposiittiasfaltti', 571),
       ('Pehmeät asfalttibetonit', 'Pehmeät asfalttibetonit', 20),
       ('Kovat asfalttibetonit', 'Kovat asfalttibetonit', 10),
       ('Ei tietoa', 'Ei tietoa', 99);

CREATE TABLE pot2_massa
(
    id                SERIAL PRIMARY KEY,
    urakka_id         INTEGER NOT NULL REFERENCES urakka (id),
    tyyppi            INTEGER NOT NULL REFERENCES pot2_massatyyppi(koodi),
    nimen_tarkenne    TEXT,
    max_raekoko       INTEGER NOT NULL CHECK (max_raekoko IN (5, 8, 11, 16, 22, 31)),
    kuulamyllyluokka  kuulamyllyluokka NOT NULL,
    litteyslukuluokka INTEGER NOT NULL,
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


CREATE TABLE pot2_mursketyyppi
(
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT
);
INSERT INTO pot2_mursketyyppi (nimi, lyhenne, koodi)
VALUES
('Kalliomurske', 'KaM', 1),
('Soramurske', 'SoM', 2),
('(Uusio) RA, Asfalttirouhe', 'Asf.rouhe', 3),
('Muu', 'Muu', 4);

CREATE TYPE murskeen_rakeisuus AS ENUM
    ('0/32', '0/40', '0/45', '0/56', '0/63');

CREATE TYPE iskunkestavyys AS ENUM
    ('LA30', 'LA35', 'LA40');

CREATE TABLE pot2_murske
(
    id                SERIAL PRIMARY KEY,
    urakka_id         INTEGER NOT NULL REFERENCES urakka (id),
    nimi              TEXT,
    nimen_tarkenne    TEXT,
    tyyppi            INTEGER NOT NULL REFERENCES pot2_mursketyyppi(koodi),
    esiintyma         TEXT NOT NULL,
    rakeisuus         murskeen_rakeisuus,
    iskunkestavyys    iskunkestavyys,
    DoP_nro           TEXT, -- ei ole kaikentyyppisillä murskeilla, täten nullable

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

CREATE TABLE pot2_runkoainetyyppi (
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT);
INSERT INTO pot2_runkoainetyyppi (nimi, lyhenne, koodi)
VALUES ('Kiviaines', 'Kiviaines', 1),
       ('Asfalttirouhe', 'Asf.rouhe', 2),
       ('Erikseen lisättävä fillerikiviaines', 'Filleri', 3),
       ('Masuunikuonajauhe', 'MaKu', 4),
       ('Ferrokromikuona (OKTO)', 'FKu', 5),
       ('Teräskuona', 'TeKu', 6),
       ('Muu', 'Muu', 7);

CREATE TYPE fillerityyppi AS ENUM
    ('Kalkkifilleri (KF)', 'Lentotuhka (LT)', 'Muu fillerikiviaines');

CREATE TABLE pot2_massa_runkoaine
(
    id SERIAL PRIMARY KEY,
    pot2_massa_id INTEGER NOT NULL REFERENCES pot2_massa (id),
    tyyppi INTEGER NOT NULL REFERENCES pot2_runkoainetyyppi(koodi),
    esiintyma    TEXT,
    fillerityyppi fillerityyppi,
    kuvaus TEXT,
    kuulamyllyarvo NUMERIC(3,1),
    litteysluku NUMERIC(3,1),
    massaprosentti INTEGER
);
CREATE INDEX pot2_massa_runkoaine_idx ON pot2_massa_runkoaine (pot2_massa_id);

CREATE TABLE pot2_sideainetyyppi(
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT);
INSERT INTO pot2_sideainetyyppi(nimi, lyhenne, koodi)
VALUES ('Bitumi, 20/30', 'Bitumi, 20/30', 1),
       ('Bitumi, 35/50', 'Bitumi, 35/50', 2),
       ('Bitumi, 50/70', 'Bitumi, 50/70', 3),
       ('Bitumi, 70/100', 'Bitumi, 70/100', 4),
       ('Bitumi, 100/150', 'Bitumi, 100/150', 5),
       ('Bitumi, 160/220', 'Bitumi, 160/220', 6),
       ('Bitumi, 250/330', 'Bitumi, 250/330', 7),
       ('Bitumi, 330/430', 'Bitumi, 330/430', 8),
       ('Bitumi, 500/650', 'Bitumi, 500/650', 9),
       ('Bitumi, 650/900', 'Bitumi, 650/900', 10),
       ('Bitumi, V1500', 'Bitumi, V1500', 11),
       ('Bitumi, V3000', 'Bitumi, V3000', 12),
       ('Polymeerimodifioitu bitumi, PMB 75/130-65',
        'Polymeerimodifioitu bitumi, PMB 75/130-65', 13),
       ('Polymeerimodifioitu bitumi, PMB 75/130-70',
        'Polymeerimodifioitu bitumi, PMB 75/130-70', 14),
       ('Polymeerimodifioitu bitumi, PMB 40/100-70',
        'Polymeerimodifioitu bitumi, PMB 40/100-70', 15),
       ('Polymeerimodifioitu bitumi, PMB 40/100-75',
        'Polymeerimodifioitu bitumi, PMB 40/100-75', 16),
       ('Bitumiliuokset ja fluksatut bitumit, BL0',
        'Bitumiliuokset ja fluksatut bitumit, BL0', 17),
       ('Bitumiliuokset ja fluksatut bitumit, BL5',
        'Bitumiliuokset ja fluksatut bitumit, BL5', 18),
       ('Bitumiliuokset ja fluksatut bitumit, BL2Bio',
        'Bitumiliuokset ja fluksatut bitumit, BL2Bio', 19),
       ('Bitumiemulsiot, BE-L', 'Bit.emuls., BE-L', 20),
       ('Bitumiemulsiot, PBE-L', 'Bit.emuls., PBE-L', 21),
       ('Bitumiemulsiot, BE-SIP', 'Bit.emuls., BE-SIP', 22),
       ('Bitumiemulsiot, BE-SOP', 'Bit.emuls., BE-SOP', 23),
       ('Bitumiemulsiot, BE-AB', 'Bit.emuls., BE-AB', 24),
       ('Bitumiemulsiot, BE-PAB', 'Bit.emuls., BE-PAB', 25),
       ('KF, Kalkkifilleri', 'KF', 26),
       ('Muu, erikoisbitumi', 'Muu, erik.bit.', 27);

CREATE TABLE pot2_massa_sideaine
(
    id            SERIAL PRIMARY KEY,
    pot2_massa_id INTEGER NOT NULL REFERENCES pot2_massa (id),
    "lopputuote?" BOOLEAN DEFAULT TRUE, -- FALSE = lisätty sideaine
    tyyppi        INTEGER NOT NULL REFERENCES  pot2_sideainetyyppi(koodi),
    pitoisuus     NUMERIC(3,1)
);
CREATE INDEX pot2_massa_sideaine_idx ON pot2_massa_sideaine (pot2_massa_id);

CREATE TABLE pot2_lisaainetyyppi(
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT);
INSERT INTO pot2_lisaainetyyppi(nimi, lyhenne, koodi)
VALUES ('Kuitu', 'Kuitu', 1),
       ('Tartuke', 'Tartuke', 2),
       ('Sementti', 'Sementti', 3),
       ('Bitumikaterouhe', 'Bitumikaterouhe', 4),
       ('Kumi- tai muovirouhe', 'Kumi- tai muovirouhe', 5),
       ('Väriaine', 'Väriaine', 6),
       ('Muu kemiallinen aine','Muu kemiallinen aine', 7);


CREATE TABLE pot2_massa_lisaaine(
    id            SERIAL PRIMARY KEY,
    pot2_massa_id INTEGER NOT NULL REFERENCES pot2_massa (id),
    tyyppi        INTEGER NOT NULL REFERENCES pot2_lisaainetyyppi(koodi),
    pitoisuus     NUMERIC(3,1)
);
CREATE INDEX pot2_massa_lisaaine_idx ON pot2_massa_lisaaine (pot2_massa_id);

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

CREATE TABLE pot2_alusta
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
    toimenpide           INTEGER NOT NULL REFERENCES pot2_alusta_toimenpide(koodi),
    toimenpide_tiedot    TEXT,
    materiaali           INTEGER NOT NULL REFERENCES pot2_murske(id)
);
CREATE INDEX pot2_alusta_idx ON pot2_alusta (pot2_id);
COMMENT ON TABLE pot2_alusta IS
    E'Päällysteen alusta. Aiemmin, eli POT1-aikana suuri osa tästä tiedosta löytyi taulusta paallystysilmoitus.ilmoitustiedot.alustatoimet. Tyypillisesti sis. mm. murskeita, verkkoja, näihin liittyviä toimenpiteitä';