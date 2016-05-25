-- Ylläpitokohteelle suorittava TR-urakka
ALTER TABLE yllapit´okohde ADD COLUMN suorittava_tiemerkintaurakka integer REFERENCES urakka (id);