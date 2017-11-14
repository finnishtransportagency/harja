ALTER TABLE kan_toimenpide
  DROP COLUMN suorittaja,
  ADD COLUMN suorittaja TEXT NOT NULL;