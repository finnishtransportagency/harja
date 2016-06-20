-- Lisää asiatarkastuksen tiedot POT-lomakkeelle
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_pvm DATE;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_tarkastaja VARCHAR(1024);
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_tekninen_osa BOOLEAN;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_taloudellinen_osa BOOLEAN;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_kommentit VARCHAR(4096);