ALTER TABLE silta DROP COLUMN siltaid;
ALTER TABLE silta DROP CONSTRAINT uniikki_siltanro;
ALTER TABLE silta ADD COLUMN siltatunnus TEXT NOT NULL DEFAULT 'silta';
ALTER TABLE silta ADD COLUMN siltaid INTEGER NOT NULL DEFAULT 0;
ALTER TABLE silta ADD CONSTRAINT uniikki_siltatunnus UNIQUE (siltatunnus);
ALTER TABLE silta ADD CONSTRAINT uniikki_siltaid UNIQUE (siltaid);