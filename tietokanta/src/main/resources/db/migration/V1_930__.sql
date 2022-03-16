-- Lisätään uudet sarakkeet harja.public.varustetoteuma_ulkoiset_kohdevirhe tauluun

ALTER TABLE varustetoteuma_ulkoiset_kohdevirhe ADD COLUMN aikaleima TIMESTAMP;
ALTER TABLE varustetoteuma_ulkoiset_kohdevirhe ADD COLUMN vastaus VARCHAR(8192);