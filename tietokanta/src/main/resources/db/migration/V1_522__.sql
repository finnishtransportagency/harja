-- Uusi taulu ylläpitokohteen maksuerille
CREATE TABLE yllapitokohteen_maksuerat (
  id SERIAL PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde (id) NOT NULL UNIQUE,
  maksuerat VARCHAR(512)[],
  maksueratunnus VARCHAR (512)
)