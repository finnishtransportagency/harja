-- Uusi taulu vv_tyo, johon tallennetaan toimenpiteen hinnoittelussa tehty työ
CREATE TABLE vv_tyo
(
  id                   SERIAL PRIMARY KEY,
  "hinnoittelu-id"     INTEGER REFERENCES vv_hinnoittelu (id)  NOT NULL,
  "toimenpidekoodi-id" INTEGER REFERENCES toimenpidekoodi (id) NOT NULL,
  maara                NUMERIC                                 NOT NULL,
  muokkaaja            INTEGER REFERENCES kayttaja (id),
  muokattu             TIMESTAMP,
  luoja                INTEGER REFERENCES kayttaja (id)        NOT NULL,
  luotu                TIMESTAMP                               NOT NULL DEFAULT NOW(),
  poistettu            BOOLEAN                                 NOT NULL DEFAULT FALSE,
  poistaja             INTEGER REFERENCES kayttaja (id)
);