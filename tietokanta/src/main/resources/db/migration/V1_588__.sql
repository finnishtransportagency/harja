-- Indeksoi ylläpitokohteita

-- Indeksoidaan sijainti, jotta sen perusteella voidaan hakea rajatulle alueelle
CREATE INDEX yllapitokohdeosa_sijainti_idx ON yllapitokohdeosa USING GIST (sijainti);

-- Indeksoidaan foreign key
CREATE INDEX yllapitokohdeosa_yllapitokohde_idx ON yllapitokohdeosa (yllapitokohde);
