-- Lisätään tieturvallisuustarkastusgeometria-aineiston päivityksen tiedot
-- Aineisto määritellään nyt lokaalisti päivitettäväksi, paikallinen = true
INSERT INTO geometriapaivitys (nimi, paikallinen) VALUES ('tieturvallisuusverkko', true);

-- Luodaan tieturvallisuusverkolle oma taulu
CREATE TABLE tieturvallisuusverkko
(
    id         SERIAL PRIMARY KEY,
    tasoluokka VARCHAR(250),
    aosa       INTEGER  NOT NULL,
    tie        INTEGER  NOT NULL,
    let        INTEGER  NOT NULL,
    losa       INTEGER  NOT NULL,
    aet        INTEGER  NOT NULL,
    tenluokka  VARCHAR(250),
    geometria  GEOMETRY NOT NULL,
    ely        VARCHAR(250),
    pituus     NUMERIC,
    luonne     VARCHAR(250),
    luotu      TIMESTAMP default now()
);
