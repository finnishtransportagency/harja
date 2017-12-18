ALTER TABLE kan_toimenpide
  ALTER COLUMN huoltokohde DROP NOT NULL,
  ADD CONSTRAINT huoltokohde_jos_kohde_id
CHECK (("kohde-id" IS NULL AND huoltokohde IS NULL) OR
       ("kohde-id" IS NOT NULL AND huoltokohde IS NOT NULL));