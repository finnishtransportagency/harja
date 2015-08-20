CREATE TABLE pohjavesialue (
  id serial primary key, -- sisäinen id
  nimi varchar(128),     -- pohjavesialueen tunnus
  tunnus varchar(16),    -- pvaluetunnus
  alue geometry,         -- polygonialue
  muokattu timestamp     -- muokkaus tulee shp tiedostosta (tietokantaan luonti/poisto) ajat ei kiinnosta meitä
);
