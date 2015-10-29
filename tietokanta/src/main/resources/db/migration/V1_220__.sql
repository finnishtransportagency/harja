-- Lisää hankkeille Sampon tyyppitiedot (väylämuoto, urakkatyyppi & urakan alityyppi)
ALTER TABLE hanke ADD COLUMN sampo_tyypit VARCHAR(3);