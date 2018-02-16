-- Muutos: Tunnista rautatiesillat


-- A V A T T A V A T   S I L L A T
CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasilta_kohdetietoihin()
  RETURNS TRIGGER AS $$

-- Triggerifunktio päivittää integraation kautta saadut siltatiedot (kan_silta) käyttöliittymän käyttämään kan_kohteenosa-tauluun.

DECLARE   integraatiokayttaja INTEGER;
  DECLARE kohteen_osa         INTEGER;
  DECLARE oletuskaytotapa     TEXT;
  DECLARE siltatyyppi         KOHTEENOSA_TYYPPI;
BEGIN

  -- Selvitetään integraatiokäyttäjän id luoja-kenttään tallennusta varten.
  -- Integraatiokäyttäjä on luotu migraatiossa, jos sitä ei aiemmin ole ollut.
  integraatiokayttaja := (SELECT id
                          FROM kayttaja
                          WHERE kayttajanimi = 'Integraatio');

  -- Selvitetään löytyykö siltaan liittyvä kohteen osa kannasta. Lähtökohtana on kanavasilta-aineistossa saatu siltanumero (= kan_kohteenosa.lähdetunnus).
  kohteen_osa := (SELECT id
                  FROM kan_kohteenosa
                  WHERE lahdetunnus = new."siltanro");

  -- K O H T E E N   O S A
  -- Jos kohteen osaa ei ole sovelluksen taulussa, se luodaan.
  -- Oletuskäyttötapa on silloilla aina 'muu', siltatyyppi päätellään nimen perusteella

  oletuskaytotapa = 'muu';

  IF (position('rata' IN new."nimi") > 0)
  THEN
    siltatyyppi = 'rautatiesilta';
  ELSE
    siltatyyppi = 'silta';
  END IF;

  IF (kohteen_osa ISNULL)
  THEN
    INSERT INTO kan_kohteenosa (tyyppi, nimi, oletuspalvelumuoto, luoja, luotu, sijainti, lahdetunnus, poistettu)
    VALUES
      (siltatyyppi :: KOHTEENOSA_TYYPPI, new."nimi", oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO,
       integraatiokayttaja,
       current_timestamp, new."geometria", new."siltanro", new."poistettu");
  ELSE
    UPDATE kan_kohteenosa
    SET
      nimi               = new."nimi",
      tyyppi             = siltatyyppi,
      oletuspalvelumuoto = oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO,
      sijainti           = new."geometria" :: GEOMETRY,
      muokattu           = current_timestamp,
      muokkaaja          = integraatiokayttaja,
      poistettu          = new."poistettu"
    WHERE id = kohteen_osa;
  END IF;

  RETURN new;

END;
$$ LANGUAGE plpgsql;


UPDATE kan_silta
SET muokattu = current_timestamp, muokkaaja = 'Migraatio';

