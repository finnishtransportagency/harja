CREATE TYPE MAKSUKAUSI AS ENUM ('kesa', 'talvi', 'molemmat');

CREATE TABLE johto_ja_hallintokorvaus_toimenkuva (
    id SERIAL PRIMARY KEY,
    toimenkuva TEXT NOT NULL
);

CREATE TABLE johto_ja_hallintokorvaus (
  id SERIAL PRIMARY KEY,
  "urakka-id" INTEGER NOT NULL REFERENCES urakka(id),
  "toimenkuva-id" INTEGER NOT NULL REFERENCES johto_ja_hallintokorvaus_toimenkuva(id),
  tunnit NUMERIC,
  tuntipalkka NUMERIC,
  "kk-v" NUMERIC,
  maksukausi MAKSUKAUSI,
  hoitokausi INTEGER,
  luotu TIMESTAMP,
  luoja INTEGER REFERENCES kayttaja(id),
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id),
  UNIQUE("urakka-id", "toimenkuva-id", maksukausi, hoitokausi)
);

INSERT INTO johto_ja_hallintokorvaus_toimenkuva (toimenkuva)
VALUES ('sopimusvastaava'),
       ('vastuunalainen työnjohtaja'),
       ('päätoiminen apulainen'),
       ('apulainen/työnjohtaja'),
       ('viherhoidosta vastaava henkilö'),
       ('hankintavastaava'),
       ('harjoittelija');