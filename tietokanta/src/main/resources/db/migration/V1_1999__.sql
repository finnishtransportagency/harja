CREATE TYPE mpu_kustannustyyppi_enum AS ENUM (
  'Arvonmuutokset',
  'Indeksi- ja kustannustason muutokset',
  'Muut kustannukset'
);

ALTER TABLE mpu_kustannukset ADD COLUMN kustannustyyppi mpu_kustannustyyppi_enum;
