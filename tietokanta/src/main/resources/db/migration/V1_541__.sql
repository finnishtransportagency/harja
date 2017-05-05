CREATE TYPE reimari_urakoitsija AS (id INTEGER, nimi TEXT);
CREATE TYPE reimari_sopimus AS (nro INTEGER, tyyppi TEXT, nimi TEXT);
CREATE TYPE reimari_turvalaite AS (nro TEXT, nimi TEXT, ryhma INTEGER);
CREATE TYPE reimari_alus AS (tunnus TEXT, nimi TEXT);
CREATE TYPE reimari_vayla AS (nro TEXT, nimi TEXT, ryhma INTEGER);

CREATE OR REPLACE FUNCTION sisaltaa_tekstia (s TEXT) -- ei sallita: null, '', '  '
  RETURNS BOOLEAN AS $$
BEGIN
  RETURN COALESCE(TRIM(s), '') != '';
END;
$$ LANGUAGE plpgsql;


CREATE TABLE reimari_toimenpide (
  id                 SERIAL PRIMARY KEY,
  "reimari-id"       INTEGER NOT NULL,
  urakoitsija        reimari_urakoitsija CHECK ((urakoitsija).id IS NOT NULL AND
                                                sisaltaa_tekstia((urakoitsija).nimi)),
  sopimus            reimari_sopimus     CHECK ((sopimus).nro IS NOT NULL AND
                                                sisaltaa_tekstia((sopimus).tyyppi) AND
                                                sisaltaa_tekstia((sopimus).nimi)),
  turvalaite         reimari_turvalaite  CHECK (sisaltaa_tekstia((turvalaite).nro) AND
                                                -- nimi saa olla tyhja
                                                (turvalaite).ryhma IS NOT NULL),
  alus               reimari_alus        CHECK (sisaltaa_tekstia((alus).tunnus)
    -- nimi saa olla tyhja
  ),
  vayla              reimari_vayla       CHECK (sisaltaa_tekstia((vayla).nro)),
  tyolaji            TEXT                CHECK (sisaltaa_tekstia(tyolaji)),
  tyoluokka          TEXT                CHECK (sisaltaa_tekstia(tyoluokka)),
  tyyppi             TEXT                CHECK (sisaltaa_tekstia(tyyppi)),
  lisatieto          TEXT                NOT NULL,
  lisatyo            BOOLEAN             NOT NULL,
  tila               TEXT                CHECK (sisaltaa_tekstia(tila)),
  suoritettu         TIMESTAMP           NOT NULL,
  "reimari-luotu"    TIMESTAMP           NOT NULL,
  "reimari-muokattu" TIMESTAMP,
  luotu              TIMESTAMP           NOT NULL DEFAULT NOW(),
  luoja              INTEGER REFERENCES kayttaja(id),
  muokattu           TIMESTAMP,
  muokkaaja          INTEGER REFERENCES kayttaja(id),
  asiakas            TEXT,
  vastuuhenkilo      TEXT,
  poistettu          BOOLEAN             NOT NULL DEFAULT FALSE,
  poistaja           INTEGER REFERENCES kayttaja(id)
);

-- VÄYLÄT

CREATE TYPE vv_vaylatyyppi AS ENUM ('kauppamerenkulku', 'muu');

CREATE TABLE vv_vayla (
  id SERIAL PRIMARY KEY,
  nimi VARCHAR NOT NULL,
  "vatu-id" INTEGER NOT NULL,
  sijainti GEOMETRY,
  tyyppi vv_vaylatyyppi NOT NULL
);

-- TURVALAITTEET

CREATE TYPE turvalaitteen_tyyppi AS ENUM ('viitta', 'kiintea', 'poiju');

ALTER TABLE turvalaite RENAME TO vv_turvalaite;

ALTER TABLE vv_turvalaite
  ADD COLUMN tyyppi turvalaitteen_tyyppi,
  ADD COLUMN vayla INTEGER REFERENCES vv_vayla(id);

-- VIKAILMOITUKSET

CREATE TABLE vv_vikailmoitus (
  id SERIAL PRIMARY KEY,
  "reimari-id" INTEGER,
  kuvaus VARCHAR,
  pvm DATE,
  "turvalaite-id" INTEGER REFERENCES vv_turvalaite(id) NOT NULL,
  "toteuma-id" INTEGER REFERENCES toteuma(id)
);

-- HINNOITTELU

CREATE TYPE vv_hintatyyppi AS ENUM ('komponentti', 'materiaali', 'matka', 'tyo', 'muu');

CREATE TABLE vv_hinta (
  id SERIAL PRIMARY KEY,
  "toteuma-id" INTEGER REFERENCES toteuma(id) NOT NULL,
  hinta NUMERIC NOT NULL,
  kuvaus VARCHAR,
  yleiskustannuslisa BOOLEAN DEFAULT FALSE NOT NULL,
  tyyppi vv_hintatyyppi
);