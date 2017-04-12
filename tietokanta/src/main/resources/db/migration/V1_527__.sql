-- Ylläpitokohdeosa uniikiksi vain ei-poistetuille
ALTER TABLE yllapitokohdeosa DROP CONSTRAINT yllapitokohdeosa_uniikki_yhaid;
CREATE UNIQUE INDEX yllapitokohdeosa_uniikki_yhaid ON yllapitokohdeosa (yhaid) WHERE poistettu = false;
