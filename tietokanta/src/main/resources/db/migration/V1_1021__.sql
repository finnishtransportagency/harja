ALTER TABLE ilmoitus
    ADD COLUMN aihe     INTEGER,
    ADD COLUMN tarkenne INTEGER;

CREATE TABLE palautevayla_aihe
(
    ulkoinen_id INTEGER PRIMARY KEY,
    nimi        VARCHAR NOT NULL,
    jarjestys   INTEGER,
    kaytossa    BOOL,
    luotu       TIMESTAMP NOT NULL DEFAULT NOW(),
    muokattu    TIMESTAMP
);

CREATE TABLE palautevayla_tarkenne
(
    ulkoinen_id INTEGER PRIMARY KEY,
    nimi        VARCHAR NOT NULL,
    aihe_id     INTEGER REFERENCES palautevayla_aihe (ulkoinen_id),
    jarjestys   INTEGER,
    kaytossa    BOOL,
    luotu       TIMESTAMP NOT NULL DEFAULT NOW(),
    muokattu    TIMESTAMP
);

INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('palautevayla', 'hae-aiheet');
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('palautevayla', 'hae-tarkenteet');
