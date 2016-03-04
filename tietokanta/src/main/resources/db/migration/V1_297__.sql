ALTER TABLE pohjavesialue DROP COLUMN ulkoinen_id;
ALTER TABLE pohjavesialue ADD COLUMN suolarajoitus BOOLEAN;

DROP MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain;
CREATE MATERIALIZED VIEW pohjavesialueet_hallintayksikoittain AS
  SELECT
    p.id,
    p.nimi,
    p.alue,
    p.tunnus,
    p.suolarajoitus,
    (SELECT id
     FROM organisaatio o
     WHERE tyyppi = 'hallintayksikko' :: organisaatiotyyppi AND ST_CONTAINS(o.alue, p.alue)) AS hallintayksikko
  FROM pohjavesialue p;

DROP MATERIALIZED VIEW pohjavesialueet_urakoittain;
CREATE MATERIALIZED VIEW pohjavesialueet_urakoittain AS
  WITH
      urakat_alueet AS (
        SELECT
          u.id,
          au.alue
        FROM urakka u
          JOIN hanke h ON u.hanke = h.id
          JOIN alueurakka au ON h.alueurakkanro = au.alueurakkanro
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
