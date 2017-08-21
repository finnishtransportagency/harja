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


ALTER TABLE vv_hinta ADD CONSTRAINT maara_positiivinen CHECK(maara >= 0);
ALTER TABLE vv_tyo ADD CONSTRAINT maara_positiivinen CHECK(maara >= 0);

-- Työt tallennettiin ennen könttäsummana vv_hinta tauluun nimellä 'Työ'.
-- Migratoidaan nämä könttäsummat päivän hinnoiksi.
UPDATE vv_hinta set otsikko = 'Päivän hinta' WHERE otsikko = 'Työ';