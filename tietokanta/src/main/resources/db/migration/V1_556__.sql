-- Lisää liitteelle UUID (fileyard)

ALTER TABLE liite
  ADD COLUMN "fileyard-uuid" UUID;
