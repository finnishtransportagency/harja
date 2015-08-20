
CREATE TYPE tarkastustyyppi AS ENUM ('tiesto','talvihoito','soratie','laatu','pistokoe');
CREATE TYPE havainnon_paatostyyppi AS ENUM ('ei_sanktiota','sanktio','hylatty');
CREATE TYPE sakkoryhma AS ENUM ('A', 'B', 'C', 'muistutus');
CREATE TYPE havainnon_kasittelytapa AS ENUM ('tyomaakokous','puhelin','kommentit','muu');

CREATE TYPE osapuoli AS ENUM ('tilaaja','urakoitsija','konsultti');


CREATE TABLE tarkastus (
  id serial PRIMARY KEY,
  urakka integer NOT NULL REFERENCES urakka(id),
  pvm date NOT NULL,
  tyyppi tarkastustyyppi NOT NULL,
  tarkastaja varchar(128),

 -- Normaalit muokkausmetatiedot
  luoja integer REFERENCES kayttaja (id),
  luotu timestamp,
  muokkaaja integer REFERENCES kayttaja (id),
  muokattu timestamp,
  poistettu boolean DEFAULT false
);

CREATE TABLE tarkastuspiste (
  id serial PRIMARY KEY,
  tarkastus integer NOT NULL REFERENCES tarkastus(id),
  tarkastusaika timestamp,
  mittaaja varchar(128),
  tr_numero INTEGER,
  tr_alkuosa INTEGER,
  tr_alkuetaisyys INTEGER,
  tr_loppuosa INTEGER,
  tr_loppuetaisyys INTEGER,
  x numeric,
  y numeric
);

CREATE TABLE havainto (
  id serial PRIMARY KEY,
  kohde varchar(512),
  tekija osapuoli, -- kuka havainnon teki: tilaaja, konsultti vai urakoitsija
  toimenpideinstanssi integer NOT NULL REFERENCES toimenpideinstanssi (id),
  
  kasittelytapa havainnon_kasittelytapa, -- miten käsitelty
  muu_kasittelytapa varchar(255), -- lyhyt kuvaus, jos muu käsittelytapa
  
  paatos havainnon_paatostyyppi,
  perustelu varchar(4096),

  tarkastuspiste integer REFERENCES tarkastuspiste (id), -- jos liittyy tarkastuksen pisteeseen
  
  -- Normaalit muokkausmetatiedot
  luoja integer REFERENCES kayttaja (id),
  luotu timestamp,
  muokkaaja integer REFERENCES kayttaja (id),
  muokattu timestamp,
  poistettu boolean DEFAULT false
);

CREATE TABLE liite (
  id serial PRIMARY KEY, 
  tyyppi varchar(128), -- MIME tyyppi, esim. image/jpeg
  koko integer,
  nimi varchar(255),
  liite_oid oid,       -- oid liitteen datan large objektiin
  pikkukuva bytea,      -- pieni kuva suoraan bytearrayna

  luoja integer REFERENCES kayttaja (id),
  luotu timestamp
);

CREATE TABLE kommentti (
  id serial PRIMARY KEY, 
  tekija osapuoli,         -- kommentin tekijän osapuoli
  kommentti varchar(4096), -- itse kommentin teksti
  liite integer REFERENCES liite (id), -- optionaalinen liite
  
  -- Muokkaustiedot
  luoja integer REFERENCES kayttaja (id),
  luotu timestamp,
  muokkaaja integer REFERENCES kayttaja (id),
  muokattu timestamp,
  poistettu boolean DEFAULT false
);

CREATE TABLE havainto_kommentti (
  havainto integer REFERENCES havainto (id),
  kommentti integer REFERENCES kommentti (id)
);

CREATE TABLE sanktio (
  sakkoryhma sakkoryhma,
  maara numeric, -- NULL, jos tyyppi on muistutus
  perintapvm date NOT NULL,
  indeksi varchar(128) -- indeksin nimi, johon sidotaan 
);

