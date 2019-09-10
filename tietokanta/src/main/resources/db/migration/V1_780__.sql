ALTER TABLE yksikkohintainen_tyo
ADD COLUMN toimenpideinstanssi INTEGER;

ALTER TABLE kokonaishintainen_tyo
ADD COLUMN luotu TIMESTAMP,
ADD COLUMN muokkaaja INTEGER references kayttaja(id),
ADD COLUMN muokattu TIMESTAMP;


ALTER TABLE kiinteahintainen_tyo
DROP CONSTRAINT kiinteahintainen_tyo_toimenpideinstanssi_sopimus_vuosi_kuuk_key,
ADD UNIQUE (toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, vuosi, kuukausi);
