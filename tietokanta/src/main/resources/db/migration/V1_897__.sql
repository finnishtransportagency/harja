ALTER TABLE raportti_suoritustieto ALTER COLUMN rooli DROP NOT NULL;
ALTER TABLE raportti_suoritustieto ADD COLUMN urakkarooli TEXT;
ALTER TABLE raportti_suoritustieto ADD COLUMN organisaatiorooli TEXT;