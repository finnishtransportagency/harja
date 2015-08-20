--Kohdeluettelon taulut:

CREATE TABLE paallystyskohde (
  id serial PRIMARY KEY,
  urakka integer REFERENCES urakka (id),
  sopimus integer REFERENCES sopimus (id),
  kohdenumero varchar(16), 
  nimi varchar(128),
  sopimuksen_mukaiset_tyot NUMERIC(10, 2),
  lisatyot NUMERIC(10, 2),
  -- muutostyöt tulevat päällystysilmoitukseksta
  arvonvahennukset NUMERIC(10, 2),
  bitumi_indeksi NUMERIC(10, 2),
  kaasuindeksi NUMERIC(10, 2)
  -- kokonaishinta lasketaan hinta+muutos+lisa+bit.ind+kaasu.ind
);


CREATE TABLE paallystyskohdeosa (
  id serial PRIMARY KEY,
  paallystyskohde integer REFERENCES paallystyskohde (id),
  nimi varchar(128),
  tr_numero INTEGER,
  tr_alkuosa INTEGER,
  tr_alkuetaisyys INTEGER,
  tr_loppuosa INTEGER,
  tr_loppuetaisyys INTEGER,
  kvl INTEGER, -- keskimääräinen vuorokausiliikenne (FIXME: onko integer ok vai numeric?)
  nykyinen_paallyste INTEGER, -- ks. päällystetyypit koodisto
  toimenpide varchar(256) -- tekstikuvaus toimenpiteestä
);

CREATE TYPE paallystystila AS ENUM ('aloitettu','valmis','lukittu');

CREATE TABLE paallystysilmoitus (
  paallystyskohde integer REFERENCES paallystyskohde (id), -- kohteen päällystysilmoitus
  tila paallystystila, -- jos ei aloitettu, ei päällystysilmoitusta ole lainkaan
  ilmoitustiedot jsonb, -- ilmoituslomakkeen tiedot
  aloituspvm date, -- aloitettu pvm
  valmistumispvm date, -- valmistumis pvm
  muutoshinta NUMERIC(10, 2),

  luotu timestamp,
  muokattu timestamp,
  luoja integer REFERENCES kayttaja (id),
  muokkaaja integer REFERENCES kayttaja (id),
  poistettu boolean DEFAULT false
);
