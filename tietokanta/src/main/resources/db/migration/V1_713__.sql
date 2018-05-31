-- Kun kolumneja poistetaan taulusta, pitää näkymä poistaa ja rakentaa uudelleen
-- jotta näkymässä ei ole riippuvuutta poistettaviin sarakkeisiin
DROP VIEW IF EXISTS tietyoilmoitus_pituus;

-- Poistetaan käyttämättömät turhat kolumnit tietyoilmoitus-taulusta
ALTER TABLE tietyoilmoitus
  DROP COLUMN lahetetty,
  DROP COLUMN lahetysid,
  DROP COLUMN tila;

CREATE VIEW tietyoilmoitus_pituus AS
  SELECT tti.*, CASE
                WHEN (tti.osoite).losa IS NOT NULL THEN
                  ST_Length(tr_osoitteelle_viiva3(
                                (tti.osoite).tie, (tti.osoite).aosa,
                                (tti.osoite).aet, (tti.osoite).losa,
                                (tti.osoite).let))
                ELSE
                  0
                END
    AS pituus FROM tietyoilmoitus tti;

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

