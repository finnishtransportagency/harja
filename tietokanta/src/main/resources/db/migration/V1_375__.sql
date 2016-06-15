ALTER TABLE yllapitokohdeosa ADD COLUMN tunnus VARCHAR(1);
ALTER TABLE yllapitokohde ADD COLUMN lahetetty TIMESTAMP;
ALTER TABLE yllapitokohde ADD COLUMN lahetys_onnistunut BOOLEAN;
ALTER TABLE yllapitokohde ADD COLUMN lahetysvirhe VARCHAR;