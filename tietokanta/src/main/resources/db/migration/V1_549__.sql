-- Poista reimari_toimenpide taulusta toteuma-linkki

CREATE TYPE VV_TOIMENPIDE_HINTATYYPPI AS ENUM ('kokonaishintainen', 'yksikkohintainen');

ALTER TABLE reimari_toimenpide
  ADD COLUMN hintatyyppi VV_TOIMENPIDE_HINTATYYPPI,
  ADD COLUMN "urakka-id" INTEGER REFERENCES urakka (id) NOT NULL DEFAULT 0;

ALTER TABLE reimari_toimenpide
  ALTER COLUMN "urakka-id" DROP DEFAULT;

UPDATE reimari_toimenpide
SET hintatyyppi = (SELECT CASE WHEN tyyppi = 'vv-kokonaishintainen'
  THEN 'kokonaishintainen'
                          ELSE 'yksikkohintainen' END
                   FROM toteuma t
                     JOIN reimari_toimenpide r
                       ON t.id = r."toteuma-id") :: VV_TOIMENPIDE_HINTATYYPPI,
  "urakka-id"   = (SELECT t.urakka
                   FROM toteuma t
                     JOIN reimari_toimenpide rt ON t.id = reimari_toimenpide."toteuma-id");

ALTER TABLE reimari_toimenpide
  DROP COLUMN "toteuma-id";

-- Poista toteumataulusta vesiväylätoteumat

DELETE FROM toteuma
WHERE tyyppi = 'vv-kokonaishintainen' OR tyyppi = 'vv-yksikkohintainen';

-- Lisää hinnoittelutaulut

CREATE TABLE vv_hinnoittelu
(
  id         SERIAL PRIMARY KEY,
  nimi       VARCHAR CONSTRAINT nipulla_oltava_nimi CHECK (hintaryhma IS FALSE OR nimi IS NOT NULL),
  hintaryhma BOOLEAN                          NOT NULL DEFAULT FALSE,

  muokkaaja  INTEGER REFERENCES kayttaja (id),
  muokattu   TIMESTAMP,
  luoja      INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu      TIMESTAMP                        NOT NULL DEFAULT NOW(),
  poistettu  BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja   INTEGER REFERENCES kayttaja (id)
);

ALTER TABLE reimari_toimenpide
  ADD COLUMN "hinnoittelu-id" INTEGER REFERENCES vv_hinnoittelu (id);

CREATE TABLE vv_hinnoittelu_toimenpide
(
  "toimenpide-id"  INTEGER REFERENCES reimari_toimenpide (id),
  "hinnoittelu-id" INTEGER REFERENCES vv_hinnoittelu (id),
  UNIQUE ("toimenpide-id", "hinnoittelu-id")
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
  luotu              TIMESTAMP                        NOT NULL DEFAULT NOW(),
  poistettu          BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja           INTEGER REFERENCES kayttaja (id)
);