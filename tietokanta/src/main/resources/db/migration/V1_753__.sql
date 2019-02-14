INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'poista-paikkaustiedot');

ALTER TABLE paikkauskohde
ADD COLUMN poistettu boolean DEFAULT FALSE,
ADD COLUMN luotu TIMESTAMP,
ADD COLUMN "muokkaaja-id" INTEGER,
ADD COLUMN muokattu TIMESTAMP,
ADD COLUMN "urakka-id" INTEGER;

ALTER TABLE paikkauskohde DROP CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja;
ALTER TABLE paikkauskohde ADD CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja_urakka UNIQUE ("ulkoinen-id", "urakka-id", "luoja-id");

