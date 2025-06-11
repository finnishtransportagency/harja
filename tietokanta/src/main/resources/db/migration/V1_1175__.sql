-- Paikkauskohteelle suorittava TR-urakka
ALTER TABLE paikkauskohde ADD COLUMN suorittava_tiemerkintaurakka integer REFERENCES urakka (id);

-- Lisää muut kustannukset päällystyskohteille
ALTER TABLE tiemerkinta_yllapitokohteen_kustannus ADD COLUMN muut_kustannukset NUMERIC(10, 2);

-- Lisää muut kustannukset paikkauskohteille
ALTER TABLE tiemerkinta_paikkauskohteen_kustannus ADD COLUMN muut_kustannukset NUMERIC(10, 2);
