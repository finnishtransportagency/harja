-- Poista kohdeluettelon alikohteelta toimenpide.
-- Tämä tieto menee nykyään esitäytettyyn POT-lomakkeeseen.
-- Jos toimenpide halutaan jatkossa näyttää kohdeluettelossa, se pitää joinia POTin kautta.
ALTER TABLE yllapitokohdeosa DROP COLUMN toimenpide;