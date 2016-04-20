-- Käyttäjärooli on nyt merkkijono, koska oletetaan niiden muuttuvan elinkaaren aikana
-- mahdolliset roolit ja niiden käyttöoikeudet luetaan roolit.xlsx tiedostosta
ALTER TABLE kayttaja_rooli ADD COLUMN roolitxt VARCHAR(64);
ALTER TABLE kayttaja_urakka_rooli ADD COLUMN roolitxt VARCHAR(64);
ALTER TABLE kayttaja_organisaatio_rooli ADD COLUMN roolitxt VARCHAR(64);

UPDATE kayttaja_rooli SET roolitxt = rooli::text;
UPDATE kayttaja_urakka_rooli SET roolitxt = rooli::text;
UPDATE kayttaja_organisaatio_rooli SET roolitxt = rooli::text;

ALTER TABLE kayttaja_rooli DROP COLUMN rooli;
ALTER TABLE kayttaja_urakka_rooli DROP COLUMN rooli;
ALTER TABLE kayttaja_organisaatio_rooli DROP COLUMN rooli;

ALTER TABLE kayttaja_rooli RENAME COLUMN roolitxt TO rooli;
ALTER TABLE kayttaja_urakka_rooli RENAME COLUMN roolitxt TO rooli;
ALTER TABLE kayttaja_organisaatio_rooli RENAME COLUMN roolitxt TO rooli;

DROP TYPE kayttajarooli;
