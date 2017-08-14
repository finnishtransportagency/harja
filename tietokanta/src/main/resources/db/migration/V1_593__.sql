-- vv_hinta -taululle viittaus toimenpidekoodiin
-- Omaa otsikkoa ei ole enää pakko olla jos on toimenpidekoodi
ALTER TABLE vv_hinta ADD COLUMN toimenpidekoodi INTEGER REFERENCES toimenpidekoodi(id);
ALTER TABLE vv_hinta ALTER COLUMN otsikko DROP NOT NULL;
ALTER TABLE vv_hinta ADD CONSTRAINT otsikko_tai_tpk CHECK (otsikko IS NOT NULL OR (otsikko IS NULL AND toimenpidekoodi IS NOT NULL));