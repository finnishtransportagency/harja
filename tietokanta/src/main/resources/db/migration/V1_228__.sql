-- Lisää sääntö: toteuma_tehtavan tpk ei voi olla null
ALTER TABLE toteuma_tehtava ALTER COLUMN toimenpidekoodi SET NOT NULL;