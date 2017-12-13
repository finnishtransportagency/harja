CREATE TABLE kan_liikennetapahtuma_ketjutus(
  "kohteelle-id" INTEGER REFERENCES kan_kohde(id) NOT NULL,
  "kohteelta-id" INTEGER REFERENCES kan_kohde(id) NOT NULL,
  "sopimus-id" INTEGER REFERENCES sopimus(id) NOT NULL,
  "urakka-id" INTEGER REFERENCES urakka(id) NOT NULL,
  "alus-id" INTEGER REFERENCES kan_liikennetapahtuma_alus(id) NOT NULL,
  "tapahtumasta-id" INTEGER REFERENCES kan_liikennetapahtuma(id) NOT NULL,
  "tapahtumaan-id" INTEGER REFERENCES kan_liikennetapahtuma(id));

CREATE UNIQUE INDEX ketjutus_tunniste ON kan_liikennetapahtuma_ketjutus ("alus-id");