-- Häiriöilmoituksille tyyppi

CREATE TYPE hairioilmoitus_tyyppi AS ENUM ('hairio', 'tiedote');
ALTER TABLE hairioilmoitus ADD COLUMN tyyppi hairioilmoitus_tyyppi;
UPDATE harja.public.hairioilmoitus SET tyyppi =  'hairio'::hairioilmoitus_tyyppi