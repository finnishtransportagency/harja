ALTER TABLE kan_hinta ADD COLUMN toimenpide INTEGER REFERENCES kan_toimenpide(id) ON DELETE CASCADE;

CREATE TABLE kan_tyo (
  id         SERIAL PRIMARY KEY,
  "toimenpidekoodi-id" integer NOT NULL,
  toimenpide INTEGER REFERENCES kan_toimenpide(id) ON DELETE CASCADE,
  maara      NUMERIC NOT NULL,
  muokkaaja  INTEGER
             REFERENCES kayttaja(id),
  muokattu   TIMESTAMP WITHOUT TIME ZONE,
  luoja      INTEGER
             REFERENCES kayttaja(id),
  luotu      TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
  poistettu  BOOLEAN DEFAULT FALSE NOT NULL,
  poistaja   INTEGER,
  CONSTRAINT maara_positiivinen CHECK ((maara >= (0)::NUMERIC))
);
