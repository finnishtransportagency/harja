-- Luo urakanvastuuhenkilö

CREATE TABLE urakanvastuuhenkilo (
  id SERIAL PRIMARY KEY,
  urakka INTEGER REFERENCES urakka (id),
  kayttajanimi VARCHAR(32), -- livin extranet tunnus
  nimi varchar(255), -- Käyttäjän oikea nimi
  rooli VARCHAR(32), -- ELY_Urakanvalvoja (ELY) tai vastuuhenkilo (urakoitsija)
  ensisijainen BOOLEAN
);
