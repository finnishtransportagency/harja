ALTER TABLE yllapitokohteen_maaramuutos ALTER COLUMN toteutunut_maara DROP NOT NULL;

ALTER TABLE yllapitokohteen_maaramuutos ADD CONSTRAINT ennuste_tai_maara CHECK
(toteutunut_maara IS NOT NULL OR ennustettu_maara IS NOT NULL);