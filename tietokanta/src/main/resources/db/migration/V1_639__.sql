ALTER TABLE reimari_toimenpide ADD COLUMN vaylanro text;
UPDATE reimari_toimenpide SET vaylanro = ("reimari-vayla").nro;
ALTER TABLE reimari_toimenpide DROP COLUMN "vayla-id";
ALTER TABLE vv_vayla DROP COLUMN id;
