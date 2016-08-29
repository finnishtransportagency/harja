-- Lisää indeksit_kaytossa kenttä
ALTER TABLE urakka ADD COLUMN indeksi TEXT;

CREATE OR REPLACE FUNCTION aseta_urakan_oletusindeksi() RETURNS trigger AS $$
BEGIN
  IF NEW.tyyppi = 'hoito' THEN
    IF EXTRACT(year FROM NEW.alkupvm) < 2017 THEN
      NEW.indeksi := 'MAKU 2005';
    ELSE
      NEW.indeksi := 'MAKU 2010';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_aseta_urakan_oletusindeksi
  BEFORE INSERT
  ON urakka
  FOR EACH ROW
  EXECUTE PROCEDURE aseta_urakan_oletusindeksi();

-- Päivitetään olemassaolevat urakat
UPDATE urakka
   SET indeksi = (CASE WHEN EXTRACT(YEAR FROM alkupvm) < 2017
                       THEN 'MAKU 2005'
                       ELSE 'MAKU 2010' END)
 WHERE tyyppi = 'hoito';
