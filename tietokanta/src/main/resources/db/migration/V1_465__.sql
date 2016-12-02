-- Ylläpito-urakoille muut työt

CREATE TABLE urakka_laskentakohde (
  id serial primary key NOT NULL,
  urakka integer references urakka (id) NOT NULL,
  nimi VARCHAR(256) NOT NULL,
  luotu TIMESTAMP,
  luoja INTEGER REFERENCES kayttaja (id),
  CONSTRAINT uniikki_laskentakohde UNIQUE (urakka, nimi)
);

CREATE TABLE yllapito_toteuma (
  id serial primary key NOT NULL,
  urakka integer references urakka (id) NOT NULL,
  sopimus integer references sopimus (id) NOT NULL,
  selite VARCHAR(512) NOT NULL,
  pvm DATE NULL,
  hinta NUMERIC NOT NULL,
  yllapitoluokka INTEGER,
  laskentakohde integer references urakka_laskentakohde (id),
  muokattu TIMESTAMP,
  muokkaaja integer references kayttaja (id),
  luotu TIMESTAMP,
  luoja integer references kayttaja (id)
);

CREATE INDEX yllapito_toteuma_urakka_idx ON yllapito_toteuma (urakka);