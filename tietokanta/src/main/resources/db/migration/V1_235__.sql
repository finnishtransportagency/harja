-- Lisää päällystyskohdeosalle sääntö: kohde ei voi olla null

ALTER TABLE paallystyskohdeosa ALTER COLUMN paallystyskohde SET NOT NULL;