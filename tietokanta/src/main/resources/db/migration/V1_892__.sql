-- Lisätään paikkauskohde -tauluun viite ylläpitokohde -tauluun, pot tyyppisille raportoinneille
ALTER TABLE paikkauskohde
    ADD COLUMN "yllapitokohde-id" INTEGER REFERENCES yllapitokohde (id) DEFAULT NULL;