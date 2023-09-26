-- Laskee ja palauttaa annetun tieosoitteen kokonaispituuden 
CREATE OR REPLACE FUNCTION laske_tr_osoitteen_pituus(_tnumero INTEGER, _aosa INTEGER, _aet INTEGER, _losa INTEGER, _let INTEGER)
    RETURNS INTEGER AS
$$
DECLARE
    kokonaispituus INT := 0;
    alkuet INT := _aet;
    loppet INT := _let;
    alkuosa INT := _aosa;
    loppuosa INT := _losa;
BEGIN
    -- Tarkistetaan että annettu osoite on nousevassa järjestyksessä (alku ennen loppua), käännetään jos ei ole
    IF alkuosa > loppuosa THEN 
      alkuosa := _losa;
      loppuosa := _aosa;
      alkuet := _let;
      loppet := _aet;
    END IF;

    -- Jos alku sekä loppuosat ovat samat, miinustetaan vaan let-aet jotta saadaan pituus 
    IF alkuosa = loppuosa THEN 
      -- Katsotaan vielä että tie ja etäisyydet ovat valideja ennenkuin palautetaan kokonaispituus
      IF (SELECT EXISTS 
          ( -- Onko rivejä olemassa tällä tienumerolla ja pituuksilla
            SELECT 1 FROM tr_osien_pituudet
            WHERE tie = _tnumero
            AND osa = alkuosa
            AND pituus >= loppet 
            AND pituus >= alkuet
      )) THEN 
        -- Osoite on validi, palautetaan kokonaispituus
        kokonaispituus := loppet - alkuet;
      ELSE RETURN 0; 
      END IF;
    ELSE
      -- Jos osia välissä, lasketaan osat yhteen, miinustetaan aet ensimmäisestä osasta, viimeisessä osassa lisätään vaan let
      -- Esim, jos meillä on yhteensä 4 osaa: kokonaispituus = [(osan1pituus - aet) + osan2pituus + osan3pituus + let] 
      SELECT 
        SUM(
          CASE 
            WHEN osa = alkuosa THEN pituus - alkuet
            WHEN osa > alkuosa AND osa < loppuosa THEN pituus
            WHEN tie = _tnumero AND osa = loppuosa THEN loppet
            ELSE 0
          END
      )
      INTO kokonaispituus
      FROM tr_osien_pituudet
      WHERE tie = _tnumero 
      AND 
      ( -- Palautetaan aina 0 jos tieosaa ei anneta
        (_aosa::INTEGER IS NULL AND _losa::INTEGER IS NULL) 
        OR
        (osa BETWEEN alkuosa AND loppuosa)
      );
    END IF;
    -- Palautetaan 0 mikäli tietä ei löytynyt
    IF kokonaispituus IS NULL THEN kokonaispituus := 0; END IF;
    RETURN kokonaispituus;
END;
$$ LANGUAGE plpgsql;
