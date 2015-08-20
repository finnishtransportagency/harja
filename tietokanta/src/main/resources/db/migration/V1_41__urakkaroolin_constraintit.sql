DELETE FROM kayttaja_urakka_rooli WHERE kayttaja IS NULL or urakka IS NULL;
ALTER TABLE kayttaja_urakka_rooli ALTER COLUMN kayttaja SET NOT NULL;
ALTER TABLE kayttaja_urakka_rooli ALTER COLUMN urakka SET NOT NULL;
