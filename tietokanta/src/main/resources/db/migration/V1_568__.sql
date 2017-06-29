-- Kopioi nykyisille käynnissä oleville urakoille geometriat suoraan tauluun
UPDATE urakka
SET alue = (SELECT au.alue
            FROM alueurakka au
            WHERE au.alueurakkanro = urakkanro)
WHERE alue IS NULL AND tyyppi = 'hoito' :: URAKKATYYPPI;
;

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
CREATE FUNCTION paivita_alueurakan_geometria()
  RETURNS TRIGGER AS $$
BEGIN
  uusi_alue = NULL;

  UPDATE urakka
  SET alue = (SELECT alue
              FROM alueurakka
              WHERE alueurakkanro = NEW.urakkanro)
  WHERE alue IS NULL AND
        urakkanro = NEW.urakkanro AND
        tyyppi = 'hoito' :: URAKKATYYPPI;
  RETURN NEW;

END;
$$ LANGUAGE plpgsql;

-- Luo triggeri, joka päivittää urakoiden geometriat automaattisesti luonnin jälkeen
CREATE TRIGGER tg_paivita_alueurakan_geometriat_luonnin_jalkeen
AFTER INSERT
  ON urakka
FOR EACH ROW
WHEN (NEW.tyyppi = 'hoito' :: URAKKATYYPPI)
EXECUTE PROCEDURE paivita_alueurakan_geometria();
