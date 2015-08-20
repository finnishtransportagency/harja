CREATE TABLE suolasakko (
  id SERIAL PRIMARY KEY,
  maara NUMERIC NOT NULL,
  hoitokauden_alkuvuosi smallint not null,
  maksukuukausi smallint not null,
  indeksi varchar(128),

  urakka integer references urakka (id)   -- urakka johon suolasakkotieto liittyy
);