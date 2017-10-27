CREATE TABLE kan_toimenpide (
  id          SERIAL PRIMARY KEY,
  pvm         DATE,
  kohde       INTEGER REFERENCES kan_kohde (id)          NOT NULL,
  huoltokohde INTEGER REFERENCES kanavahuoltokohde (id) NOT NULL,
  toimenpide  INTEGER REFERENCES toimenpidekoodi (id),
  lisatieto   TEXT,
  suorittaja  INTEGER REFERENCES kayttaja (id)           NOT NULL,
  kuittaaja   INTEGER REFERENCES kayttaja (id)           NOT NULL
);