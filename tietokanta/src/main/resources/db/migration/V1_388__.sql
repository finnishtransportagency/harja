-- Yhtenäistä Harjan turvallisuuspoikkeama TURI:n kanssa

-- Turvallisuuspoikkeaman päivitys

ALTER TABLE turvallisuuspoikkeama DROP COLUMN paattynyt;
ALTER TABLE turvallisuuspoikkeama DROP COLUMN tyotehtava;

CREATE TYPE turvallisuuspoikkeama_tila AS ENUM ('avoin','kasitelty','taydennetty', 'suljettu');

ALTER TABLE turvallisuuspoikkeama ADD COLUMN tapahtuman_otsikko VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN paikan_kuvaus VARCHAR(2048);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vaarallisten_aineiden_kuljetus BOOLEAN;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vaarallisten_aineiden_vuoto BOOLEAN;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tila turvallisuuspoikkeama_tila;

ALTER TABLE turvallisuuspoikkeama ALTER COLUMN kuvaus TYPE VARCHAR(4000);
ALTER TABLE turvallisuuspoikkeama ALTER COLUMN aiheutuneet_seuraukset TYPE VARCHAR(4000);

-- Migratoi wanha data
ALTER TABLE turvallisuuspoikkeama SET tila = 'avoin'::turvallisuuspoikkeama_tila;
ALTER TABLE turvallisuuspoikkeama SET vaarallisten_aineiden_kuljetus = FALSE;
ALTER TABLE turvallisuuspoikkeama SET vaarallisten_aineiden_vuoto = FALSE;

-- Korjaavan toimenpiteen päivitys

ALTER TABLE korjaavatoimenpide DROP COLUMN vastaavahenkilo;

CREATE TYPE korjaavatoimenpide_tila AS ENUM ('avoin','siirretty','toteutettu');

ALTER TABLE korjaavatoimenpide ADD COLUMN otsikko VARCHAR(2048);
ALTER TABLE korjaavatoimenpide ADD COLUMN laatija integer REFERENCES kayttaja (id);
ALTER TABLE korjaavatoimenpide ADD COLUMN vastuuhenkilo integer REFERENCES kayttaja (id);
ALTER TABLE korjaavatoimenpide ADD COLUMN toteuttaja VARCHAR(1024);
ALTER TABLE korjaavatoimenpide ADD COLUMN tila korjaavatoimenpide_tila;

ALTER TABLE korjaavatoimenpide SET tila = 'avoin'::korjaavatoimenpide_tila;