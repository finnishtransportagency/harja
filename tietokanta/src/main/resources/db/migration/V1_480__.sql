-- Raportin urakkatyyppi enumiksi
ALTER TABLE raportti ADD COLUMN urakkatyyppi_ urakkatyyppi[];
UPDATE raportti SET urakkatyyppi_ = ARRAY['hoito']::urakkatyyppi[];
ALTER TABLE raportti DROP COLUMN urakkatyyppi;
ALTER TABLE raportti RENAME COLUMN urakkatyyppi_ TO urakkatyyppi;

-- Laatu- ja turvallisuuspoikkeamaraportit ylläpidolle

UPDATE raportti SET urakkatyyppi = ARRAY['hoito', 'paallystys', 'paikkaus', 'tiemerkinta']::urakkatyyppi[]
WHERE nimi = 'laatupoikkeamaraportti' OR nimi = 'turvallisuus';