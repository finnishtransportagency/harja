-- Maksuerän tilat
CREATE TYPE maksueratila AS ENUM ('odottaa_vastausta', 'lahetetty', 'virhe');

ALTER TABLE maksuera ADD COLUMN tila maksueratila;