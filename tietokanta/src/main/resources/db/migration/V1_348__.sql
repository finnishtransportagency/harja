-- Lisää urakan YHA-tiedoille uusi taulu

CREATE TABLE yhatiedot (
  id                       SERIAL PRIMARY KEY,
  urakka                   INTEGER REFERENCES urakka (id),
  yhatunnus                VARCHAR(255),
  yhaid                    INTEGER,
  yhanimi                  VARCHAR(2048),
  elyt                     VARCHAR(2048) [],
  vuodet                   INTEGER [],
  kohdeluettelo_paivitetty TIMESTAMP,
  luotu                    TIMESTAMP,
  muokattu                 TIMESTAMP
);

ALTER TABLE yhatiedot ADD CONSTRAINT uniikki_yhatunnus UNIQUE (yhatunnus);
ALTER TABLE yhatiedot  ADD CONSTRAINT uniikki_yhaid UNIQUE (yhaid);
