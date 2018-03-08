-- Jos urakan käyttämä indeksi muuttuu, poistetaan kaikki laskutusyhteenvedot cachesta
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_urakan_indeksi() RETURNS trigger AS $$
BEGIN
  IF (OLD.indeksi != NEW.indeksi
      -- Postgres ei tue vertailuoperaattoria NULL arvoille, vertaillaan ne eksplisiittisesti
      OR OLD.indeksi IS NULL AND NEW.indeksi IS NOT NULL
      OR NEW.indeksi IS NULL AND OLD.indeksi IS NOT NULL)
  THEN
    DELETE FROM laskutusyhteenveto_cache WHERE urakka = NEW.id;
    RETURN NULL;
  ELSE RETURN NULL;
  END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_urakan_indeksi
AFTER UPDATE
  ON urakka
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_urakan_indeksi();
