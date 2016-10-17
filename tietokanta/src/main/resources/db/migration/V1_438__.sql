-- Tee funktio, jolla voidaan muuttaa vanhat ilmoituksen selitteet tekstiarrayhyn

CREATE OR REPLACE FUNCTION mappaa_selitteet(vanhat_selitteet ilmoituksenselite [])
  RETURNS TEXT [] AS
$$
DECLARE
  uudet_selitteet TEXT [];
  uusi_selite     TEXT;
  vanha_selite    ilmoituksenselite;
BEGIN
  IF vanhat_selitteet IS NULL THEN
    RETURN NULL;
  END IF;
  FOREACH vanha_selite IN ARRAY vanhat_selitteet
  LOOP
    IF vanha_selite = 'tielleOnVuotanutNestettäLiikkuvastaAjoneuvosta' :: ilmoituksenselite
    THEN
      uusi_selite := 'tielleOnVuotanutNestettaLiikkuvastaAjoneuvosta';
    ELSEIF vanha_selite = 'tietOvatjaisiäJamarkia' :: ilmoituksenselite
      THEN
        uusi_selite := 'tietOvatjaisiaJamarkia';
    ELSE
      uusi_selite := vanha_selite :: TEXT;
    END IF;
    uudet_selitteet := uudet_selitteet || uusi_selite;
  END LOOP;
  RETURN uudet_selitteet;
END;
$$
LANGUAGE plpgsql;

-- Tee uusi sarake ja nimeä vanha uudestaan
ALTER TABLE ilmoitus
  RENAME COLUMN selitteet TO selitteet_temp;
ALTER TABLE asiakaspalauteluokka
  RENAME COLUMN selitteet TO selitteet_temp;
ALTER TABLE ilmoitus
  ADD selitteet TEXT [];
ALTER TABLE asiakaspalauteluokka
  ADD selitteet TEXT [];

-- Päivitä arvot

UPDATE ilmoitus
SET
  selitteet = mappaa_selitteet(selitteet_temp);

UPDATE asiakaspalauteluokka
SET selitteet = mappaa_selitteet(selitteet_temp);

-- Pudota vanhat sarakkeet ja tyyppi

ALTER TABLE ilmoitus
  DROP COLUMN selitteet_temp;
ALTER TABLE asiakaspalauteluokka
  DROP COLUMN selitteet_temp;

DROP FUNCTION mappaa_selitteet (vanhat_selitteet ilmoituksenselite []);

DROP TYPE ilmoituksenselite;
