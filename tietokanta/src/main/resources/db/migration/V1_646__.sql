ALTER TABLE reimari_toimenpide DISABLE TRIGGER USER; -- nopeushack vaylanro:n päivityksen ajaksi
ALTER TABLE reimari_toimenpide ADD COLUMN vaylanro integer;
UPDATE reimari_toimenpide SET vaylanro = ("reimari-vayla").nro::integer;
ALTER TABLE reimari_toimenpide ENABLE TRIGGER USER;
ALTER TABLE reimari_toimenpide DROP COLUMN "vayla-id";
DELETE FROM vv_vayla a USING vv_vayla b WHERE a.id < b.id AND a.vaylanro = b.vaylanro;
ALTER TABLE vv_vayla DROP COLUMN id;
CREATE UNIQUE INDEX uniikki_vaylan_vaylanro on vv_vayla (vaylanro);