-- Poista toimimaton uniikkiconstraint
ALTER TABLE yllapitokohde
  DROP CONSTRAINT validi_kohde;

-- Päivitä negatiivisiksi päivitetyt kohdenumerot nulleiksi
UPDATE yllapitokohde
SET yha_kohdenumero = NULL
WHERE yha_kohdenumero < 0;
