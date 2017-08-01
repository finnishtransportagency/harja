
-- Poista muistetut laskutusyhteenvedot kun toteuma_tehtava muuttuu.
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_toteuma_tehtava() RETURNS trigger AS $$
DECLARE
  toteuma_alkanut DATE;
  urakka_id INTEGER;
  toteuma_id INTEGER;
BEGIN
  toteuma_id = NEW.toteuma;
  SELECT alkanut FROM toteuma where id = toteuma_id INTO toteuma_alkanut;
  SELECT urakka FROM toteuma where id = toteuma_id INTO urakka_id;
  PERFORM poista_hoitokauden_muistetut_laskutusyht(urakka_id, toteuma_alkanut::DATE);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Jos toteuma_tehtava muuttuu, poista laskutusyhteenvedot cachesta. koska
-- esim määrän muutoksella on vaikutus yksikköhintaisten töiden kustannuksiin
CREATE TRIGGER tg_poista_muistetut_laskutusyht_toteuma_tehtava
AFTER INSERT OR UPDATE
  ON toteuma_tehtava
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_toteuma_tehtava();