ALTER TABLE silta DROP COLUMN siltaid;
ALTER TABLE silta DROP CONSTRAINT uniikki_siltanro;

ALTER TABLE silta ADD COLUMN siltatunnus TEXT;
ALTER TABLE silta ADD COLUMN siltaid INTEGER;

UPDATE silta
SET siltatunnus = 'silta' || CAST(id AS TEXT), siltaid = id;

ALTER TABLE silta ALTER COLUMN siltatunnus SET NOT NULL;
ALTER TABLE silta ALTER COLUMN siltaid SET NOT NULL;

ALTER TABLE silta ADD CONSTRAINT uniikki_siltatunnus UNIQUE (siltatunnus);
ALTER TABLE silta ADD CONSTRAINT uniikki_siltaid UNIQUE (siltaid);


