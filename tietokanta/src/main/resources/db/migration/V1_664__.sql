ALTER TABLE vv_materiaali ADD COLUMN toimenpide INTEGER REFERENCES kan_toimenpide (id);
ALTER TYPE vv_materiaali_muutos ADD ATTRIBUTE toimenpide INTEGER;
