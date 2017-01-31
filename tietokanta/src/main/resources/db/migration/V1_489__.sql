INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-maaramuutokset');

ALTER TABLE yllapitokohteen_maaramuutos
  ADD COLUMN jarjestelma VARCHAR(128);

ALTER TABLE yllapitokohteen_maaramuutos
  ADD COLUMN ulkoinen_id INTEGER;

ALTER TABLE yllapitokohteen_maaramuutos
  ADD COLUMN ennustettu_maara NUMERIC;
