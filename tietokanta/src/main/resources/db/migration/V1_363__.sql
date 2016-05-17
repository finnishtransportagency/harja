<<<<<<< HEAD
-- Poista kohdeluettelon alikohteelta toimenpide.
-- Tämä tieto menee nykyään esitäytettyyn POT-lomakkeeseen.
-- Jos toimenpide halutaan jatkossa näyttää kohdeluettelossa, se pitää joinia POTin kautta.
ALTER TABLE yllapitokohdeosa DROP COLUMN toimenpide;
=======
UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorateiden muokkaushoylays'::suoritettavatehtava
WHERE nimi = 'Sorateiden muokkaushöyläys';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorateiden polynsidonta'::suoritettavatehtava
WHERE nimi = 'Sorateiden pölynsidonta';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'paallysteiden juotostyot'::suoritettavatehtava
WHERE nimi = 'Päällysteiden juotostyöt';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'paallysteiden paikkaus'::suoritettavatehtava
WHERE nimi = 'Päällysteiden paikkaus';
>>>>>>> develop
