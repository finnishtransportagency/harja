-- Poista reimari_toimenpide taulusta toteuma-linkki

CREATE TYPE vv_toimenpide_hintatyyppi AS ENUM ('kokonaishintainen', 'yksikkohintainen');

ALTER TABLE reimari_toimenpide
    ADD COLUMN hintatyyppi vv_toimenpide_hintatyyppi;

UPDATE reimari_toimenpide
  SET hintatyyppi = CASE WHEN (SELECT tyyppi FROM toteuma t JOIN reimari_toimenpide r ON t.id = r."toteuma-id") = 'vv-kokonaishintainen'
    THEN 'kokonaishintainen' ELSE 'yksikkohintainen';

ALTER TABLE reimari_toimenpide
    DROP COLUMN "toteuma-id";

-- Päivitä toteumatyyppiä, poista vesiväylä-enumit

CREATE TYPE toteumatyyppi_new AS ENUM ('kokonaishintainen', 'yksikkohintainen', 'akillinen-hoitotyo', 'lisatyo', 'muutostyo', 'vahinkojen-korjaukset', 'materiaali');

DELETE FROM toteuma WHERE tyyppi = 'vv-kokonaishintainen' OR tyyppi = 'vv-yksikkohintainen';

ALTER TABLE toteuma
  ALTER COLUMN tyyppi TYPE toteumatyyppi_new
  USING (tyyppi::text::toteumatyyppi_new);

DROP TYPE toteumatyyppi;

ALTER TYPE toteumatyyppi_new RENAME TO toteumatyyppi;

-- Lisää hinnoittelutaulut

CREATE TABLE vv_hinnoittelu
(
  id         SERIAL PRIMARY KEY,
  nimi       VARCHAR CONSTRAINT nipulla_oltava_nimi CHECK(hintanippu IS FALSE OR nimi IS NOT NULL),
  hintanippu BOOLEAN                          NOT NULL DEFAULT FALSE,

  muokkaaja  INTEGER REFERENCES kayttaja (id),
  muokattu   TIMESTAMP,
  luoja      INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu      TIMESTAMP                        NOT NULL,
  poistettu  BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja   INTEGER REFERENCES kayttaja (id)
);

ALTER TABLE reimari_toimenpide
  ADD COLUMN "hinnoittelu-id" INTEGER REFERENCES vv_hinnoittelu (id);

CREATE TABLE vv_hinnoittelu_toimenpide
(
  "toimenpide-id"  INTEGER REFERENCES reimari_toimenpide (id),
  "hinnoittelu-id" INTEGER REFERENCES vv_hinnoittelu (id),
  UNIQUE("toimenpide-id", "hinnoittelu-id");
  -- TODO unique
);

CREATE TABLE vv_hinta
(
  id                 SERIAL PRIMARY KEY,
  "hinnoittelu-id"   INTEGER REFERENCES vv_hinnoittelu (id),
  otsikko            VARCHAR                          NOT NULL,
  maara              NUMERIC                          NOT NULL,
  yleiskustannuslisa NUMERIC                          NOT NULL DEFAULT 0,

  muokkaaja          INTEGER REFERENCES kayttaja (id),
  muokattu           TIMESTAMP,
  luoja              INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu              TIMESTAMP                        NOT NULL,
  poistettu          BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja           INTEGER REFERENCES kayttaja (id)
);