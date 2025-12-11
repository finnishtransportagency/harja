
ALTER TABLE urakka_parametrit ADD COLUMN laskutusraja_kaytossa BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE urakka_parametrit SET laskutusraja_kaytossa = FALSE where laskutusraja_kaytossa IS NULL;

ALTER TABLE urakka_tavoite ADD COLUMN laskutusraja NUMERIC;
