-- Ylläpitokohteelle suorittava TR-urakka
ALTER TABLE yllapitokohde ADD COLUMN suorittava_tiemerkintaurakka integer REFERENCES urakka (id);