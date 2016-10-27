-- Lisää ylläpitokohteelle tiemerkintä-spesifiset tiedot

CREATE TYPE yllapitokohde_tiemerkinta_hintatyyppi AS ENUM ('suunnitelma', 'toteuma');

-- TODO Tähän tauluun olisi hyvä siirtää myös aikataulutiedot tai vaihtoehtoisesti tehdä aikataululle oma taulu?
CREATE TABLE yllapitokohde_tiemerkinta (
  id serial PRIMARY KEY,
  yllapitokohde integer REFERENCES yllapitokohde (id) NOT NULL UNIQUE,
  hinta integer,
  hintatyyppi yllapitokohde_tiemerkinta_hintatyyppi,
  muutospvm DATE
);

COMMENT ON TABLE yllapitokohde_tiemerkinta IS 'Tiemerkintäurakkaa koskevaa tietoa ylläpitokohteesta';
