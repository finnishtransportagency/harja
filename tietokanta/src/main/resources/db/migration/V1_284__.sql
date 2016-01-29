ALTER TABLE silta DROP COLUMN siltaid;
ALTER TABLE silta DROP CONSTRAINT uniikki_siltanro;
ALTER TABLE silta ADD COLUMN siltatunnus TEXT NOT NULL;
ALTER TABLE silta ADD COLUMN siltaid INTEGER NOT NULL;

-- Päivitä dummy id:t aluksi paikalleen
UPDATE silta
SET siltatunnus = 'silta' || CAST(id AS TEXT);

ALTER TABLE silta ADD CONSTRAINT uniikki_siltatunnus UNIQUE (siltatunnus);
ALTER TABLE silta ADD CONSTRAINT uniikki_siltaid UNIQUE (siltaid);