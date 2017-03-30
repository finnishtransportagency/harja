-- Uusi taulu ylläpitokohteen maksuerille
CREATE TABLE (
  id SERIAL PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde (id)
)