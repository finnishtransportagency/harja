-- Välitavoitteelle linkki ylläpitokohteeseen
ALTER TABLE valitavoite ADD COLUMN "yllapitokohde" INTEGER REFERENCES yllapitokohde(id);