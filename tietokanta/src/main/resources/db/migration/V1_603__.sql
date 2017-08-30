ALTER TABLE yllapitokohde
  RENAME yhatunnus TO tunnus;

ALTER TABLE yllapitokohdeosa
  DROP COLUMN tunnus;
