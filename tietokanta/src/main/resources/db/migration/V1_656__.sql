CREATE TABLE kan_liikennetapahtuma_ketjutus(
  id SERIAL PRIMARY KEY,
  suunta liikennetapahtuma_suunta NOT NULL,
  aika TIMESTAMP NOT NULL,

  "kohde-id" INTEGER REFERENCES kan_kohde(id) NOT NULL,
  "sopimus-id" INTEGER REFERENCES sopimus(id) NOT NULL,
  "urakka-id" INTEGER REFERENCES urakka(id) NOT NULL,
  "alus-id" INTEGER REFERENCES kan_liikennetapahtuma_alus(id) NOT NULL,
  "kuitattu-id" INTEGER REFERENCES kan_liikennetapahtuma(id),

  luotu               TIMESTAMP DEFAULT NOW(),
  luoja               INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu            TIMESTAMP,
  muokkaaja           INTEGER REFERENCES kayttaja (id),
  poistettu           BOOLEAN   DEFAULT FALSE,
  poistaja            INTEGER REFERENCES kayttaja (id)
);