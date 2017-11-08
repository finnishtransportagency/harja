-- Tiukenna kan_toimenpide taulua
UPDATE kan_toimenpide SET poistettu = FALSE WHERE poistettu IS NULL;
ALTER TABLE kan_toimenpide ALTER COLUMN poistettu SET NOT NULL;