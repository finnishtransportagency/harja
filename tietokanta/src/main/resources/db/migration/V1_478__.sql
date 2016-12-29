INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'paivita-yllapitokohde');

ALTER TABLE yllapitokohdeosa
  ADD COLUMN ulkoinen_id INT;