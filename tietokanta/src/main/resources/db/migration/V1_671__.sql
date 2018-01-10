-- Luo taulu ylläpitokohteen yksityiskohtaiselle aikataululle
CREATE TYPE yllapitokohteen_aikataulu_toimenpide AS ENUM ('ojan_kaivuu', 'rp_tyot', 'rumpujen_vaihto', 'muu');

CREATE TABLE yllapitokohteen_yksityiskohtainen_aikataulu (
  id SERIAL PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde(id) NOT NULL,
  toimenpide yllapitokohteen_aikataulu_toimenpide NOT NULL,
  kuvaus VARCHAR(1024),
  alku DATE NOT NULL,
  loppu DATE NOT NULL,
  luoja INTEGER REFERENCES kayttaja(id) NOT NULL,
  luotu TIMESTAMP NOT NULL,
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id)
);