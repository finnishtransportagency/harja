CREATE TABLE materiaalikoodi (
  id serial primary key,   -- sisäinen tunniste 
  nimi varchar(128),       -- materiaalin nimi (esim. Talvisuolaliuos NaCl)
  yksikko varchar(16)      -- yksikkö, jossa materiaalin määrä lasketaan (esim. kg)

);


CREATE TABLE materiaalin_kaytto (
  id serial primary key,

  -- hoitokausi, jolle materiaalin käyttö on 
  alkupvm date,
  loppupvm date,

  -- kuinka paljon materiaali voi käyttää
  maara numeric,

  -- mikä materiaali on kyseessä
  materiaali integer REFERENCES materiaalikoodi (id),

  urakka integer REFERENCES urakka (id),
  sopimus integer REFERENCES sopimus(id),

  -- valinnainen: pohjavesialue jolle tämä materiaalin käyttö on
  pohjavesialue integer REFERENCES pohjavesialue (id),

  luotu timestamp DEFAULT NOW(),
  muokattu timestamp,
  luoja integer REFERENCES kayttaja (id),
  muokkaaja integer REFERENCES kayttaja (id),
  poistettu boolean DEFAULT false
  
);

