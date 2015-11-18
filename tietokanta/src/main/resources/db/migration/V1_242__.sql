-- Lisää ilmoitukselle uudet kentät
ALTER TABLE ilmoitus
ADD COLUMN otsikko TEXT,
ADD COLUMN lyhytselite TEXT,
ADD COLUMN pitkaselite TEXT;

UPDATE ilmoitus
SET lyhytselite = vapaateksti, pitkaselite = vapaateksti;

ALTER TABLE ilmoitus DROP COLUMN vapaateksti;