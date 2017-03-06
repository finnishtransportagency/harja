-- Siirtää alueurakkanumeron urakka tasolta hanketasolle

UPDATE urakka u
SET urakkanro = (SELECT h.alueurakkanro
                 FROM hanke h
                 WHERE h.id = u.hanke)
WHERE urakkanro IS NULL;

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

ALTER TABLE hanke
  DROP alueurakkanro,
  DROP sampo_tyypit;
