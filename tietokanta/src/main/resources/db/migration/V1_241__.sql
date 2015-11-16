-- Luo yleinen lahetyksen tila arvojoukko ja ota se käyttöön maksuerille ja kustannussuunnitelmille
CREATE TYPE lahetyksen_tila AS ENUM ('odottaa_vastausta', 'lahetetty', 'virhe');

ALTER TABLE maksuera RENAME COLUMN tila TO _tila;
ALTER TABLE kustannussuunnitelma RENAME COLUMN tila TO _tila;

ALTER TABLE maksuera ADD tila lahetyksen_tila;
ALTER TABLE kustannussuunnitelma ADD tila lahetyksen_tila;

UPDATE maksuera
SET tila = _tila :: TEXT :: lahetyksen_tila;
UPDATE kustannussuunnitelma
SET tila = _tila :: TEXT :: lahetyksen_tila;

ALTER TABLE maksuera DROP COLUMN _tila;
ALTER TABLE kustannussuunnitelma DROP COLUMN _tila;
DROP TYPE maksueratila;

-- Nimeä kuittaustaulu ilmoitustoimenpiteeksi
ALTER TABLE kuittaus RENAME TO ilmoitustoimenpide;

-- Lisää ilmoitustoimenpiteelle tarvittavat kentät lähetyksien hallitsemiseksi
ALTER TABLE ilmoitustoimenpide
ADD COLUMN tila lahetyksen_tila,
ADD COLUMN lahetetty TIMESTAMP,
ADD COLUMN lahetysid VARCHAR(255);