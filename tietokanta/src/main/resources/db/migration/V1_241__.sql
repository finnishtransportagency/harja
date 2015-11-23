-- Raportille urakkatyyppi
ALTER TABLE raportti ADD COLUMN urakkatyyppi urakkatyyppi;
UPDATE raportti SET urakkatyyppi = 'hoito'::urakkatyyppi