-- Paranna kan_toimenpide taulua
ALTER TABLE kan_toimenpide ADD COLUMN muu_toimenpide TEXT;

UPDATE kan_toimenpide SET poistettu = FALSE WHERE poistettu IS NULL;
ALTER TABLE kan_toimenpide ALTER COLUMN poistettu SET NOT NULL;