-- vv_hinta -taululle viittaus toimenpidekoodiin
ALTER TABLE vv_hinta ADD COLUMN toimenpidekoodi INTEGER REFERENCES toimenpidekoodi(id);
ALTER TABLE vv_hinta ALTER COLUMN otsikko DROP NOT NULL; -- Otsikkoa ei tarvi jos on TPK
ALTER TABLE vv_hinta RENAME COLUMN maara TO summa; -- Määrä tarkoittaa jatkossa kpl-määrää
ALTER TABLE vv_hinta ALTER COLUMN summa DROP NOT NULL; -- Summaa ei tarvi jos on määrä ja TPK
ALTER TABLE vv_hinta ADD COLUMN maara NUMERIC;
ALTER TABLE vv_hinta ADD CONSTRAINT otsikko_tai_tpk CHECK (otsikko IS NOT NULL OR (otsikko IS NULL AND toimenpidekoodi IS NOT NULL));
ALTER TABLE vv_hinta ADD CONSTRAINT summa_tai_maara CHECK ((maara IS NULL AND summa IS NOT NULL) OR (summa IS NULL AND maara IS NOT NULL AND toimenpidekoodi IS NOT NULL));