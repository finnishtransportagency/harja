ALTER TABLE toteuma DROP COLUMN toimenpidekoodi;
ALTER TABLE toteuma ADD COLUMN toimenpidekoodi integer REFERENCES toimenpidekoodi (id) ON DELETE CASCADE;
