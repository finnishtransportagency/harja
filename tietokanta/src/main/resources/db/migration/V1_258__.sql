-- Kuvaus: uudelleennimeä toimenpidekoodin historiakuvasarake tilannekuvaksi
ALTER TABLE toimenpidekoodi DROP COLUMN historiakuva;
ALTER TABLE toimenpidekoodi ADD COLUMN suoritettavatehtava suoritettavatehtava;

