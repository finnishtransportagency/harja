-- Laadunseurannan tietomallimuutokset

-- Vakiohavainto: uusi taulu, jossa vakiohavaintokoodit
CREATE TABLE vakiohavainto (
  id SERIAL PRIMARY KEY,
  nimi varchar(128),
  emo INTEGER REFERENCES vakiohavainto (id),
  tarkastustyyppi tarkastustyyppi
);

COMMENT ON TABLE vakiohavainto IS 'Vakiohavainto on kooditettu yleinen tarkastustyökalun tuottama tieto kuten: lumisuus, liukkaus, jne. Tarkastustyyppi kertoo minkä tyyppisessä tarkastuksessa tätä vakiohavaintoa tarjotaan käyttöliittymässä. Emo kentän perusteella tuleva hierarkia tekee hierarkisen valinnan tarkastustyökalussa.';

-- FIXME: luo testidataa, jotain vakiohavaintoja


-- Tarkastusajo: uusi taulu, johon tarkastus voi kuulua
CREATE TABLE tarkastusajo (
  id SERIAL PRIMARY KEY,
  ulkoinen_id INTEGER,
  luoja INTEGER REFERENCES kayttaja (id),
  luotu TIMESTAMP
);

COMMENT ON TABLE tarkastusajo IS 'Tarkastus voi optionaalisesti kuulua tiettyyn tarkastusajoon, jos urakoitsija voi ne ryvästää. Harjan kenttätarkastustyökalu luo uuden ajon aina kun tarkastaminen aloitetaan ja liittää kaikki samaan ajoon.';


-- Tarkastus:
-- lisää vakiohavainnot ja havainnot teksti
-- poista linkki havainto tauluun
-- lisää omat kommentit ja liitteet
ALTER TABLE tarkastus ADD COLUMN tarkastusajo INTEGER REFERENCES tarkastusajo (id);
CREATE TABLE tarkastus_vakiohavainto (
  tarkastus INTEGER REFERENCES tarkastus (id),
  vakiohavainto INTEGER REFERENCES vakiohavainto (id)
);
COMMENT ON TABLE tarkastus_vakiohavainto IS 'Linkkitaulu, jolla yhden tarkastuksen vakiohavainnot liitetään siihen';
ALTER TABLE tarkastus ADD COLUMN havainnot TEXT;
ALTER TABLE tarkastus DROP COLUMN havainto;
CREATE TABLE tarkastus_kommentti (
  tarkastus INTEGER REFERENCES tarkastus (id),
  kommentti INTEGER references kommentti (id)
);
COMMENT ON TABLE tarkastus_kommentti IS 'Linkkitaulu, jolla kommentit liitetään tarkastukseen';

CREATE TABLE tarkastus_liite (
  tarkastus INTEGER REFERENCES tarkastus (id),
  liite INTEGER REFERENCES liite (id)
);
COMMENT ON TABLE tarkastus_liite IS 'Tarkastukseen liitetyt tiedostot.';


-- Havainto:
ALTER TABLE havainto RENAME TO laatupoikkeama;
ALTER TABLE sanktio RENAME COLUMN havainto TO laatupoikkeama;

ALTER TABLE havainto_kommentti RENAME TO laatupoikkeama_kommentti;
ALTER TABLE laatupoikkeama_kommentti RENAME COLUMN havainto TO laatupoikkeama;
ALTER TABLE havainto_liite RENAME TO laatupoikkeama_liite;
ALTER TABLE laatupoikkeama_liite RENAME COLUMN havainto TO laatupoikkeama;






