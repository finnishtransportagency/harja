-- Lisää verkojen tiedot pot2_alusta tauluun


CREATE TABLE pot2_verkon_tyyppi (
    koodi   INTEGER PRIMARY KEY NOT NULL,
    nimi    TEXT NOT NULL,
    lyhenne TEXT
);

INSERT INTO pot2_verkon_tyyppi (koodi, nimi)
VALUES
  (1, 'Teräsverkko'),
  (2, 'Lasikuituverkko'),
  (3, 'Muoviverkko'),
  (4, 'Lujitekangas'),
  (5, 'Suodatinkangas'),
  (9, 'Muu');

CREATE TABLE pot2_verkon_tarkoitus (
    koodi   INTEGER PRIMARY KEY NOT NULL,
    nimi    TEXT NOT NULL,
    lyhenne TEXT
);

INSERT INTO pot2_verkon_tarkoitus (koodi, nimi)
VALUES
    (1, 'Pituushalkeamien ehkäisy'),
    (2, 'Muiden routavaurioiden ehkäisy'),
    (3, 'Levennyksen tukeminen'),
    (4, 'Painumien ehkäisy'),
    (5, 'Moniongelmaisen tukeminen'),
    (9, 'Muu tarkoitus');

CREATE TABLE pot2_verkon_sijainti (
    koodi   INTEGER PRIMARY KEY NOT NULL,
    nimi    TEXT NOT NULL,
    lyhenne TEXT
);

INSERT INTO pot2_verkon_sijainti (koodi, nimi)
VALUES
    (1, 'Päällysteessä'),
    (2, 'Kantavan kerroksen yläpinnassa'),
    (3, 'Kantavassa kerroksessa'),
    (4, 'Kantavan kerroksen alapinnassa'),
    (9, 'Muu sijainti');

ALTER TABLE pot2_alusta ADD COLUMN verkon_tyyppi INTEGER REFERENCES pot2_verkon_tyyppi (koodi);
ALTER TABLE pot2_alusta ADD COLUMN verkon_tarkoitus INTEGER REFERENCES pot2_verkon_tarkoitus (koodi);
ALTER TABLE pot2_alusta ADD COLUMN verkon_sijainti INTEGER REFERENCES pot2_verkon_sijainti (koodi);
