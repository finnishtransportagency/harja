-- Ylläpitokohdeosalle tarkemmin eritellyt toimenpiteet
ALTER TABLE yllapitokohdeosa ADD COLUMN paallystetyyppi INT; -- ks. koodisto +paallystetyypit+
ALTER TABLE yllapitokohdeosa ADD COLUMN raekoko INT;
ALTER TABLE yllapitokohdeosa ADD COLUMN tyomenetelma INT; -- ks. koodisto +tyomenetelmat+
ALTER TABLE yllapitokohdeosa ADD COLUMN massamaara NUMERIC(10, 2);