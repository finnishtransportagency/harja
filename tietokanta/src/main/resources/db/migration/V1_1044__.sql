-- Tie osien pituuden laskentafunktio jotta kokomaan raportin 
-- latauksessa ei kestä ikuisuutta päällystysurakoiden yhteenvedossa (vastaanottotarkastus)

DROP FUNCTION IF EXISTS laske_tie_osien_pituus(INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER);

CREATE OR REPLACE FUNCTION laske_tie_osien_pituus(_tnumero INTEGER, _rata INTEGER, _kaista INTEGER, _aosa INTEGER, _aet INTEGER, _losa INTEGER, _let INTEGER)
    RETURNS INTEGER AS
$$
DECLARE
    _kokonaispituus INT := 0;
BEGIN
    IF _aosa = _losa THEN
        -- Ei sallita invalid arvoja 
        IF _aet > _let THEN
          RAISE EXCEPTION 'Alkuetäisyys ei voi olla loppuetäisyyttä isompi, silloin kun aosa ja losa ovat samat.';
        END IF;

        -- Jos alku ja loppuosat ovat samoja, miinustetaan vaan let - aet jotta saadaa pituus 
        _kokonaispituus := _let - _aet;
    ELSE
      -- Ei myöskään sallita että aosa on isopi kun losa sillä tässä ei ole järkeä, kai
    	IF _aosa > _losa THEN
		    RAISE EXCEPTION 'Alkuosa ei voi olla loppuosaa isompi pituuden laskennassa.';
		  END IF;

      -- Jos osia välissä, lasketaan osat yhteen, miinustetaan aet ensimmäisestä osasta, viimeiseen osaan lisätään vaan _let
      SELECT 
        SUM(
          CASE 
            WHEN osa = _aosa THEN pituus - _aet
            WHEN osa > _aosa AND osa < _losa THEN pituus
            WHEN osa = _losa THEN _let
            ELSE 0
          END
      )
      INTO _kokonaispituus
      FROM tr_osien_pituudet
      WHERE tie = _tnumero AND
            ((_aosa::INTEGER IS NULL AND _losa::INTEGER IS NULL)
              OR
            (osa BETWEEN _aosa AND _losa));
    END IF;

    RETURN _kokonaispituus;
END;
$$ LANGUAGE plpgsql;
