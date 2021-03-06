-- Yhtenäistä Harjan turvallisuuspoikkeama TURI:n kanssa

-- Turvallisuuspoikkeaman päivitys

ALTER TABLE turvallisuuspoikkeama DROP COLUMN paattynyt;
ALTER TABLE turvallisuuspoikkeama DROP COLUMN tyotehtava;
ALTER TABLE turvallisuuspoikkeama DROP COLUMN laatija_etunimi;
ALTER TABLE turvallisuuspoikkeama DROP COLUMN laatija_sukunimi;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN laatija integer REFERENCES kayttaja (id);

CREATE TYPE turvallisuuspoikkeama_tila AS ENUM ('avoin','kasitelty','taydennetty', 'suljettu');

ALTER TABLE turvallisuuspoikkeama ADD COLUMN tapahtuman_otsikko VARCHAR(1024);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN paikan_kuvaus VARCHAR(2048);
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vaarallisten_aineiden_kuljetus BOOLEAN;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN vaarallisten_aineiden_vuoto BOOLEAN;
ALTER TABLE turvallisuuspoikkeama ADD COLUMN tila turvallisuuspoikkeama_tila;

ALTER TABLE turvallisuuspoikkeama ALTER COLUMN kuvaus TYPE VARCHAR(4000);
ALTER TABLE turvallisuuspoikkeama ALTER COLUMN aiheutuneet_seuraukset TYPE VARCHAR(4000);

-- Migratoi wanha data
UPDATE turvallisuuspoikkeama SET tila = CASE WHEN (kasitelty IS NOT NULL)
    THEN 'suljettu'::turvallisuuspoikkeama_tila
    ELSE 'avoin'::turvallisuuspoikkeama_tila
    END;
UPDATE turvallisuuspoikkeama SET vaarallisten_aineiden_kuljetus = FALSE;
UPDATE turvallisuuspoikkeama SET vaarallisten_aineiden_vuoto = FALSE;

-- Korjaavan toimenpiteen päivitys

ALTER TABLE korjaavatoimenpide DROP COLUMN vastaavahenkilo;

CREATE TYPE korjaavatoimenpide_tila AS ENUM ('avoin','siirretty','toteutettu');

ALTER TABLE korjaavatoimenpide ADD COLUMN otsikko VARCHAR(2048);
ALTER TABLE korjaavatoimenpide ADD COLUMN laatija integer REFERENCES kayttaja (id);
ALTER TABLE korjaavatoimenpide ADD COLUMN vastuuhenkilo integer REFERENCES kayttaja (id);
ALTER TABLE korjaavatoimenpide ADD COLUMN toteuttaja VARCHAR(1024);
ALTER TABLE korjaavatoimenpide ADD COLUMN tila korjaavatoimenpide_tila;

-- Migratoi wanha data
UPDATE korjaavatoimenpide SET tila = CASE WHEN (suoritettu IS NOT NULL)
    THEN 'toteutettu'::korjaavatoimenpide_tila
    ELSE 'avoin'::korjaavatoimenpide_tila
    END;