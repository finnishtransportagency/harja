
UPDATE havainto SET tekija = 'tilaaja'::osapuoli WHERE tekija IS NULL;

ALTER TABLE havainto ALTER COLUMN tekija SET NOT NULL;
