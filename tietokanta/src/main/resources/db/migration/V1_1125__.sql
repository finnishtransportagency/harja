--- Poista turhaksi jääneet pot-lahetykseen liittyvät Velho tiedot
ALTER TABLE yllapitokohde DROP COLUMN IF EXISTS velho_lahetyksen_aika;
ALTER TABLE yllapitokohde DROP COLUMN IF EXISTS velho_lahetyksen_tila;
ALTER TABLE yllapitokohde DROP COLUMN IF EXISTS velho_lahetyksen_vastaus;

ALTER TABLE pot2_paallystekerros DROP COLUMN IF EXISTS velho_lahetyksen_aika;
ALTER TABLE pot2_paallystekerros DROP COLUMN IF EXISTS velho_rivi_lahetyksen_tila;
ALTER TABLE pot2_paallystekerros DROP COLUMN IF EXISTS velho_lahetyksen_vastaus;

ALTER TABLE pot2_alusta DROP COLUMN IF EXISTS velho_lahetyksen_aika;
ALTER TABLE pot2_alusta DROP COLUMN IF EXISTS velho_rivi_lahetyksen_tila;
ALTER TABLE pot2_alusta DROP COLUMN IF EXISTS velho_lahetyksen_vastaus;

DROP TYPE IF EXISTS velho_lahetyksen_tila_tyyppi;
DROP TYPE IF EXISTS velho_rivi_lahetyksen_tila_tyyppi;