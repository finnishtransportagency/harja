CREATE TABLE urakka_tehtavamaara (
   id serial primary key,    -- sisäinen id
   urakka integer not null references urakka(id),
   "hoitokauden-alkuvuosi" smallint not null,
   tehtava integer not null references toimenpidekoodi(id), -- taso 4
   maara numeric,
   poistettu boolean DEFAULT false,
   luotu timestamp,
   luoja integer REFERENCES kayttaja (id),
   muokattu timestamp,
   muokkaaja integer REFERENCES kayttaja (id),
   unique (urakka, "hoitokauden-alkuvuosi", tehtava)
);

COMMENT ON table urakka_tehtavamaara IS
  E'Tehtävä- ja määräluettelon tietojen tallentamiseen. Taulu linkittää urakan ja tehtävän ja säilyttää tehtävämäärän.
  Urakassa tehtävät tehtävämäärät määritellään hoitokausittain.';

