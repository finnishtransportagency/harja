-- Nimeä uudelleen "tekniset laitteet" > "tekniset-laitteet"

ALTER TYPE urakkatyyppi
RENAME TO urakkatyyppi_;

CREATE TYPE urakkatyyppi AS ENUM ('hoito', 'paallystys', 'paikkaus', 'tiemerkinta', 'valaistus', 'siltakorjaus', 'tekniset-laitteet');

-- 1. Urakkataulu
ALTER TABLE urakka
  RENAME COLUMN tyyppi TO tyyppi_;
ALTER TABLE urakka
  ADD COLUMN tyyppi urakkatyyppi;

UPDATE urakka
SET tyyppi = CASE
             WHEN tyyppi_ = 'tekniset laitteet'
               THEN 'tekniset-laitteet' :: urakkatyyppi
             ELSE tyyppi_ :: TEXT :: urakkatyyppi END;

-- 2. Ilmoitustaulu
ALTER TABLE ilmoitus
  RENAME COLUMN urakkatyyppi TO urakkatyyppi_;
ALTER TABLE ilmoitus
  ADD COLUMN urakkatyyppi urakkatyyppi;

UPDATE ilmoitus
SET urakkatyyppi = CASE
                   WHEN urakkatyyppi_ = 'tekniset laitteet'
                     THEN 'tekniset-laitteet' :: urakkatyyppi
                   ELSE urakkatyyppi_ :: TEXT :: urakkatyyppi END;

ALTER TABLE ilmoitus
  DROP COLUMN urakkatyyppi_;

-- 3. Materiaalikooditaulu
ALTER TABLE materiaalikoodi
  RENAME COLUMN urakkatyyppi TO urakkatyyppi_;
ALTER TABLE materiaalikoodi
  ADD COLUMN urakkatyyppi urakkatyyppi;

UPDATE materiaalikoodi
SET urakkatyyppi = CASE
                   WHEN urakkatyyppi_ = 'tekniset laitteet'
                     THEN 'tekniset-laitteet' :: urakkatyyppi
                   ELSE urakkatyyppi_ :: TEXT :: urakkatyyppi END;

ALTER TABLE materiaalikoodi
  DROP COLUMN urakkatyyppi_;

-- 4. Raporttitaulu
ALTER TABLE raportti
  RENAME COLUMN urakkatyyppi TO urakkatyyppi_;
ALTER TABLE raportti
  ADD COLUMN urakkatyyppi urakkatyyppi;

UPDATE raportti
SET urakkatyyppi = CASE
                   WHEN urakkatyyppi_ = 'tekniset laitteet'
                     THEN 'tekniset-laitteet' :: urakkatyyppi
                   ELSE urakkatyyppi_ :: TEXT :: urakkatyyppi END;

ALTER TABLE raportti
  DROP COLUMN urakkatyyppi_;

-- 5. Valitavoitetaulu
ALTER TABLE valitavoite
  RENAME COLUMN urakkatyyppi TO urakkatyyppi_;
ALTER TABLE valitavoite
  ADD COLUMN urakkatyyppi urakkatyyppi;

UPDATE valitavoite
SET urakkatyyppi = CASE
                   WHEN urakkatyyppi_ = 'tekniset laitteet'
                     THEN 'tekniset-laitteet' :: urakkatyyppi
                   ELSE urakkatyyppi_ :: TEXT :: urakkatyyppi END;

ALTER TABLE valitavoite
  DROP COLUMN urakkatyyppi_;

-- Materialisoidut näkymät

DROP MATERIALIZED VIEW urakoiden_alueet;
CREATE MATERIALIZED VIEW urakoiden_alueet AS
  SELECT
    u.id,
    u.tyyppi,

    CASE
    WHEN u.tyyppi = 'hoito'
      THEN au.alue
    ELSE u.alue
    END

  FROM urakka u
    LEFT JOIN alueurakka au ON u.urakkanro = au.alueurakkanro;

DROP MATERIALIZED VIEW pohjavesialueet_urakoittain;
CREATE MATERIALIZED VIEW pohjavesialueet_urakoittain AS
  WITH
      urakat_alueet AS (
        SELECT
          u.id,
          au.alue
        FROM urakka u
          JOIN alueurakka au ON u.urakkanro = au.alueurakkanro
        WHERE u.tyyppi = 'hoito' :: urakkatyyppi),
      pohjavesialue_alue AS (
        SELECT
          p.nimi,
          p.tunnus,
          ST_UNION(ST_SNAPTOGRID(p.alue, 0.0001)) AS alue,
          p.suolarajoitus
        FROM pohjavesialue p
        GROUP BY nimi, tunnus, suolarajoitus)
  SELECT
    pa.nimi,
    pa.tunnus,
    pa.alue,
    pa.suolarajoitus,
    ua.id AS urakka
  FROM pohjavesialue_alue pa
    CROSS JOIN urakat_alueet ua
  WHERE ST_CONTAINS(ua.alue, pa.alue);

DROP MATERIALIZED VIEW sillat_alueurakoittain;
CREATE MATERIALIZED VIEW sillat_alueurakoittain AS
  SELECT
    u.id AS urakka,
    s.id AS silta
  FROM urakka u
    JOIN alueurakka au ON u.urakkanro = au.alueurakkanro
    JOIN silta s ON ST_CONTAINS(au.alue, s.alue)
  WHERE u.tyyppi = 'hoito' :: urakkatyyppi;

-- Lopulta poista vanha tyyppi

ALTER TABLE urakka
  DROP COLUMN tyyppi_;

DROP TYPE urakkatyyppi_;