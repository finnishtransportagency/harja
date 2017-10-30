CREATE TABLE vv_kanavatyo (
  id SERIAL PRIMARY KEY,
  urakka INTEGER REFERENCES urakka (id),
  pvm DATE NOT NULL,
  kohde VARCHAR(512) NOT NULL,
  huoltokohde VARCHAR(512) NOT NULL,
  toimenpide VARCHAR(512) NOT NULL,
  lisatieto VARCHAR(512),
  muu_toimenpide VARCHAR(512) NOT NULL,
  suorittaja VARCHAR(512) NOT NULL,
  kuittaaja VARCHAR(512) NOT NULL,

  muokkaaja  INTEGER REFERENCES kayttaja (id),
  muokattu   TIMESTAMP,
  luoja      INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu      TIMESTAMP                        NOT NULL DEFAULT NOW(),
  poistettu  BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja   INTEGER REFERENCES kayttaja (id)
);