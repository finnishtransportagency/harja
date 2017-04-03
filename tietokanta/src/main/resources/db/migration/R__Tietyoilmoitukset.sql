-- Luodaan view, joka automaattisesti laskee TR-osoitteen pituuden mukaan
-- Tätä käytetään hauissa itse taulun sijasta ja se palauttaa automaattisesti
-- pituuden tierekisteriosoitteen perusteella
CREATE OR REPLACE VIEW tietyoilmoitus_pituus AS
  SELECT tti.*, ST_Length(tr_osoitteelle_viiva3(
                           (tti.osoite).tie,
			   (tti.osoite).aosa, (tti.osoite).aet,
			   (tti.osoite).losa, (tti.osoite).let)) AS pituus
    FROM tietyoilmoitus tti;
