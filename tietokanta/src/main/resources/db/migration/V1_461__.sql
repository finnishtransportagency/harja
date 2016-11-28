
-- Ylläpito-urakoille muut työt

CREATE TABLE urakka_laskentakohde (
  id serial primary key NOT NULL,
  nimi VARCHAR(256) NOT NULL,
  urakka integer references urakka (id) NOT NULL
);

ALTER TABLE urakka_laskentakohde ADD CONSTRAINT uniikki_laskentakohde UNIQUE (nimi, urakka);

CREATE TABLE yllapito_toteuma (
  id serial primary key NOT NULL,
  urakka integer references urakka (id) NOT NULL,
  selite VARCHAR(512) NOT NULL,
  hinta NUMERIC NOT NULL,
  yllapitoluokka INTEGER,
  laskentakohde integer references urakka_laskentakohde (id) NOT NULL
);