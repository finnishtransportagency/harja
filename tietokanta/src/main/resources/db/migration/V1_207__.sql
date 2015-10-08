-- Kuvaus: Luodaan taulu ja rivit geometriapäivityksille Paikkatietojärjestelmästä
CREATE TABLE geometriapaivitys (
  id                 SERIAL PRIMARY KEY,
  nimi               VARCHAR(20) NOT NULL,
  viimeisin_paivitys TIMESTAMP,
  CONSTRAINT uniikki_geometriapaivitys UNIQUE (nimi)
);

INSERT INTO geometriapaivitys (nimi) VALUES ('tieverkko');
INSERT INTO geometriapaivitys (nimi) VALUES ('talvihoitoluokat');
INSERT INTO geometriapaivitys (nimi) VALUES ('soratieluokat');
INSERT INTO geometriapaivitys (nimi) VALUES ('pohjavesialueet');
INSERT INTO geometriapaivitys (nimi) VALUES ('sillat');
INSERT INTO geometriapaivitys (nimi) VALUES ('ely-alueet');
INSERT INTO geometriapaivitys (nimi) VALUES ('hoidon-alueurakat');