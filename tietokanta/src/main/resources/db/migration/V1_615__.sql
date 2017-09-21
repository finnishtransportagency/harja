-- Vaadi ylläpitokohteille NOT NULL YHA-kohdenumero jos YHA_kohde
ALTER TABLE yllapitokohde ADD CONSTRAINT validi_kohde CHECK (yhaid IS NULL OR (yhaid IS NOT NULL AND yha_kohdenumero IS NOT NULL));
