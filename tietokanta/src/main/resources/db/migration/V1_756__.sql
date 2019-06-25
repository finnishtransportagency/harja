ALTER TABLE toimenpidekoodi
ADD COLUMN piilota BOOLEAN;

COMMENT ON COLUMN toimenpidekoodi.piilota IS 'Piilottaa toimenpiteen tai tehtävän Harjan hallintaosion listoilta.';
