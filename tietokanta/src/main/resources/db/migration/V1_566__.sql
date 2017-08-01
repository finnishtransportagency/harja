-- Lisää liitteelle hash (fileyard)

ALTER TABLE liite
  ADD COLUMN "fileyard-hash" char(64);
