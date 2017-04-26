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
  id               SERIAL PRIMARY KEY,
  reimari_id       INTEGER NOT NULL,
  urakoitsija      reimari_urakoitsija CHECK ((urakoitsija).id IS NOT NULL AND
                                            sisaltaa_tekstia((urakoitsija).nimi)),
  sopimus          reimari_sopimus     CHECK ((sopimus).nro IS NOT NULL AND
                                              sisaltaa_tekstia((sopimus).tyyppi) AND
                                              sisaltaa_tekstia((sopimus).nimi)),
  turvalaite       reimari_turvalaite  CHECK (sisaltaa_tekstia((turvalaite).nro) AND
                                              -- nimi saa olla tyhja
                                              (turvalaite).ryhma IS NOT NULL),
  alus             reimari_alus        CHECK (sisaltaa_tekstia((alus).tunnus)
                                              -- nimi saa olla tyhja
                                            ),
  vayla            reimari_vayla       CHECK (sisaltaa_tekstia((vayla).nro)),
  tyolaji          TEXT                CHECK (sisaltaa_tekstia(tyolaji)),
  tyoluokka        TEXT                CHECK (sisaltaa_tekstia(tyoluokka)),
  tyyppi           TEXT                CHECK (sisaltaa_tekstia(tyyppi)),
  lisatieto        TEXT                NOT NULL,
  lisatyo          BOOLEAN             NOT NULL,
  tila             TEXT                CHECK (sisaltaa_tekstia(tila)),
  suoritettu       TIMESTAMP           NOT NULL,
  reimari_luotu    TIMESTAMP           NOT NULL,
  reimari_muokattu TIMESTAMP,
  luotu            TIMESTAMP           NOT NULL DEFAULT NOW(),
  luoja            INTEGER REFERENCES kayttaja(id),
  muokattu         TIMESTAMP,
  muokkaaja        INTEGER REFERENCES kayttaja(id),
  asiakas          TEXT,
  vastuuhenkilo    TEXT,
  poistettu        BOOLEAN             NOT NULL DEFAULT FALSE,
  poistaja         INTEGER REFERENCES kayttaja(id)
);
