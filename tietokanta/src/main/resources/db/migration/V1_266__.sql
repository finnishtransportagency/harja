-- Päällystysurakan aikataulunäkymän tarvitsemat tiedot
ALTER TABLE paallystyskohde ADD COLUMN aikataulu_paallystys_alku TIMESTAMP;
ALTER TABLE paallystyskohde ADD COLUMN aikataulu_paallystys_loppu TIMESTAMP;
ALTER TABLE paallystyskohde ADD COLUMN aikataulu_tiemerkinta_alku DATE ;
ALTER TABLE paallystyskohde ADD COLUMN aikataulu_tiemerkinta_loppu DATE;
ALTER TABLE paallystyskohde ADD COLUMN aikataulu_kohde_valmis DATE;
ALTER TABLE paallystyskohde ADD COLUMN aikataulu_muokattu TIMESTAMP;
ALTER TABLE paallystyskohde ADD COLUMN aikataulu_muokkaaja INTEGER REFERENCES kayttaja(id);