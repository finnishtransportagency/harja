-- Laatupoikkeamalle mahdollinen viittaus ylläpitokohteeseen
ALTER TABLE laatupoikkeama ADD COLUMN yllapitokohde INTEGER REFERENCES yllapitokohde(id);