ALTER TABLE urakka ADD COLUMN harjassa_luotu BOOLEAN DEFAULT FALSE;

UPDATE urakka SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;

