-- Poista hyppy-käsi
DELETE FROM yllapitokohdeosa WHERE hyppy IS TRUE;
ALTER TABLE yllapitokohdeosa DROP COLUMN hyppy;