-- vv_materiaali-tauluun poistettu ja poistaja
ALTER TABLE vv_materiaali ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE vv_materiaali ADD COLUMN poistaja INTEGER REFERENCES kayttaja (id) NOT NULL;