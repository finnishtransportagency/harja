ALTER TABLE hanke ADD COLUMN harjassa_luotu BOOLEAN DEFAULT FALSE;
UPDATE hanke SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;

ALTER TABLE organisaatio ADD COLUMN harjassa_luotu BOOLEAN DEFAULT FALSE;
UPDATE organisaatio SET harjassa_luotu = FALSE where harjassa_luotu IS NULL;