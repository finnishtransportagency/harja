ALTER TABLE kanavahuoltokohde
  RENAME TO kan_huoltokohde;

CREATE TYPE KAN_TOIMENPIDETYYPPI AS ENUM ('yksikkohintainen', 'kokonaishintainen', 'muutos-lisatyo');

CREATE TABLE kan_toimenpide (
  id              SERIAL PRIMARY KEY,
  tyyppi          KAN_TOIMENPIDETYYPPI                       NOT NULL,
  urakka          INTEGER REFERENCES urakka                  NOT NULL,
  sopimus         INTEGER REFERENCES sopimus                 NOT NULL,
  pvm             DATE                                       NOT NULL,
  kohde           INTEGER REFERENCES kan_kohde (id)          NOT NULL,
  huoltokohde     INTEGER REFERENCES kan_huoltokohde (id)    NOT NULL,
  toimenpidekoodi INTEGER REFERENCES toimenpidekoodi (id),
  lisatieto       TEXT,
  suorittaja      INTEGER REFERENCES kayttaja (id)           NOT NULL,
  kuittaaja       INTEGER REFERENCES kayttaja (id)           NOT NULL,
  luotu           TIMESTAMP DEFAULT NOW(),
  luoja           INTEGER REFERENCES kayttaja (id)           NOT NULL,
  muokattu        TIMESTAMP,
  muokkaaja       INTEGER REFERENCES kayttaja (id),
  poistettu       BOOLEAN   DEFAULT FALSE,
  poistaja        INTEGER REFERENCES kayttaja (id)
);
