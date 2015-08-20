-- Samalla käyttäjänimellä voi olla useita, jos käyttäjä on jo poistettu

ALTER TABLE kayttaja DROP CONSTRAINT uniikki_kayttajanimi;

CREATE INDEX kayttaja_kayttajanimi ON kayttaja (kayttajanimi);
