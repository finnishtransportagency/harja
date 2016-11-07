-- Uusi kantataulu, jossa järjestelmä-käyttäjille annetaan oikeudet toiseen urakkaan

CREATE TABLE kayttajan_lisaoikeudet_urakkaan (
  id          SERIAL PRIMARY KEY,
  kayttaja    INTEGER REFERENCES kayttaja (id) NOT NULL,
  urakka      INTEGER REFERENCES urakka (id) NOT NULL
);
COMMENT ON TABLE kayttajan_lisaoikeudet_urakkaan IS 'Rivi tässä taulussa antaa järjestelmäkäyttäjälle oikeudet urakkaan.';

ALTER TABLE kayttajan_lisaoikeudet_urakkaan ADD CONSTRAINT uniikki_lisaoikeus UNIQUE (urakka, kayttaja);
