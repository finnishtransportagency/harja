-- Laskutusyhteenvedon välimuisti
CREATE TABLE laskutusyhteenveto_cache (
  urakka integer references urakka (id),
  alkupvm DATE,
  loppupvm DATE,
  rivit laskutusyhteenveto_rivi[]
);

CREATE INDEX laskutusyhteenveto_urakka ON laskutusyhteenveto_cache (urakka);

CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_ind() RETURNS trigger AS $$
DECLARE
  alku DATE;
  loppu DATE;
BEGIN
  IF NEW.kuukausi >= 10 THEN
    alku := make_date(NEW.vuosi, 10, 1);
    loppu := make_date(NEW.vuosi+1, 9, 30);
  ELSE
    alku := make_date(NEW.vuosi-1, 10, 1);
    loppu := make_date(NEW.vuosi, 9, 30);
  END IF;
  RAISE NOTICE 'Poistetaan muistetut laskutusyhteenvedot % - %', alku, loppu;
  DELETE FROM laskutusyhteenveto_cache
   WHERE alkupvm >= alku
     AND loppupvm <= loppu;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_ind
  AFTER INSERT OR UPDATE
  ON indeksi
  FOR EACH ROW
  EXECUTE PROCEDURE poista_muistetut_laskutusyht_ind();
