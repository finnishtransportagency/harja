-- Laskee ja palauttaa annetun tieosoitteen kokonaispituuden 
CREATE OR REPLACE FUNCTION laske_tr_osoitteen_pituus(_tnumero INTEGER, _rata INTEGER, _kaista INTEGER, _aosa INTEGER, _aet INTEGER, _losa INTEGER, _let INTEGER)
    RETURNS INTEGER AS
$$
DECLARE
    kokonaispituus INT := 0;
    alkuet INT := _aet;
    loppet INT := _let;
    -- Sallitaan syöttää osat molemmin päin ja convertataan ne oikein päin, alkuosa aina loppusaa pienempi
    alkuosa INT := LEAST(_aosa, _losa);
    loppuosa INT := GREATEST(_aosa, _losa);
BEGIN
    -- Jos alku sekä loppuosat ovat samat, convertataan etäisyydet myös oikeinpäin, alkuetäisyys aina pienempi
    -- Jos osat eivät ole samoja, ei voida tietää kummin päin etäisyydet tulisi olla, joten silloin annetaan niiden olla
    IF _aosa = _losa THEN 
      alkuet := LEAST(_aet, _let);
      loppet := GREATEST(_aet, _let);
    END IF; 

    -- Ja miinustetaan vaan let-aet jotta saadaan pituus 
    IF alkuosa = loppuosa THEN
      kokonaispituus := loppet - alkuet;
    ELSE
      -- Jos osia välissä, lasketaan osat yhteen, miinustetaan aet ensimmäisestä osasta, viimeisessä osassa lisätään vaan let
      -- Esim, jos meillä on yhteensä 4 osaa: kokonaispituus = [(osan1pituus - aet) + osan2pituus + osan3pituus + let] 
      SELECT 
        SUM(
          CASE 
            WHEN osa = alkuosa THEN pituus - alkuet
            WHEN osa > alkuosa AND osa < loppuosa THEN pituus
            WHEN osa = loppuosa THEN loppet
            ELSE 0
          END
      )
      INTO kokonaispituus
      FROM tr_osien_pituudet
      WHERE tie = _tnumero AND
            ( -- Palautetaan aina 0 jos osia ei anneta, jos yksi osa vain annetaan, oletetaan että molemmat osat samoja
              (_aosa::INTEGER IS NULL AND _losa::INTEGER IS NULL) 
              OR
              (osa BETWEEN alkuosa AND loppuosa)
            );
    END IF;

    RETURN kokonaispituus;
END;
$$ LANGUAGE plpgsql;
