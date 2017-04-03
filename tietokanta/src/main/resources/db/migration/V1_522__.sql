-- Uusi taulu ylläpitokohteen maksuerille
CREATE TABLE yllapitokohteen_maksuera (
  id SERIAL PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde (id) NOT NULL,
  maksueranumero INT NOT NULL,
  maksueratunnus VARCHAR (512)
);

ALTER TABLE yllapitokohteen_maksuera ADD CONSTRAINT uniikki_maksueranumero UNIQUE (yllapitokohde, maksueranumero);
ALTER TABLE yllapitokohteen_maksuera ADD CONSTRAINT validi_maksueranumero
CHECK (maksueranumero >= 1 AND maksueranumero <= 5);
