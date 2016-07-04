-- Yhtenäistä Harjan turvallisuuspoikkeama TURI:n kanssa
ALTER TABLE turvallisuuspoikkeama DROP COLUMN paattynyt;
ALTER TABLE turvallisuuspoikkeama DROP COLUMN tyotehtava;

CREATE TYPE turvallisuuspoikkeama_tila AS ENUM ('avoin','kasitelty','taydennetty', 'suljettu');

ALTER TABLE turvallisuuspoikkeama ADD COLUMN tapahtuman_otsikko VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN paikan_kuvaus VARCHAR(2048);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vaarallisten_aineiden_kuljetus BOOLEAN;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vaarallisten_aineiden_vuoto BOOLEAN;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tila);

ALTER TABLE turvallisuuspoikkeama ALTER COLUMN kuvaus TYPE VARCHAR(4000);
ALTER TABLE turvallisuuspoikkeama ALTER COLUMN aiheutuneet_seuraukset TYPE VARCHAR(4000);

CREATE TYPE korjaavatoimenpide_tila AS ENUM ('avoin','siirretty','toteutettu');

ALTER TABLE korjaavatoimenpide ADD COLUMN vapaateksti VARCHAR(2048);
ALTER TABLE korjaavatoimenpide ADD COLUMN integer REFERENCES kayttaja (id);
-- TODO Vastuuhenkilö on livi-tunnus?
ALTER TABLE korjaavatoimenpide ADD COLUMN toteuttaja;
ALTER TABLE korjaavatoimenpide ADD COLUMN tila;
