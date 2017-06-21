CREATE TABLE vv_kiintio(
  id SERIAL PRIMARY KEY,
  "urakka-id" INTEGER REFERENCES urakka(id) NOT NULL,
  "sopimus-id" INTEGER REFERENCES sopimus(id) NOT NULL,
  nimi VARCHAR(80) NOT NULL,
  kuvaus VARCHAR (1024),
  koko INTEGER NOT NULL CONSTRAINT koko_yli_nolla CHECK (koko > 0),

  muokkaaja  INTEGER REFERENCES kayttaja (id),
  muokattu   TIMESTAMP,
  luoja      INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu      TIMESTAMP                        NOT NULL DEFAULT NOW(),
  poistettu  BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja   INTEGER REFERENCES kayttaja (id));

ALTER TABLE reimari_toimenpide
  ADD COLUMN "kiintio-id" INTEGER REFERENCES vv_kiintio(id)