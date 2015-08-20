
-- Sillat, siltatarkastukset ja kohteet

CREATE TABLE silta (
  id serial primary key,
  tyyppi char(1), -- TR:n siltatyyppi 1,2,3 tai 4
  siltanro integer not null, -- sillan numero siltarekisteristä
  siltanimi varchar(255), -- sillan nimi
  askelipaino numeric(10,2), -- suurin sallittu akselipaino (t)
  telipaino numeric(10,2), -- suurin sallittu telipaino (t)
  ajoneuvopaino numeric(10,2), -- suurin sallittu ajoneuvon paino (t)
  yhdistelmapaino numeric(10,2), -- suurin sallittu ajoneuvoyhdistelman paino (t)
  siltaid varchar(32), -- TR<->Siltarekisteri tiedot yhdistävä tunniste (FIXME: tietotyyppi ok?)
  alue geometry
);

CREATE TABLE siltatarkastus (
  id serial primary key,
  tarkastusaika timestamp,
  tarkastaja varchar(128), -- tarkastajan kuvaus, jos muu kuin luoja
  luotu timestamp,
  muokattu timestamp,
  luoja integer REFERENCES kayttaja (id),
  muokkaaja integer REFERENCES kayttaja (id),
  poistettu boolean DEFAULT false
);

-- Yksittäisen tarkastuksen yhden kohteen arvo
CREATE TABLE siltatarkastuskohde (
  siltatarkastus integer REFERENCES siltatarkastus (id),
  kohde smallint, -- tarkastuskohteen numero, esim. 12 Liikuntasaumalaitteiden siisteys
  tulos char(1), -- A,B,C tai D
  lisatieto varchar(255)
);

  
