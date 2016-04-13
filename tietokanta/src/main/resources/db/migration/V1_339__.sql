CREATE TABLE tarkastus_laatupoikkeama (
  tarkastus INTEGER REFERENCES tarkastus (id)      NOT NULL,
  laatupoikkeama INTEGER REFERENCES laatupoikkeama (id) NOT NULL,
  CONSTRAINT uniikki_tarkastuksen_laatupoikkeama UNIQUE (tarkastus, laatupoikkeama)
);