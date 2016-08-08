ALTER TYPE yllapitokohdetyyppi RENAME TO yllapitokohdetyotyyppi;
ALTER TABLE yllapitokohde RENAME COLUMN tyyppi TO yllapitokohdetyotyyppi;

CREATE TYPE yllapitokohdetyyppi AS ENUM ('paallyste', 'sora', 'kevytliikenne');
ALTER TABLE yllapitokohde ADD COLUMN yllapitokohdetyyppi yllapitokohdetyyppi;
