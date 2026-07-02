CREATE TABLE toteuma_muutos (
  id SERIAL PRIMARY KEY,
  toteuma_id INTEGER NOT NULL,
  urakka_id INTEGER NOT NULL,
  vanha_alkanut TIMESTAMP NOT NULL,
  muutospvm TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  urakan_valimuisti_paivitetty BOOLEAN NOT NULL DEFAULT FALSE,
  sopimuksen_valimuisti_paivitetty BOOLEAN NOT NULL DEFAULT FALSE,
  muokattu TIMESTAMP
);

CREATE OR REPLACE FUNCTION update_toteuma_check_partition()
  RETURNS TRIGGER AS $$
BEGIN
  IF (NEW.lahde = 'harja-api') THEN  
    INSERT INTO toteuma_muutos (toteuma_id, urakka_id, vanha_alkanut)
      VALUES (NEW.id, NEW.urakka, OLD.alkanut::DATE);
  END IF;
  EXECUTE format('DELETE FROM %I.%I WHERE id = %L::INTEGER;', TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);
  INSERT INTO toteuma VALUES (NEW.*);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;
