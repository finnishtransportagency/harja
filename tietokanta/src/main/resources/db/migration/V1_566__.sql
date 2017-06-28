-- Kopioi nykyisille käynnissä oleville urakoille geometriat suoraan tauluun

-- Hoidon alueurakat
UPDATE urakka u
SET u.alue = (SELECT au.alue
              FROM alueurakka au
              WHERE au.alueurakkanro = u.urakkanro)
WHERE u.alue IS NULL AND u.tyyppi = 'hoito' :: URAKKATYYPPI;

-- Valaistusurakat
UPDATE urakka u
SET u.alue = (SELECT ST_Union(vu.alue)
              FROM valaistusurakka vu
              WHERE vu.valaistusurakkanro = u.urakkanro)
WHERE u.alue IS NULL AND
      tyyppi = 'valaistus' :: URAKKATYYPPI;

-- Tekniset laitteet -urakat
UPDATE urakka u
SET u.alue = (SELECT tlu.alue
              FROM tekniset_laitteet_urakka tlu
              WHERE tlu.urakkanro = u.urakkanro)
WHERE u.alue IS NULL AND
      u.tyyppi = 'tekniset-laitteet' :: URAKKATYYPPI;

-- Siltakorjausurakat
UPDATE urakka u
SET u.alue = (SELECT sps.alue
              FROM siltapalvelusopimus sps
              WHERE sps.urakkanro = u.urakkanro)
WHERE u.alue IS NULL AND
      u.tyyppi = 'siltakorjaus' :: URAKKATYYPPI;

-- Päällystyksen palvelusopimukset
UPDATE urakka u
SET u.alue = (SELECT pps.alue
              FROM paallystyspalvelusopimus pps
              WHERE pps.urakkanro = u.urakkanro)
WHERE u.alue IS NULL AND
      u.sopimustyyppi = 'palvelusopimus' :: SOPIMUSTYYPPI AND
      u.tyyppi = 'paallystys' :: URAKKATYYPPI;

-- Päivitä pohjavesialueiden ja siltojen materialisoidut näkymät käyttämään urakkataulun geometriaa
DROP MATERIALIZED VIEW pohjavesialueet_urakoittain;
CREATE MATERIALIZED VIEW pohjavesialueet_urakoittain AS
  WITH
      urakat_alueet AS (
        SELECT
          u.id,
          u.alue
        FROM urakka u
        WHERE u.tyyppi = 'hoito' :: URAKKATYYPPI),
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
    JOIN silta s ON ST_CONTAINS(u.alue, s.alue)
  WHERE u.tyyppi = 'hoito' :: URAKKATYYPPI;


