-- Lisää ylläpitokohteelle tieto siitä, minä vuosina ko. kohde on työn alla

ALTER TABLE yllapitokohde ADD COLUMN vuodet int[] NOT NULL DEFAULT ARRAY[]::integer[];

-- Pakota ylläpitokohteille tyyppi

UPDATE yllapitokohde
SET yllapitokohdetyotyyppi = 'paikkaus'
WHERE yllapitokohdetyotyyppi IS NULL AND yhaid IS NULL;

UPDATE yllapitokohde
SET yllapitokohdetyotyyppi = 'paallystys'
WHERE yllapitokohdetyotyyppi IS NULL AND yhaid IS NOT NULL;

ALTER TABLE yllapitokohde ALTER COLUMN yllapitokohdetyotyyppi SET NOT NULL;

-- Nykyisille YHA-kohteille asetetaan vuodeksi 2016
UPDATE yllapitokohde SET vuodet = '{2016}' WHERE yhaid IS NOT NULL;