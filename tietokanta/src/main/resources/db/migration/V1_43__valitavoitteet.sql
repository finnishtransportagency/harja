
-- Luodaan taulu ylläpitourakoiden välitavoitteille

CREATE TABLE valitavoite (
  id serial primary key, 
  urakka integer references urakka (id),
  nimi varchar(128),

  takaraja date, -- milloin pitää olla valmis (PENDING: voi olla myös kohde? jolloin sidottu kohdeluettelon kohteen X pvm)
  viikkosakko decimal(12,2), -- sakkosumma per alkava viikko
  sakko decimal(12,2),   -- sakon summa (tilaaja voi asettaa)  

  valmis_pvm date,
  valmis_kommentti text, -- tekstikommentti valmistumisesta
  valmis_merkitsija integer REFERENCES kayttaja (id), -- kuka merkitsi valmistuneeksi
  valmis_merkitty timestamp,  -- milloin merkittiin valmistuneeksi
  
  -- muokkausmetadata
  luotu timestamp,
  muokattu timestamp,
  luoja integer REFERENCES kayttaja (id),
  muokkaaja integer REFERENCES kayttaja (id),
  poistettu boolean DEFAULT false
);
  
  
