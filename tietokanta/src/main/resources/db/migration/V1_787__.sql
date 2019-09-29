CREATE TYPE MAKSUKAUSI AS ENUM ('kesä', 'talvi', 'molemmat');

CREATE TABLE johto_ja_hallintokorvaus_toimenkuva (
    id SERIAL PRIMARY KEY,
    toimenkuva TEXT NOT NULL
);

CREATE TABLE johto_ja_hallintokorvaus (
  id SERIAL PRIMARY KEY,
  "urakka-id" INTEGER NOT NULL REFERENCES urakka(id),
  "toimenkuva-id" INTEGER NOT NULL REFERENCES johto_ja_hallintokorvaus_toimenkuva(id),
  tunnit INTEGER,
  tuntipalkka INTEGER,
  kk_v INTEGER,
  maksukausi MAKSUKAUSI,
  hoitokausi INTEGER,
  luotu TIMESTAMP,
  luoja INTEGER REFERENCES kayttaja(id),
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id),
  UNIQUE("urakka-id", "toimenkuva-id", maksukausi, hoitokausi)
);