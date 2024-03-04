-- Lisätään reikäpaikkaukset tietokantaan, reikäpaikkaukset tuodaan Excelistä ja nämä insertoidaan paikkaus tauluun
CREATE TYPE paikkaustyyppi AS ENUM ('paikkaus', 'reikapaikkaus');
-- Lisätään sarake paikkaustauluun, annetaan kaikille olemassaoleville tyypiksi 'paikkaus', koska tietokannassa ei ole vielä reikäpaikkauksia
ALTER TABLE paikkaus ADD COLUMN tyyppi paikkaustyyppi DEFAULT 'paikkaus';

-- Sallitaan reikäpaikkauksille paikkauskohde NULL
ALTER TABLE paikkaus ALTER COLUMN "paikkauskohde-id" DROP NOT NULL;

-- Reikäpaikkaukset ei ole kiinni missään kohteessa, joten meidän tulee sallia NULL arvot
-- Tehdään uusi constraint, jolla sallitaan NULL jos tyyppi on reikäpaikkaus mutta muuten heitetään virhe
ALTER TABLE     paikkaus
ADD CONSTRAINT  paikkauskohde_sallitaanko_null
CHECK           (tyyppi = 'reikapaikkaus' OR paikkauskohde-id IS NOT NULL);

-- Lisätään kustannus sarake reikäpaikkauksille
ALTER TABLE paikkaus ADD COLUMN kustannus NUMERIC;

-- Vaaditaan että kustannus on läsnä reikäpaikkauksille
ALTER TABLE    paikkaus 
ADD CONSTRAINT kustannus_sallitaanko_null 
CHECK          (tyyppi = 'paikkaus' OR kustannus IS NOT NULL);

-- Lisätään vielä yksikkö sarake reikäpaikkauksille
ALTER TABLE paikkaus ADD COLUMN yksikko VARCHAR(20);

-- Vaaditaan että yksikkö on läsnä reikäpaikkauksille
ALTER TABLE    paikkaus 
ADD CONSTRAINT yksikko_sallitaanko_null 
CHECK          (tyyppi = 'paikkaus' OR yksikko IS NOT NULL);


-- Jos ulkoinen-id ei ole 0, sen pitää olla jokaisella urakalla uniikki
-- Eli sallitaan esim arvo 123 urakalle 1 sekä 2, mutta urakalla 1 ei voi olla arvoa 123 kahdesti.
CREATE UNIQUE INDEX unique_ulkoinen_id_urakalla
ON paikkaus ("urakka-id", "ulkoinen-id")
WHERE "ulkoinen-id" <> 0;

DROP FUNCTION IF EXISTS reikapaikkaus_upsert(
  paikkaustyyppi,  INT, 
  TIMESTAMP, INT, 
  TIMESTAMP, INT, 
  BOOLEAN, INT, 
  INT, INT, 
  TIMESTAMP, TIMESTAMP, 
  TR_OSOITE, INT, 
  TEXT, NUMERIC, 
  NUMERIC,  NUMERIC, 
  NUMERIC, INT, 
  TEXT, NUMERIC,
  TEXT, GEOMETRY
);

-- UPSERT funktio, sen takia koska INSERT .. ON CONFLICT ei toimi tässä tapauksessa  
-- Vaikka kaikkia paikkaus sarakkeita ei reikäpaikkauksessa tarvita, lyöty silti mukaan jos tarvitaankin myöhemmin 
CREATE OR REPLACE FUNCTION reikapaikkaus_upsert(
  _tyyppi paikkaustyyppi,  _luojaid INT, 
  _luotu TIMESTAMP, _muokkaajaid INT, 
  _muokattu TIMESTAMP, _poistajaid INT, 
  _poistettu BOOLEAN, _urakkaid INT, 
  _paikkauskohdeid INT, _ulkoinenid INT, 
  _alkuaika TIMESTAMP, _loppuaika TIMESTAMP, 
  _tierekisteriosoite TR_OSOITE, _tyomenetelma INT, 
  _massatyyppi TEXT, _leveys NUMERIC, 
  _massamenekki NUMERIC, _massamaara NUMERIC, 
  _pintaala NUMERIC, _raekoko INT, 
  _kuulamylly TEXT, _kustannus NUMERIC,
  _yksikko TEXT, _sijainti GEOMETRY
) RETURNS VOID AS $$
BEGIN
  UPDATE paikkaus SET
    tyyppi = _tyyppi, 
    "luoja-id" = _luojaid,
    luotu = _luotu, 
    "muokkaaja-id" = _muokkaajaid,
    muokattu = _muokattu, 
    "poistaja-id" = _poistajaid,
    poistettu = _poistettu, 
    "paikkauskohde-id" = _paikkauskohdeid,
    alkuaika = _alkuaika, 
    loppuaika = _loppuaika,
    tierekisteriosoite = _tierekisteriosoite, 
    tyomenetelma = _tyomenetelma,
    massatyyppi = _massatyyppi, 
    leveys = _leveys,
    massamenekki = _massamenekki,
    massamaara = _massamaara,
    "pinta-ala" = _pintaala, 
    raekoko = _raekoko,
    kuulamylly = _kuulamylly, 
    kustannus = _kustannus,
    yksikko = _yksikko, 
    sijainti = _sijainti
  WHERE "urakka-id" = _urakkaid AND "ulkoinen-id" = _ulkoinenid;
  -- FOUND ilmeisesti jokin vakio postgres muuttuja, kertoo viime PERFORM, INSERT, UPDATE tilan
  IF NOT FOUND THEN
    -- Eli jos viime UPDATE ei palauttanut mitään, riviä ei ole kannassa -> insert 
    INSERT INTO paikkaus (
      tyyppi, 
      "luoja-id", 
      luotu, 
      "muokkaaja-id", 
      muokattu, 
      "poistaja-id", 
      poistettu, 
      "urakka-id", 
      "paikkauskohde-id", 
      "ulkoinen-id", 
      alkuaika, 
      loppuaika, 
      tierekisteriosoite, 
      tyomenetelma, 
      massatyyppi, 
      leveys, 
      massamenekki, 
      massamaara, 
      "pinta-ala", 
      raekoko, 
      kuulamylly, 
      kustannus,
      yksikko,
      sijainti 
    )
    VALUES (
      _tyyppi, 
      _luojaid, 
      _luotu, 
      _muokkaajaid, 
      _muokattu, 
      _poistajaid, 
      _poistettu, 
      _urakkaid, 
      _paikkauskohdeid,
      _ulkoinenid, 
      _alkuaika, 
      _loppuaika, 
      _tierekisteriosoite, 
      _tyomenetelma, 
      _massatyyppi, 
      _leveys,
      _massamenekki, 
      _massamaara, 
      _pintaala,
      _raekoko, 
      _kuulamylly,
      _kustannus,
      _yksikko,
      _sijainti
    );
  END IF;
END;
$$ LANGUAGE plpgsql;
