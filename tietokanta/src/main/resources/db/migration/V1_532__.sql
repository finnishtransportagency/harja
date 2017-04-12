ALTER TABLE sopimus ADD COLUMN harjassa_luotu BOOLEAN DEFAULT FALSE;
UPDATE sopimus SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;
