ALTER TABLE urakka_toimenpide ADD COLUMN nimi varchar (255);
ALTER TABLE urakka_toimenpide ADD COLUMN alkupvm date;
ALTER TABLE urakka_toimenpide ADD COLUMN loppupvm date;
ALTER TABLE urakka_toimenpide RENAME TO toimenpideinstanssi;