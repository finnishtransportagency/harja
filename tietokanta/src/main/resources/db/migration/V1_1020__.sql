ALTER TABLE ilmoitus
    ADD COLUMN aihe INTEGER,
    ADD COLUMN tarkenne INTEGER;

CREATE TABLE palautejarjestelma_aihe
(
    ulkoinen_id INTEGER PRIMARY KEY,
    nimi        VARCHAR NOT NULL,
    jarjestys   INTEGER,
    kaytossa BOOL
);

CREATE TABLE palautejarjestelma_tarkenne
(
   ulkoinen_id INTEGER PRIMARY KEY,
   nimi VARCHAR NOT NULL,
   aihe_id INTEGER REFERENCES palautejarjestelma_aihe(ulkoinen_id),
   jarjestys INTEGER,
   kaytossa BOOL
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('palautejarjestelma', 'hae-aiheet');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('palautejarjestelma', 'hae-tarkenteet');
