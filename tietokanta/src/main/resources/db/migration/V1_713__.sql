-- Kun kolumneja poistetaan taulusta, pitää näkymä poistaa ja rakentaa uudelleen
-- jotta näkymässä ei ole riippuvuutta poistettaviin sarakkeisiin
DROP VIEW IF EXISTS tietyoilmoitus_pituus;

-- Poistetaan käyttämättömät turhat kolumnit tietyoilmoitus-taulusta
ALTER TABLE tietyoilmoitus
  DROP COLUMN lahetetty,
  DROP COLUMN lahetysid,
  DROP COLUMN tila;
s

-- Luodaan uusi taulu johon kerätään kaikki email lähetykset sekä niiden kuittaukset
CREATE TABLE tietyoilmoituksen_email_lahetys  (
  id SERIAL PRIMARY KEY,
  tietyoilmoitus INTEGER REFERENCES tietyoilmoitus (id),
  tiedostonimi TEXT,
  lahetetty TIMESTAMP NOT NULL,
  lahetysid VARCHAR(255), -- JMS Message ID
  lahettaja INTEGER REFERENCES kayttaja (id) NOT NULL,
  kuitattu TIMESTAMP,
  lahetysvirhe TIMESTAMP -- mahdollisen lähetysvirheen aikaleima, toivottavasti usein tyhjä
);

