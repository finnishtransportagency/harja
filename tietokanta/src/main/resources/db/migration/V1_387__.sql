ALTER TYPE yllapitokohdetyyppi
RENAME TO yllapitokohdetyotyyppi;
CREATE TYPE yllapitokohdetyyppi AS ENUM ('paallyste', 'sora', 'kevytliikenne');
ALTER TABLE yllapitokohde ADD COLUMN yllapitokohdetyyppi yllapitokohdetyyppi;
