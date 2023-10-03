-- Tehdään oikeudet kolumnista array jotta käyttäjille voidaan antaa usea enum (usea oikeus)
ALTER TABLE kayttaja ADD COLUMN api_oikeudet apioikeus[];

-- Importtaa kaikki käyttäjien vanhat arvot uuteen array kolumniin 
UPDATE kayttaja SET api_oikeudet = ARRAY[api_oikeus] WHERE api_oikeus IS NOT NULL;

-- Vanhan kolumnin voi nyt poistaa 
ALTER TABLE kayttaja DROP COLUMN api_oikeus;

-- Lisää pari uutta oikeustyyppiä 
ALTER TYPE apioikeus ADD VALUE 'luku'; -- Voi tehdä GET kutsuja 
ALTER TYPE apioikeus ADD VALUE 'kirjoitus'; -- Voi tehdä GET sekä POST kutsuja 
