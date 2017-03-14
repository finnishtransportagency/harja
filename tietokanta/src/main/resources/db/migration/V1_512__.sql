ALTER TABLE tietyoilmoitus
  ADD COLUMN urakoitsija INTEGER REFERENCES organisaatio (id);

ALTER TABLE tietyoilmoitus
  ADD COLUMN urakoitsijan_nimi VARCHAR(128);