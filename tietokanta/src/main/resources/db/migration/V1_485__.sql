-- Jos yksikköhintaista työtä muokataan, poistetaan hoitokauden laskutusyhteenvedot
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_yksikkohintainen_tyo() RETURNS trigger AS $$
BEGIN
  PERFORM poista_hoitokauden_muistetut_laskutusyht(NEW.urakka, NEW.alkupvm::DATE);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER tg_poista_muistetut_laskutusyht_yksikkohintainen_tyo
AFTER INSERT OR UPDATE
  ON yksikkohintainen_tyo
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_yksikkohintainen_tyo();