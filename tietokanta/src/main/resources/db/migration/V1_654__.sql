-- Ylläpitokohteelle ja osalle muokkaustiedot
ALTER TABLE yllapitokohde ADD COLUMN muokattu TIMESTAMP;
ALTER TABLE yllapitokohdeosa ADD COLUMN muokattu TIMESTAMP;