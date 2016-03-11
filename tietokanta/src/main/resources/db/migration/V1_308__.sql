-- Lisää yhteyshenkilo_urakka tauluun luoja ja vaadi uniikki (urakka, rooli, yhteyshenkilo) (konversioita varten)
ALTER TABLE yhteyshenkilo_urakka ADD COLUMN luoja INTEGER;
ALTER TABLE yhteyshenkilo_urakka ADD CONSTRAINT uniikki_yhteyshenkilo_urakka UNIQUE (urakka, rooli, yhteyshenkilo);