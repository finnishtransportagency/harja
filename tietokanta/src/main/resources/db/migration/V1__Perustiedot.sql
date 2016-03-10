
-------------------------------------------------------------------------------------------
-- Perustiedot: liikennemuodot, organisaatiot ja yhteyshenkilöt                          --
-------------------------------------------------------------------------------------------

CREATE TYPE liikennemuoto AS ENUM ('T','V','R');
CREATE TYPE organisaatiotyyppi AS ENUM ('hallintayksikko','urakoitsija');

CREATE TYPE urakkatyyppi AS ENUM ('hoito', 'paallystys', 'tiemerkinta', 'valaistus');

CREATE TABLE organisaatio (
  id serial primary key,
  tyyppi organisaatiotyyppi,
  nimi varchar(128),           -- organisaation nimi (esim. 'Pohjois-Pohjanmaan ELY-keskus')
  lyhenne varchar(16),         -- valinnainen lyhempi nimi (esim. POP ELY)
  ytunnus char(9) unique,      -- ytunnus jos kyseessä urakoitsija
  liikennemuoto liikennemuoto, -- liikennemuoto, jos kyseessä hallintayksikko
  
  alue geometry,               -- toiminta-alue, jos kyseessä hallintayksikko
  katuosoite varchar(128),     -- osoite (esim. käyntiosoite)
  postinumero char(5),         -- postinumero
  sampoid varchar(16)          -- sampoid, jos kyseessä urakoitsija
);


CREATE TABLE urakka (
       id serial primary key,       -- sisäinen ID
       tyyppi urakkatyyppi,         -- urakan tyyppi
       sampoId varchar(16),         -- sampon hanke id (FIXME: varmista muoto)
       nimi varchar(128),           -- urakan nimi (esim. Oulun alueurakka 2005-2010)
       alkupvm date,                -- alkamispäivä
       loppupvm date,               -- loppumispäivä
       alue geometry,               -- urakan alue, voi olla tyhjä jos urakkaan (esim ylläpito) on linkitetty kohteet erikseen
       hallintayksikko INTEGER REFERENCES organisaatio (id),  -- minkä hallintayksikön urakka on kyseessä
       urakoitsija integer REFERENCES organisaatio (id)       -- kuka on tämän urakan urakoitsija
);       
       
-- Sisältää sekä SAMPOsta tulevat Resource tyyppiset, että Harjan kautta syötetyt yhteyshenkilöt
CREATE TABLE yhteyshenkilo (
  id serial primary key,
  etunimi varchar(64),
  sukunimi varchar(64),
  kayttajatunnus varchar(16), -- livi käyttäjätunnnus (ei pakollinen)
  tyopuhelin varchar(16),
  matkapuhelin varchar(16),
  sahkoposti varchar(255),
  organisaatio integer REFERENCES organisaatio (id) -- mihin organisaatioon kuuluu
);


CREATE TABLE yhteyshenkilo_urakka (
  id serial primary key,
  yhteyshenkilo integer references yhteyshenkilo (id),
  urakka integer references urakka (id),
  rooli varchar(32) -- Lilli rooli (jvh, lpk, uv, ...) tai AURAsta siirretty (Aluevastaava, Sillanvalvoja), NULL SAMPOsta tuodulle
);

-- päivystystaulu kertoo milloin yhteyshenkilö on urakassa päivystysvuorossa
CREATE TABLE paivystys (
  id serial primary key,
  yhteyshenkilo_urakka integer references yhteyshenkilo_urakka (id), -- henkilö<->urakka linkki, jolle tämä päivystys on
  vastuuhenkilo boolean,
  varahenkilo boolean,
  alku timestamp,
  loppu timestamp
);  

-------------------------------------------------------------------------------------------
-- Perustiedot: käyttäjät ja roolit                                                      --
-------------------------------------------------------------------------------------------

CREATE TABLE kayttaja (
  id serial primary key,    -- sisäinen id
  kayttajanimi varchar(16), -- livi käyttäjä id, esim LX722899
  etunimi varchar(64),      -- käyttäjän etunimi, esim Rolf
  sukunimi varchar(64),     -- käyttäjän sukunimi, esim Teflon
  sahkoposti varchar(255),  -- sähköpostiosoite
  puhelin varchar(32)       -- GSM numero
);


CREATE TYPE kayttajarooli AS ENUM (
   -- Yleinen käyttäjärooli, joka ei ole linkitetty mihinkään (korkeintaan väylämuoto)
   'jarjestelmavastuuhenkilo', 'vaylamuodon vastuuhenkilo', 'liikennepaivystaja', 'tilaajan asiantuntija',

   -- Rooli, joka on hallintayksikössä
   'tilaajan kayttaja',

   -- Tilaajan urakkaan sidottu rooli
   'urakanvalvoja', 'tilaajan laadunvalvontakonsultti',

   -- Urakoitsijan urakkaan sidottu rooli 
   'urakoitsijan paakayttaja',
   'urakoitsijan urakan vastuuhenkilo',
   'urakoitsijan kayttaja',
   'urakoitsijan laatuvastaava'
);

					

-- Käyttäjän linkitetty rooli urakassa
CREATE TABLE kayttaja_urakka_rooli (
  rooli kayttajarooli NOT NULL,
  kayttaja INTEGER  REFERENCES kayttaja (id),
  urakka INTEGER  REFERENCES urakka (id)
);
  
CREATE TABLE kayttaja_organisaatio_rooli (
  rooli kayttajarooli NOT NULL,
  kayttaja INTEGER  REFERENCES kayttaja (id),
  organisaatio INTEGER  REFERENCES organisaatio (id)
);


-------------------------------------------------------------------------------------------
-- Toimenpidekoodisto, joihin SAMPO koodit kuuluvat ja joista tehtäväkoodit tarkennetaan --
-------------------------------------------------------------------------------------------

CREATE TABLE toimenpidekoodi (
   id serial primary key,    -- sisäinen id
   nimi varchar (128),       -- toimenpiteen nimi (esim. "Laaja toimenpide")
   koodi char(5),            -- viisinumeroinen SAMPO koodi (esim. "23104"), Harjan toimenpidekoodeissa sama kuin emokoodin
   emo INTEGER REFERENCES toimenpidekoodi (id),  -- ylemmän tason toimenpidekoodi (tai NULL jos tämän taso on 1.)
   taso smallint,            -- koodin taso (1,2,3 = SAMPO tasot, 4 = "Hussin lista")
   luotu timestamp,
   muokattu timestamp,
   luoja integer REFERENCES kayttaja (id),
   muokkaaja integer REFERENCES kayttaja (id),
   poistettu boolean DEFAULT false 
);

-- Tehdään indeksit, jotta toimenpidekoodit löytää nopeasti
CREATE INDEX toimenpidekoodi_taso ON toimenpidekoodi (taso);
CREATE INDEX toimenpidekoodi_emo ON toimenpidekoodi (emo);
CREATE INDEX toimenpidekoodi_koodi ON toimenpidekoodi (koodi);

