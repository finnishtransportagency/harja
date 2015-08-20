
-- päivystyksiä ei liitetäkään yhteyshenkilo_urakka taulun kautta
ALTER TABLE paivystys DROP COLUMN yhteyshenkilo_urakka;
-- vaan suoraan urakkaan ja yhteyshenkilöön
ALTER TABLE paivystys ADD COLUMN yhteyshenkilo integer REFERENCES yhteyshenkilo (id);
ALTER TABLE paivystys ADD COLUMN urakka integer REFERENCES urakka (id);
