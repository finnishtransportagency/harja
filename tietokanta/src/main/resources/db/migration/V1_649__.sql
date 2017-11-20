CREATE TABLE kan_toimenpide_hinta (
  toimenpide INTEGER
             NOT NULL
             REFERENCES kan_toimenpide(id),
  hinta      INTEGER
             NOT NULL
             REFERENCES kan_hinta(id),
  CONSTRAINT uniikki_kan_toimenpiteen_hinta UNIQUE (toimenpide,  hinta));

CREATE TABLE kan_tyo (
  id         SERIAL PRIMARY KEY,
  "toimenpidekoodi-id" integer NOT NULL,
  maara      NUMERIC NOT NULL,
  muokkaaja  INTEGER
             REFERENCES kayttaja(id),
  muokattu   TIMESTAMP WITHOUT TIME ZONE,
  luoja      INTEGER NOT NULL
             REFERENCES kayttaja(id),
  luotu      TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
  poistettu  BOOLEAN DEFAULT FALSE NOT NULL,
  poistaja   INTEGER,
  CONSTRAINT maara_positiivinen CHECK ((maara >= (0)::NUMERIC))
);
