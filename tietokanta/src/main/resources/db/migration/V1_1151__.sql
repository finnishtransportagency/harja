ALTER TABLE yllapitokohde ADD COLUMN lahettaja integer REFERENCES kayttaja (id);
