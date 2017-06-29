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

-- Luo triggeri, joka päivittää urakan geometrian luomisen jälkeen
CREATE FUNCTION paivita_urakan_geometria()
  RETURNS TRIGGER AS $$
DECLARE
  uusi_alue GEOMETRY;

BEGIN
  uusi_alue = NULL;

  IF NEW.tyyppi = 'hoito' :: URAKKATYYPPI
  THEN
    uusi_alue :=  (SELECT alue
                   FROM alueurakka
                   WHERE alueurakkanro = NEW.urakkanro);

  ELSEIF NEW.tyyppi = 'valaistus' :: URAKKATYYPPI
    THEN
      uusi_alue :=  (SELECT ST_Union(alue)
                     FROM valaistusurakka
                     WHERE valaistusurakkanro = NEW.urakkanro);

  ELSEIF NEW.tyyppi = 'tekniset-laitteet' :: URAKKATYYPPI
    THEN
      uusi_alue :=  (SELECT alue
                     FROM tekniset_laitteet_urakka
                     WHERE urakkanro = NEW.urakkanro);

  ELSEIF NEW.tyyppi = 'siltakorjaus' :: URAKKATYYPPI
    THEN
      uusi_alue :=  (SELECT alue
                     FROM siltapalvelusopimus
                     WHERE urakkanro = NEW.urakkanro);

  ELSEIF NEW.tyyppi = 'siltakorjaus' :: URAKKATYYPPI
    THEN
      uusi_alue :=  (SELECT alue
                     FROM paallystyspalvelusopimus
                     WHERE paallystyspalvelusopimusnro = NEW.urakkanro);

  END IF;

  UPDATE urakka
  SET alue = uusi_alue, nimi = 'HEIJAKKAA-' || new.nimi
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
