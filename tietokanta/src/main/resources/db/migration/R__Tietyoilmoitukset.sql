-- Luodaan view, joka automaattisesti laskee TR-osoitteen pituuden mukaan
-- Tätä käytetään hauissa itse taulun sijasta ja se palauttaa automaattisesti
-- pituuden tierekisteriosoitteen perusteella
DROP VIEW IF EXISTS tietyoilmoitus_pituus;


CREATE VIEW tietyoilmoitus_pituus AS
  SELECT tti.*, CASE
                  WHEN (tti.osoite).losa IS NOT NULL THEN
                   ST_Length(tieosoitteelle_viiva(
                                (tti.osoite).tie, (tti.osoite).aosa,
                                  (tti.osoite).aet, (tti.osoite).losa,
                                  (tti.osoite).let))
                  ELSE
                     0
                  END
                AS pituus FROM tietyoilmoitus tti;

-- 27.7.2017 muutettu, jotta tti view päivittyy
-- 31.5.2018 muutettu, jotta tti view päivittyy
