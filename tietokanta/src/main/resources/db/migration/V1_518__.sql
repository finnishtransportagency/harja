-- Tiukenna ylläpitokohteen ja liittyvien ilmoitusten sääntöjä
ALTER TABLE yllapitokohde ALTER COLUMN urakka SET NOT NULL;
ALTER TABLE yllapitokohde ALTER COLUMN tr_numero SET NOT NULL;
ALTER TABLE yllapitokohde ALTER COLUMN tr_alkuosa SET NOT NULL;
ALTER TABLE yllapitokohde ALTER COLUMN tr_alkuetaisyys SET NOT NULL;

ALTER TABLE yllapitokohdeosa ALTER COLUMN yllapitokohde SET NOT NULL;
ALTER TABLE yllapitokohdeosa ALTER COLUMN tr_numero SET NOT NULL;
ALTER TABLE yllapitokohdeosa ALTER COLUMN tr_alkuosa SET NOT NULL;
ALTER TABLE yllapitokohdeosa ALTER COLUMN tr_alkuetaisyys SET NOT NULL;

ALTER TABLE paallystysilmoitus ALTER COLUMN paallystyskohde SET NOT NULL;
ALTER TABLE paikkausilmoitus ALTER COLUMN paikkauskohde SET NOT NULL;