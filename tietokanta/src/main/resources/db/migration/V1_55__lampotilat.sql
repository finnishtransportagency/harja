CREATE TABLE lampotilat (
  id serial PRIMARY KEY,
  urakka integer REFERENCES urakka (id),
  alkupvm DATE, -- Hoitokauden alkamispäivämäärä
  loppupvm DATE, -- Hoitokauden loppupäivämäärä
  keskilampotila DECIMAL(2,2), -- Hoitokauden keskilämpotila
  pitka_keskilampotila DECIMAL(2,2) --Pitkän aikavälin keskilämpitila
)