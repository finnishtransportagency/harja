INSERT INTO urakkatyypin_indeksi (urakkatyyppi, indeksinimi, raakaaine, koodi) VALUES ('vesivayla-kanavien-hoito', 'Palvelujen tuottajahintaindeksi 2010', null, 'PTHI2010');
-- TODO: Tarkista indeksistä käytetty lyhenne, tarkista minkä vuoden indeksi on kyseessä


CREATE OR REPLACE FUNCTION aseta_urakan_oletusindeksi()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN

  IF NEW.tyyppi = 'hoito' THEN
    IF EXTRACT(year FROM NEW.alkupvm) < 2017 THEN
      NEW.indeksi := 'MAKU 2005';
    ELSE
      NEW.indeksi := 'MAKU 2010';
    END IF;

  ELSEIF NEW.tyyppi = 'vesivayla-hoito' THEN
    IF EXTRACT(year FROM NEW.alkupvm) < 2017 THEN
      NEW.indeksi := 'MAKU 2005 kunnossapidon osaindeksi';
    ELSE
      NEW.indeksi := 'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi';
    END IF;

  ELSEIF NEW.tyyppi = 'vesivayla-kanavien-hoito' THEN
    IF EXTRACT(year FROM NEW.alkupvm) < 2017 THEN
      NEW.indeksi := 'Palvelujen tuottajahintaindeksi 2005';
    ELSE
      NEW.indeksi := 'Palvelujen tuottajahintaindeksi 2010';
    END IF;

  END IF;
  RETURN NEW;
END;
$$
