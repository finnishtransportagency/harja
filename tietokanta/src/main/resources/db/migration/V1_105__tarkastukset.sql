

-- Dropataan aiemmin luodut taulut, jotka eivät ole uuden tietomallin mukaiset
DROP TABLE tarkastuspiste CASCADE;
DROP TABLE tarkastus CASCADE;




-- Tarkastus on yksittäinen käynti jossain pisteessä.
-- Tarkastuksilla ei ole ylätason konseptia kierrokselle.

CREATE TABLE tarkastus (
  id serial PRIMARY KEY,
  urakka integer references urakka (id),
  sopimus integer references sopimus (id),
  aika timestamp,
  tr_numero integer,
  tr_alkuosa integer,
  tr_loppuosa integer,
  tr_loppuetaisyys integer,
  sijainti point,
  tarkastaja varchar(128), -- tarkastajan nimi
  mittaaja varchar(128),   -- mittaajan nimi
  tyyppi tarkastustyyppi,  -- ks. tyyppispesifiset mittausdatat alla

  havainto integer references havainto (id), -- mahdollinen havainto
  
  luotu timestamp DEFAULT current_timestamp,
  luoja integer references kayttaja (id),
  muokattu timestamp,
  muokkaaja integer references kayttaja (id)
);


CREATE TABLE talvihoitomittaus (
 tarkastus integer references tarkastus (id),
 talvihoitoluokka varchar(4),
 lumimaara NUMERIC(6, 2),
 epatasaisuus NUMERIC(6, 2),
 kitka NUMERIC(6, 2),
 lampotila NUMERIC(6, 2),
 ajosuunta smallint
);


CREATE TABLE soratiemittaus (
 tarkastus integer references tarkastus (id),
 hoitoluokka smallint, -- 1 tai 2
 tasaisuus smallint, -- 1 - 5
 kiinteys smallint, -- 1 - 5
 polyavyys smallint, -- 1 - 5
 sivukaltevuus NUMERIC(4,2) -- prosenttiluku, (ok välillä 3 - 7 %)
);
 
  
