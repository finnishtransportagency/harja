
ALTER TABLE paivystys DROP COLUMN yhteyshenkilo;
ALTER TABLE paivystys ADD COLUMN yhteyshenkilo integer REFERENCES yhteyshenkilo (id) ON DELETE CASCADE;

 
