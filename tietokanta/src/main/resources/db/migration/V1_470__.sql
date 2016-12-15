-- Urakkastats taulu

CREATE TABLE urakkastats (
  urakka INTEGER REFERENCES urakka (id),
  tyokonehavainto TIMESTAMP, -- Viimeisin työkoneen lähetysaika
  toteuma TIMESTAMP, -- Viimeisin toteuman luonti-/muokkausaika
  ilmoitus TIMESTAMP, -- Viimeisin urakkaan saapunut ilmoitus
  kuittaus TIMESTAMP, -- Viimeisin urakan ilmoitukseen saapunut kuittaus
  tarkastus TIMESTAMP -- Viimeisin urakan tarkastuksen luonti/-muokkausaika
);
