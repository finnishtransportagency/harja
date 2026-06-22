-- Jos toteuman alkanut pvm muuttuu, vanha pvm tallennetaan tänne ajastettua välimuistin päivitystä varten
CREATE TABLE IF NOT EXISTS toteuma_alkanut_muutos (
  id SERIAL PRIMARY KEY,
  toteuma_id INTEGER NOT NULL,
  urakka_id INTEGER NOT NULL,
  vanha_alkanut DATE NOT NULL,
  muutospvm TIMESTAMP NOT NULL DEFAULT current_timestamp,
  kasitelty BOOLEAN DEFAULT FALSE
);

--Poistetaan cachen päivitys toteuma taulun triggeristä
CREATE OR REPLACE FUNCTION update_toteuma_check_partition()
    RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.lahde = 'harja-api') THEN
      -- Tallennetaan toteuman alkuperäinen alkanut pvm.
      -- Tätä tarvitaan ajastettussa välimuistitaulujen päivityksessä. Jos toteuman alkanut pvm muuttuu, välimuistitaulut tulee päivittää alkuperäisen ja uuden pvm:n päiviltä.
      INSERT INTO toteuma_alkanut_muutos (toteuma_id, urakka_id, vanha_alkanut)
      VALUES (NEW.id, NEW.urakka, OLD.alkanut::DATE);
    END IF;

    EXECUTE format('DELETE FROM %I.%I WHERE id = %L::INTEGER;', TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);
    INSERT INTO toteuma VALUES (NEW.*);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
