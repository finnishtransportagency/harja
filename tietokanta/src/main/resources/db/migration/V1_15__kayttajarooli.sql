-- käyttäjärooli ilman linkityksiä organisaatioon tai urakkaan

CREATE TABLE kayttaja_rooli (
  kayttaja integer REFERENCES kayttaja (id),
  rooli kayttajarooli,
  CONSTRAINT uniikki_kayttaja_rooli UNIQUE (kayttaja, rooli)
);


