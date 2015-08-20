
ALTER TABLE yhteyshenkilo_urakka DROP COLUMN yhteyshenkilo;
ALTER TABLE yhteyshenkilo_urakka ADD COLUMN yhteyshenkilo integer REFERENCES yhteyshenkilo (id) ON DELETE CASCADE;

 
