CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasulku_kohdetietoihin()
  RETURNS TRIGGER AS $$

-- Triggerifunktio päivittää integraation kautta saadut kanavasulkutiedot (kan_sulku) käyttöliittymän käyttämiin kohdetauluihin: kan_kohdekokonaisuus, kan_kohde, kan_kohteenosa.

DECLARE   integraatiokayttaja INTEGER;
  DECLARE kohteen_osa         INTEGER;
  DECLARE kohde               INTEGER;
  DECLARE kohdekokonaisuus    INTEGER;
  DECLARE oletuskaytotapa     TEXT;

BEGIN

  -- Selvitetään integraatiokäyttäjän id luoja-kenttään tallennusta varten.
  -- Integraatiokäyttäjä on luotu migraatiossa, jos sitä ei aiemmin ole ollut.
  integraatiokayttaja := (SELECT id
                          FROM kayttaja
                          WHERE kayttajanimi = 'Integraatio');

  -- Selvitetään löytyykö sulkuun liittyvä kohteen osa, kohde ja kohdekokonaisuus kannasta. Lähtökohtana on kanavasulkuaineistossa saatu kanavanumero (lähdetunnus)
  kohteen_osa := (SELECT id
                  FROM kan_kohteenosa
                  WHERE lahdetunnus = new."kanavanro");
  kohde := (SELECT "kohde-id"
            FROM kan_kohteenosa
            WHERE lahdetunnus = new."kanavanro");
  kohdekokonaisuus := (SELECT id
                       FROM kan_kohdekokonaisuus
                       WHERE id = (SELECT "kohdekokonaisuus-id"
                                   FROM kan_kohde
                                   WHERE id = kohde));

  -- Jos kohdekokonaisuutta ei vielä löytynyt, selvitetään löytyykö se nimen perusteella.
  IF (kohdekokonaisuus ISNULL)
  THEN
    kohdekokonaisuus := (SELECT id
                         FROM kan_kohdekokonaisuus
                         WHERE nimi = new."kanavakokonaisuus");
  END IF;

  -- Jos kohdeetta ei vielä löytynyt, selvitetään löytyykö se nimen perusteella.
  IF (kohde ISNULL)
  THEN
    kohde := (SELECT id
              FROM kan_kohde
              WHERE nimi = new."nimi");
  END IF;

  -- K O H D E K O K O N A I S U U S
  -- Luo tai päivitä kohdekokonaisuus
  IF (kohdekokonaisuus ISNULL)
  THEN
    INSERT INTO kan_kohdekokonaisuus (nimi, luotu, luoja)
    VALUES (new."kanavakokonaisuus", current_timestamp, integraatiokayttaja);
    kohdekokonaisuus := (SELECT id
                         FROM kan_kohdekokonaisuus
                         WHERE nimi = new."kanavakokonaisuus");
  ELSE
    UPDATE kan_kohdekokonaisuus
    SET nimi = new."kanavakokonaisuus", muokattu = current_timestamp, muokkaaja = integraatiokayttaja
    WHERE id = kohdekokonaisuus;
  END IF;

  -- K O H D E
  -- Luo tai päivitä kohde.
  -- Hox. Kohteen sijainti != kohteen osan sijainti. Kohteella ja kohteen osilla on omat geometriansa.
  IF (kohde ISNULL)
  THEN
    INSERT INTO kan_kohde ("kohdekokonaisuus-id", nimi, luotu, luoja, poistettu)
    VALUES (kohdekokonaisuus, new."nimi", current_timestamp, integraatiokayttaja, new."poistettu");
    kohde := (SELECT id
              FROM kan_kohde
              WHERE nimi = new."nimi");
  ELSE
    UPDATE kan_kohde
    SET
      nimi      = new."nimi",
      muokattu  = current_timestamp,
      muokkaaja = integraatiokayttaja
    WHERE id = kohde;
  END IF;

  -- K O H T E E N   O S A
  -- Luo tai päivitä kohteenosa

  -- Selvitä tallennettava oletuskäyttötapa
  IF (new."kayttotapa" = 'Itsepalvelu')
  THEN oletuskaytotapa = 'itse'; END IF;
  IF (new."kayttotapa" = 'Kaukokäyttö')
  THEN oletuskaytotapa = 'kauko'; END IF;
  IF (new."kayttotapa" = 'Paikalliskäyttö')
  THEN oletuskaytotapa = 'paikallis'; END IF;
  IF (oletuskaytotapa ISNULL)
  THEN oletuskaytotapa = 'muu'; END IF;

  IF (kohteen_osa ISNULL)
  THEN
    INSERT INTO kan_kohteenosa (tyyppi, "kohde-id", nimi, oletuspalvelumuoto, luoja, luotu, sijainti, lahdetunnus, poistettu)
    VALUES
      ('sulku' :: KOHTEENOSA_TYYPPI, kohde, new."nimi", oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO, integraatiokayttaja,
       current_timestamp, ST_Centroid(new."geometria"), new."kanavanro", new."poistettu");
  ELSE
    UPDATE kan_kohteenosa
    SET
      "kohde-id"         = kohde,
      nimi = new."nimi",
      oletuspalvelumuoto = oletuskaytotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO,
      sijainti           = ST_Centroid(new."geometria") :: GEOMETRY,
      muokattu        = current_timestamp,
      poistettu = new."poistettu",
      muokkaaja = integraatiokayttaja
    WHERE id = kohteen_osa;
  END IF;

  RETURN new;

END;
$$ LANGUAGE plpgsql;