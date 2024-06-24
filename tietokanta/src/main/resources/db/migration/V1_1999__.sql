CREATE TYPE mpu_kustannustyyppi_enum AS ENUM (
  'Arvonmuutokset',
  'Indeksi- ja kustannustason muutokset',
  'Muut kustannukset'
);

ALTER TABLE mpu_kustannukset ADD COLUMN kustannustyyppi mpu_kustannustyyppi_enum;

-- Lisää muokkaajan tiedot, lisätty myös poistettu vaikka tätä toiminnallisuutta ei tällä hetkellä ole
ALTER TABLE mpu_kustannukset
ADD COLUMN luotu TIMESTAMP,
ADD COLUMN luoja INTEGER,
ADD COLUMN poistettu BOOLEAN DEFAULT FALSE,
ADD COLUMN muokattu TIMESTAMP DEFAULT NULL,
ADD COLUMN muokkaaja INTEGER REFERENCES kayttaja (id) DEFAULT NULL;
