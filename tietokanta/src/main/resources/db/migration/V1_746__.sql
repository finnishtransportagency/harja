INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-paikkaustiedot');

ALTER TABLE paikkauskohde
ADD COLUMN poistettu boolean DEFAULT FALSE,
ADD COLUMN luotu TIMESTAMP,
ADD COLUMN "muokkaaja-id" INTEGER,
ADD COLUMN muokattu TIMESTAMP,
ADD COLUMN "urakka-id" INTEGER;

