-- Luo taulu ylläpitokohteen yksityiskohtaiselle aikataululle
CREATE TYPE yllapitokohteen_aikataulu_toimenpide AS ENUM ('ojankaivuu', 'rp_tyot', 'rumpujen_vaihto', 'muu');

CREATE TABLE yllapitokohteen_tarkka_aikataulu (
  id SERIAL PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde(id) NOT NULL,
  urakka INTEGER REFERENCES urakka(id) NOT NULL, -- Urakka, jota aikataulu koskee (päällystys/tiemerkintä)
  toimenpide yllapitokohteen_aikataulu_toimenpide NOT NULL,
  kuvaus VARCHAR(1024),
  alku DATE NOT NULL,
  loppu DATE NOT NULL,
  luoja INTEGER REFERENCES kayttaja(id) NOT NULL,
  luotu TIMESTAMP NOT NULL,
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id),
  poistettu BOOLEAN NOT NULL DEFAULT FALSE
);