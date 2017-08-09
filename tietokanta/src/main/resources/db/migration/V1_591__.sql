-- vv_materiaali-tauluun poistettu ja poistaja
ALTER TABLE vv_materiaali ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE vv_materiaali ADD COLUMN poistaja INTEGER REFERENCES kayttaja (id);
ALTER TABLE vv_materiaali ADD CONSTRAINT poistaja_olemassa CHECK (poistettu IS NOT TRUE OR (poistettu IS TRUE AND poistaja IS NOT NULL));
