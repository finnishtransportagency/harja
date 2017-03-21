-- Ylläpitokohteen lahetys_onnistunut kentän arvoksi pelkästään true/false
UPDATE yllapitokohde SET lahetys_onnistunut = FALSE WHERE lahetys_onnistunut IS NULL;
ALTER TABLE yllapitokohde ALTER COLUMN lahetys_onnistunut SET DEFAULT FALSE;