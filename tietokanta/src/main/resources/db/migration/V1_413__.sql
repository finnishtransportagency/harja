-- Ylläpitokohteen datalle validointisääntöjä
ALTER TABLE yllapitokohde ADD CONSTRAINT paallystys_loppu_validi CHECK (aikataulu_paallystys_loppu IS NULL OR aikataulu_paallystys_alku IS NOT NULL);
ALTER TABLE yllapitokohde ADD CONSTRAINT valmis_tiemerkintaan_validi CHECK (valmis_tiemerkintaan IS NULL OR aikataulu_paallystys_loppu IS NOT NULL);