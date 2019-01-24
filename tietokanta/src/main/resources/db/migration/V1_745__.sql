-- Päivitä Saimaan kanavan indeksi
UPDATE urakka set indeksi = 'Palvelujen tuottajahintaindeksi 2015' where sampoid = 'PR00002086';

-- Kanavapuolella aina 2015 indeksi
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
      NEW.indeksi := 'Palvelujen tuottajahintaindeksi 2015';

  END IF;
  RETURN NEW;
END;
$$