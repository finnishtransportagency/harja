CREATE TABLE johto_ja_hallintokorvaus_ennen_urakkaa
(
    id     SERIAL PRIMARY KEY,
    "kk-v" NUMERIC NOT NULL
);

-- Halutaan tiputtaa 'hoitokausi' sarake pois ja luoda sen tilalle 'vuosi' ja 'kuukausi' sarakkeet
ALTER TABLE johto_ja_hallintokorvaus
  DROP CONSTRAINT "johto_ja_hallintokorvaus_urakka-id_toimenkuva-id_maksukausi_key",
  ADD COLUMN vuosi INTEGER,
  ADD COLUMN kuukausi INTEGER,
  ADD COLUMN "ennen-urakkaa-id" INTEGER REFERENCES johto_ja_hallintokorvaus_ennen_urakkaa (id);

