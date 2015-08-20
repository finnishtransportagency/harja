
-------------------------------------------------------------------------------------------
-- Sopimustiedot: Samposta tulevien sopimusten perusteidot                               --
-------------------------------------------------------------------------------------------

CREATE TABLE sopimus (
  id serial primary key,
  nimi varchar(128),                      -- sopimuksen nimi (esim. 'Mitat ja massat merkit Äänekoski')
  alkupvm date,                           -- alkamispäivä
  loppupvm date,                          -- loppumispäivä
  sampoid varchar(16),                    -- sampoid
  urakka integer references urakka (id)   -- urakka johon sopimus liittyy
);