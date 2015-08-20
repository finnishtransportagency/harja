-- Lisää päällystila-enumiin uuden arvon

ALTER TYPE paallystystila RENAME TO _pltila2;

CREATE TYPE paallystystila AS ENUM ('aloitettu','valmis','lukittu');

ALTER TABLE paallystysilmoitus RENAME COLUMN tila TO _tila;

ALTER TABLE paallystysilmoitus ADD tila paallystystila;

ALTER TABLE paallystysilmoitus DROP COLUMN _tila;
DROP TYPE _pltila2;