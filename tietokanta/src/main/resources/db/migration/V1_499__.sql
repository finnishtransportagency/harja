-- Ylläpitokohteen määrämuutos -taulukossa tietyt kentät ei voi olla negatiivisia

ALTER TABLE yllapitokohteen_maaramuutos ADD CONSTRAINT tilattu_maara_ei_neg CHECK (tilattu_maara >= 0);
ALTER TABLE yllapitokohteen_maaramuutos ADD CONSTRAINT toteutunut_maara_ei_neg CHECK (toteutunut_maara >= 0);
ALTER TABLE yllapitokohteen_maaramuutos ADD CONSTRAINT yksikkohinta_ei_neg CHECK (yksikkohinta >= 0);
ALTER TABLE yllapitokohteen_maaramuutos ADD CONSTRAINT ennustettu_maara_ei_neg CHECK (ennustettu_maara >= 0);