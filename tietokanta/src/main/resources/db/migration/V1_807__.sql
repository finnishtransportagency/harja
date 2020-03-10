ALTER TABLE johto_ja_hallintokorvaus_toimenkuva ADD COLUMN "urakka-id" INTEGER REFERENCES urakka (id);

ALTER TABLE johto_ja_hallintokorvaus_toimenkuva ALTER COLUMN toimenkuva DROP NOT NULL;