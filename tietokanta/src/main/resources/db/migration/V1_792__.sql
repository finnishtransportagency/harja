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

ALTER TABLE johto_ja_hallintokorvaus
    DROP COLUMN "kk-v",
    DROP COLUMN maksukausi,
    DROP COLUMN hoitokausi,
    ALTER COLUMN vuosi SET NOT NULL,
    ALTER COLUMN kuukausi SET NOT NULL,
    ADD CONSTRAINT uniikki_johto_ja_hallintokorvaus UNIQUE ("urakka-id", "toimenkuva-id", vuosi, kuukausi, "ennen-urakkaa-id");
