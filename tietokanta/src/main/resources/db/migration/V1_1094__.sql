-- yllapitokohde yotyo voi olla null. Pakotetaan null arvot falseksi ja estetään null.
UPDATE yllapitokohde SET yotyo = COALESCE(yotyo, false) WHERE yotyo IS NULL;
ALTER TABLE yllapitokohde ALTER COLUMN yotyo SET NOT NULL;
