CREATE TABLE mhu_muutos_laskutusraja_historia (
  id                              SERIAL      PRIMARY KEY,
  urakka                          INTEGER     NOT NULL REFERENCES urakka (id),
  hoitokauden_alkuvuosi           INTEGER     NOT NULL,
  voimassa_alkaen                 DATE        NOT NULL,
  muutos_id                       INTEGER     REFERENCES mhu_muutos (id),
  muutoksen_maara                 NUMERIC,
  luoja                           INTEGER     REFERENCES kayttaja (id),
  luotu                           TIMESTAMP,
  muokkaaja                       INTEGER     REFERENCES kayttaja (id),
  muokattu                        TIMESTAMP,
  poistettu                       BOOLEAN     DEFAULT FALSE,
  poistaja                        INTEGER     REFERENCES kayttaja (id),
  poistettu_pvm                   TIMESTAMP
);

ALTER TABLE urakka_tavoite ADD COLUMN laskutusraja_alkuperainen NUMERIC;
