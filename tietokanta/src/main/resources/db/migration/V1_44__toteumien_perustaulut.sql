-- Luodaan taulu toteumille
CREATE TABLE toteuma (
  id serial primary key,
  urakka integer references urakka (id),
  sopimus integer references sopimus (id),
  toimenpidekoodi varchar(16) references toimenpidekoodi (koodi), -- 4. tason toimenpide johon toteuma liittyy
  aika timestamp, -- Aika jolloin toteuma tapahtui
  luotu timestamp, -- Aika jolloin toteuma tallennettiin
  kokonaishintainentyo boolean -- Onko toteuma kokonaishintaista työtä. Jos ei kyseessä on yksikköhintainen työ.
);

-- Luodaan taulu reittipisteille
CREATE TABLE reittipiste (
  id serial primary key,
  toteuma integer references toteuma (id), -- Toteuma johon reittipiste liittyy
  aika timestamp, -- Aika jolloin oltiin pisteessä
  luotu timestamp, -- Aika jolloin piste tallennettiin
  x numeric,
  y numeric,
  z numeric
);

-- Luodaan taulu materiaalitoteumille
CREATE TABLE materiaalitoteuma (
  id serial primary key,
  toteuma integer references toteuma (id), -- Materiaalitoteuma liittyy joko suoraan toteumaan...
  reittipiste integer references reittipiste (id), -- ...tai tiettyyn reittipisteeseen
  luotu timestamp, -- Aika jolloin materiaalitoteuma tallennettiin
  materiaali integer REFERENCES materiaalikoodi (id), -- Käytetty materiaali
  maara numeric  -- Käytetty materiaalin määrä
);