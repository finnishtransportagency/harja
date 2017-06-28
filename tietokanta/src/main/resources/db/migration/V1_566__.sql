-- Kopioi nykyisille käynnissä oleville urakoille geometriat suoraan tauluun

-- Hoidon alueurakat
UPDATE urakka
SET alue = (SELECT au.alue
              FROM alueurakka au
              WHERE au.alueurakkanro = urakkanro)
WHERE alue IS NULL AND tyyppi = 'hoito' :: URAKKATYYPPI;

-- Valaistusurakat
UPDATE urakka
SET alue = (SELECT ST_Union(vu.alue)
              FROM valaistusurakka vu
              WHERE vu.valaistusurakkanro = urakkanro)
WHERE alue IS NULL AND
      tyyppi = 'valaistus' :: URAKKATYYPPI;

-- Tekniset laitteet -urakat
UPDATE urakka
SET alue = (SELECT tlu.alue
              FROM tekniset_laitteet_urakka tlu
              WHERE tlu.urakkanro = urakkanro)
WHERE alue IS NULL AND
      tyyppi = 'tekniset-laitteet' :: URAKKATYYPPI;

-- Siltakorjausurakat
UPDATE urakka
SET alue = (SELECT sps.alue
              FROM siltapalvelusopimus sps
              WHERE sps.urakkanro = urakkanro)
WHERE alue IS NULL AND
      tyyppi = 'siltakorjaus' :: URAKKATYYPPI;

-- Päällystyksen palvelusopimukset
UPDATE urakka
SET alue = (SELECT pps.alue
              FROM paallystyspalvelusopimus pps
              WHERE pps.paallystyspalvelusopimusnro = urakkanro)
WHERE alue IS NULL AND
      sopimustyyppi = 'palvelusopimus' :: SOPIMUSTYYPPI AND
      tyyppi = 'paallystys' :: URAKKATYYPPI;

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


