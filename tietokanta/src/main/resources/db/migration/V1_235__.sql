-- Lisää päällystyskohdeosalle sääntö: kohde ei voi olla null

DELETE FROM paallystyskohdeosa WHERE paallystyskohde IS NULL;
ALTER TABLE paallystyskohdeosa ALTER COLUMN paallystyskohde SET NOT NULL;