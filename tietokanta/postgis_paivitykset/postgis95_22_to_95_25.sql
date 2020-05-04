DROP MATERIALIZED VIEW pohjavesialueet_urakoittain;

ALTER EXTENSION postgis UPDATE TO "2.5.4";
ALTER EXTENSION postgis_topology UPDATE TO "2.5.4";

CREATE MATERIALIZED VIEW pohjavesialueet_urakoittain AS
  WITH
      urakat_alueet AS (
        SELECT
          u.id,
          u.alue
        FROM urakka u
        WHERE u.tyyppi IN ('hoito' :: URAKKATYYPPI, 'teiden-hoito' :: URAKKATYYPPI)),
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

GRANT ALL PRIVILEGES ON pohjavesialueet_urakoittain TO harja;
GRANT ALL PRIVILEGES ON pohjavesialueet_urakoittain TO flyway;
