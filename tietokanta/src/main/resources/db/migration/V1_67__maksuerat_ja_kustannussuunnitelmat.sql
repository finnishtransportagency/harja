-- Maksueränumerot
CREATE SEQUENCE maksueranumero;

-- Maksuerätyypit
CREATE TYPE maksueratyyppi AS ENUM ('kokonaishintainen', 'yksikkohintainen', 'lisatyo', 'indeksi', 'bonus', 'sakko', 'akillinen_hoitotyo', 'muu');


-- Maksuerät
CREATE TABLE maksuera (
  numero              INTEGER PRIMARY KEY DEFAULT nextval('maksueranumero'),
  toimenpideinstanssi INTEGER REFERENCES toimenpideinstanssi (id),
  tyyppi              maksueratyyppi,
  luotu               TIMESTAMP,
  muokattu            TIMESTAMP,
  lahetetty           TIMESTAMP,
  lahetysid           VARCHAR(255), -- JMS Message ID
  lukko               CHAR(36), -- Lukon UUID
  lukittu             TIMESTAMP -- Milloin lukko on asetettu
);

-- Kustannussuunnitelmat
CREATE TABLE kustannussuunnitelma (
  maksuera  INTEGER REFERENCES maksuera (numero),
  luotu     TIMESTAMP,
  muokattu  TIMESTAMP,
  lahetetty TIMESTAMP,
  lahetysid VARCHAR(255), -- JMS Message ID
  lukko     CHAR(36), -- Lukon UUID
  lukittu   TIMESTAMP -- Milloin lukko on asetettu
);