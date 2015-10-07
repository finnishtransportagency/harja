-- Kuvaus: Luodaan taulu ja rivit geometriapäivityksille Paikkatietojärjestelmästä
CREATE TABLE geometriapaivitys (
  id                 SERIAL PRIMARY KEY,
  nimi               VARCHAR(20) NOT NULL,
  url                TEXT,
  viimeisin_paivitys TIMESTAMP
);

INSERT INTO geometriapaivitys (nimi) VALUES ('tieverkko');
INSERT INTO geometriapaivitys (nimi) VALUES ('talvihoitoluokat');
INSERT INTO geometriapaivitys (nimi) VALUES ('soratieluokat');
INSERT INTO geometriapaivitys (nimi) VALUES ('pohjavesialueet');
INSERT INTO geometriapaivitys (nimi) VALUES ('sillat');
INSERT INTO geometriapaivitys (nimi) VALUES ('ely-alueet');
INSERT INTO geometriapaivitys (nimi) VALUES ('hoidon-alueurakat');