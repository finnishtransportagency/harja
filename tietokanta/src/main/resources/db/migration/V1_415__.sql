ALTER TABLE ilmoitus
  ADD COLUMN tr_lopputienumero INTEGER,
  ADD COLUMN ulkoinen_id INTEGER,
  ADD COLUMN luoja INTEGER,
  ADD CONSTRAINT ilmoitus_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id),
  ADD CONSTRAINT ilmoitus_uniikki_ulkoinen_id_luoja UNIQUE (ulkoinen_id, luoja),
  ALTER COLUMN ilmoitusid DROP NOT NULL;