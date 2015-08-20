
-- Toimenpidekoodi pidennetty 16 merkkiin, jotta vastaa Samposta tulevia id:tä
ALTER TABLE toimenpidekoodi ALTER COLUMN koodi TYPE varchar(16);

