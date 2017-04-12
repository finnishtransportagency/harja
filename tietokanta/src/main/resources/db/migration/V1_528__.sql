-- Päällystyksen aloitus- ja lopetuskentistä kellonaika pois (Lähde: ASPA 12.4.2017)
ALTER TABLE yllapitokohteen_aikataulu ALTER COLUMN paallystys_alku TYPE DATE;
ALTER TABLE yllapitokohteen_aikataulu ALTER COLUMN paallystys_loppu TYPE DATE;