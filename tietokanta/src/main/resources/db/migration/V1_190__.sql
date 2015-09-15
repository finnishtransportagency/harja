-- Kuvaus pidennä Sampo idt 32 merkkiin
ALTER TABLE hanke ALTER COLUMN sampoid TYPE varchar(32);
ALTER TABLE urakka ALTER COLUMN sampoid TYPE varchar(32);
ALTER TABLE sopimus ALTER COLUMN sampoid TYPE varchar(32);
ALTER TABLE toimenpideinstanssi ALTER COLUMN sampoid TYPE varchar(32);
ALTER TABLE organisaatio ALTER COLUMN sampoid TYPE varchar(32);
ALTER TABLE yhteyshenkilo ALTER COLUMN sampoid TYPE varchar(32);
