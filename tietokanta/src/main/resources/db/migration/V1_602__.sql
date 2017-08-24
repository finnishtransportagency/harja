<<<<<<< HEAD
ALTER TABLE yllapitokohde
  RENAME yhatunnus TO tunnus;

ALTER TABLE yllapitokohdeosa
  DROP COLUMN tunnus;
=======
-- Indeksoi toteuman alkamisaika

CREATE INDEX toteuma_alkanut_idx ON toteuma (alkanut);
>>>>>>> develop
