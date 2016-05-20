-- Poista kohdeluettelon alikohteelta toimenpide.
-- Tämä tieto menee nykyään esitäytettyyn POT-lomakkeeseen.
-- Jos toimenpide halutaan jatkossa näyttää kohdeluettelossa, se pitää joinia POTin kautta.
ALTER TABLE yllapitokohdeosa DROP COLUMN toimenpide;

-- Kohteille ajorata ja kaista
ALTER TABLE yllapitokohde ADD COLUMN tr_ajorata INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN tr_kaista INTEGER;
ALTER TABLE yllapitokohdeosa ADD COLUMN tr_ajorata INTEGER;
ALTER TABLE yllapitokohdeosa ADD COLUMN tr_kaista INTEGER;