
-- Poistettaessa urakka tai toimenpide, poistetaan myös linkki
ALTER TABLE urakka_toimenpide DROP COLUMN urakka;
ALTER TABLE urakka_toimenpide ADD COLUMN urakka integer REFERENCES urakka (id) ON DELETE CASCADE;

ALTER TABLE urakka_toimenpide DROP COLUMN toimenpide;
ALTER TABLE urakka_toimenpide ADD COLUMN toimenpide integer REFERENCES toimenpidekoodi (id) ON DELETE CASCADE; 


