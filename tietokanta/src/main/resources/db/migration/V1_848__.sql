ALTER TABLE pot2 ADD COLUMN asiatarkastus_pvm DATE;
ALTER TABLE pot2 ADD COLUMN asiatarkastus_tarkastaja TEXT;
ALTER TABLE pot2 ADD COLUMN asiatarkastus_hyvaksytty BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pot2 ADD COLUMN asiatarkastus_lisatiedot TEXT;
ALTER TABLE pot2 ADD COLUMN kasittelyaika_tekninen_osa DATE;
ALTER TABLE pot2 ADD COLUMN perustelu_tekninen_osa TEXT;