ALTER TABLE ilmoitus
  ADD COLUMN tr_lopputienumero INTEGER,
  ADD COLUMN ulkoinen_id VARCHAR(25),
  ADD COLUMN luoja INTEGER,
  ADD CONSTRAINT ilmoitus_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id),
  ALTER COLUMN ilmoitusid DROP NOT NULL;