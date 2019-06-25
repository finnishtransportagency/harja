-- Silloilla on oletuspalvelumuoto. Älä päivitä UPDATE-lauseella päälle.

-- A V A T T A V A T   S I L L A T
CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasilta_kohdetietoihin()
  RETURNS TRIGGER AS $$

-- Triggerifunktio päivittää integraation kautta saadut siltatiedot (kan_silta) käyttöliittymän käyttämään kan_kohteenosa-tauluun.

DECLARE   integraatiokayttaja INTEGER;
  DECLARE kohteen_osa         INTEGER;
  DECLARE oletuskaytotapa     TEXT;
BEGIN

  -- Selvitetään integraatiokäyttäjän id luoja-kenttään tallennusta varten.
  -- Integraatiokäyttäjä on luotu migraatiossa, jos sitä ei aiemmin ole ollut.
  integraatiokayttaja := (SELECT id
                          FROM kayttaja
                          WHERE kayttajanimi = 'Integraatio');

  -- Selvitetään löytyykö siltaan liittyvä kohteen osa kannasta. Lähtökohtana on kanavasilta-aineistossa saatu siltanumero (= kan_kohteenosa.lähdetunnus).
  kohteen_osa := (SELECT id
                  FROM kan_kohteenosa
                  WHERE lahdetunnus = new."siltanro" AND tyyppi IN ('silta', 'rautatiesilta'));

  -- K O H T E E N   O S A
  -- Jos kohteen osaa ei ole sovelluksen taulussa, se luodaan.
  -- Oletuskäyttötapa on silloilla INSERTOIDESSA 'muu', koska tietoa ei saada integraation kautta.
  -- Tieto päivitetään käsin kantaan, älä päivitä oletuskäyttötapaa update-lauseessa.
  oletuskaytotapa = 'muu';

  IF (kohteen_osa ISNULL)
  THEN
    INSERT INTO kan_kohteenosa (tyyppi, nimi, oletuspalvelumuoto, luoja, luotu, sijainti, lahdetunnus, poistettu, etuliite)
    VALUES
      ('silta' :: KOHTEENOSA_TYYPPI, new."nimi", oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO, integraatiokayttaja,
       current_timestamp, new."geometria", new."siltanro", new."poistettu", new."tunnus");
  ELSE
    UPDATE kan_kohteenosa
    SET
      nimi               = new."nimi",
      sijainti           = new."geometria" :: GEOMETRY,
      muokattu        = current_timestamp,
      muokkaaja = integraatiokayttaja,
      poistettu = new."poistettu",
      etuliite =  new."tunnus"
    WHERE id = kohteen_osa;
  END IF;

  RETURN new;

END;
$$ LANGUAGE plpgsql;