-- VÄYLÄT

CREATE TYPE VV_VAYLATYYPPI AS ENUM ('kauppamerenkulku', 'muu');

CREATE TABLE vv_vayla (
  id        SERIAL PRIMARY KEY,
  nimi      VARCHAR        NOT NULL,
  "vatu-id" INTEGER        NOT NULL,
  sijainti  GEOMETRY,
  tyyppi    VV_VAYLATYYPPI NOT NULL
);

-- TURVALAITTEET

CREATE TYPE TURVALAITTEEN_TYYPPI AS ENUM ('viitta', 'kiintea', 'poiju');

ALTER TABLE turvalaite
  RENAME TO vv_turvalaite;

ALTER TABLE vv_turvalaite
  ADD COLUMN tyyppi TURVALAITTEEN_TYYPPI,
  ADD COLUMN nimi VARCHAR,
  ADD COLUMN vayla INTEGER REFERENCES vv_vayla (id),
  ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT uniikki_tunniste UNIQUE (tunniste);

-- VIKAILMOITUKSET

CREATE TABLE vv_vikailmoitus (
  id              SERIAL PRIMARY KEY,
  "reimari-id"    INTEGER,
  kuvaus          VARCHAR,
  pvm             DATE,
  "turvalaite-id" INTEGER REFERENCES vv_turvalaite (id) NOT NULL,
  "toteuma-id"    INTEGER REFERENCES toteuma (id)
);

CREATE TYPE REIMARI_URAKOITSIJA AS (id INTEGER, nimi TEXT);
CREATE TYPE REIMARI_SOPIMUS AS (nro INTEGER, tyyppi TEXT, nimi TEXT);
CREATE TYPE REIMARI_TURVALAITE AS (nro TEXT, nimi TEXT, ryhma INTEGER);
CREATE TYPE REIMARI_ALUS AS (tunnus TEXT, nimi TEXT);
CREATE TYPE REIMARI_VAYLA AS (nro TEXT, nimi TEXT, ryhma INTEGER);

CREATE OR REPLACE FUNCTION sisaltaa_tekstia(s TEXT) -- ei sallita: null, '', '  '
  RETURNS BOOLEAN AS $$
BEGIN
  RETURN COALESCE(TRIM(s), '') != '';
END;
$$ LANGUAGE plpgsql;


CREATE TABLE reimari_toimenpide (
  id                      SERIAL PRIMARY KEY,
  "toteuma-id"            INTEGER REFERENCES toteuma (id),
  "reimari-id"            INTEGER   NOT NULL,
  "reimari-urakoitsija"   REIMARI_URAKOITSIJA CHECK (("reimari-urakoitsija").id IS NOT NULL AND
                                                     sisaltaa_tekstia(
                                                         ("reimari-urakoitsija").nimi)),
  "urakoitsija-id"        INTEGER REFERENCES organisaatio (id),
  "reimari-sopimus"       REIMARI_SOPIMUS CHECK (("reimari-sopimus").nro IS NOT NULL AND
                                                 sisaltaa_tekstia(("reimari-sopimus").tyyppi) AND
                                                 sisaltaa_tekstia(("reimari-sopimus").nimi)),
  "sopimus-id"            INTEGER REFERENCES sopimus (id),
  "reimari-turvalaite"    REIMARI_TURVALAITE CHECK (sisaltaa_tekstia(("reimari-turvalaite").nro) AND
                                                    -- nimi saa olla tyhja
                                                    ("reimari-turvalaite").ryhma IS NOT NULL),
  "turvalaite-id"         INTEGER REFERENCES vv_turvalaite (id),
  "reimari-alus"          REIMARI_ALUS CHECK (sisaltaa_tekstia(("reimari-alus").tunnus)),
  "reimari-vayla"         REIMARI_VAYLA CHECK (sisaltaa_tekstia(("reimari-vayla").nro)),
  "vayla-id"              INTEGER REFERENCES vv_vayla (id),
  "reimari-tyolaji"       TEXT CHECK (sisaltaa_tekstia("reimari-tyolaji")),
  "reimari-tyoluokka"     TEXT CHECK (sisaltaa_tekstia("reimari-tyoluokka")),
  "reimari-tyyppi"        TEXT CHECK (sisaltaa_tekstia("reimari-tyyppi")),
  -- TODO: Toimenpiteelle komponenttitiedot
  lisatieto               TEXT      NOT NULL,
  lisatyo                 BOOLEAN   NOT NULL,
  "reimari-tila"          TEXT CHECK (sisaltaa_tekstia("reimari-tila")),
  suoritettu              TIMESTAMP NOT NULL,
  "reimari-luotu"         TIMESTAMP NOT NULL,
  "reimari-muokattu"      TIMESTAMP,
  luotu                   TIMESTAMP NOT NULL DEFAULT NOW(),
  luoja                   INTEGER REFERENCES kayttaja (id),
  muokattu                TIMESTAMP,
  muokkaaja               INTEGER REFERENCES kayttaja (id),
  "reimari-asiakas"       TEXT,
  "asiakas-id"            INTEGER REFERENCES organisaatio (id),
  "reimari-vastuuhenkilo" TEXT,
  "vastuuhenkilo-id"      INTEGER REFERENCES kayttaja (id),
  poistettu               BOOLEAN   NOT NULL DEFAULT FALSE,
  poistaja                INTEGER REFERENCES kayttaja (id)
);