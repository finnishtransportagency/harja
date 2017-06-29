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

-- Kopioi geometriat 28.6.2017 ennen päättyneiltä hoidon alueurakoille niitä vastaavilta uusilta urakoilta
UPDATE urakka
SET alue = arvot.uusi_alue
FROM (SELECT
        vanha.urakkanro AS vanha_urakkanro,
        uusi.alue       AS uusi_alue
      FROM urakka vanha
        JOIN (SELECT
                urakkanro,
                nimi,
                alue
              FROM urakka
              WHERE tyyppi = 'hoito' AND loppupvm > '2017-06-28') uusi
          ON upper(substring(vanha.nimi FROM 1 FOR 5)) = upper(substring(uusi.nimi FROM 1 FOR 5))
      WHERE vanha.tyyppi = 'hoito' AND vanha.loppupvm < '2017-06-28') arvot
WHERE urakka.urakkanro = arvot.vanha_urakkanro;

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

CREATE FUNCTION paivita_urakan_geometria()
  RETURNS TRIGGER AS $$
DECLARE
  uusi_alue GEOMETRY;

BEGIN
  uusi_alue = NULL;

  IF NEW.tyyppi = 'valaistus' :: URAKKATYYPPI
  THEN
    uusi_alue :=  (SELECT ST_Union(uusi_alue)
                   FROM valaistusurakka
                   WHERE valaistusurakkanro = NEW.urakkanro);

    -- name: paivita-tekniset-laitteet-urakan-geometria-kannasta!
  ELSEIF NEW.tyyppi = 'tekniset-laitteet' :: URAKKATYYPPI
    THEN
      uusi_alue :=  (SELECT uusi_alue
                     FROM tekniset_laitteet_urakka
                     WHERE urakkanro = NEW.urakkanro);

      -- name: paivita-siltakorjausurakan-geometria-kannasta!
  ELSEIF NEW.tyyppi = 'siltakorjaus' :: URAKKATYYPPI
    THEN
      uusi_alue :=  (SELECT uusi_alue
                     FROM siltapalvelusopimus
                     WHERE urakkanro = NEW.urakkanro);

      -- name: paivita-paallystyksen-palvelusopimuksen-geometria-kannasta!
  ELSEIF NEW.tyyppi = 'siltakorjaus' :: URAKKATYYPPI
    THEN
      uusi_alue :=  (SELECT uusi_alue
                     FROM siltapalvelusopimus
                     WHERE urakkanro = NEW.urakkanro);

  END IF;

  UPDATE urakka
  SET alue = uusi_alue
  WHERE alue IS NULL AND
        urakkanro = NEW.urakkanro;

  RETURN NEW;

END;
$$ LANGUAGE plpgsql;


-- Luo triggeri, joka päivittää urakoiden geometriat automaattisesti luonnin jälkeen
CREATE TRIGGER tg_paivita_urakan_geometriat_luonnin_jalkeen
AFTER INSERT
  ON urakka
FOR EACH ROW
WHEN (NEW.tyyppi IN ('valaistus' :: URAKKATYYPPI,
                     'tekniset-laitteet' :: URAKKATYYPPI,
                     'hoito' :: URAKKATYYPPI,
                     'siltakorjaus' :: URAKKATYYPPI,
                     'paallystys' :: URAKKATYYPPI))
EXECUTE PROCEDURE paivita_urakan_geometria();








