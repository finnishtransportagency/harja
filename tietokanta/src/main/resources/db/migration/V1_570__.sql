-- Toimenpiteen lisätieto voi olla NULL
ALTER TABLE reimari_toimenpide ALTER lisatieto DROP NOT NULL;
UPDATE reimari_toimenpide SET lisatieto = NULL
WHERE lisatieto = '';