-- Ylläpitokohteen yhaid voi olla uniikki vain ei-poistetuille kohteille
ALTER TABLE yllapitokohde DROP CONSTRAINT yllapitokohde_uniikki_yhaid;
CREATE UNIQUE INDEX yllapitokohde_uniikki_yhaid ON yllapitokohde (yhaid) WHERE poistettu = false;