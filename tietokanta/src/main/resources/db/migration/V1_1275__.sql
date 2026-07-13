CREATE TABLE materiaalivalimuisti_paivitystarve (
  id SERIAL PRIMARY KEY,
  toteuma_id INTEGER NOT NULL,
  urakka_id INTEGER NOT NULL,
  toteuma_alkanut_vanha TIMESTAMP NOT NULL,
  luotu TIMESTAMP,
  luoja INTEGER,
  urakan_valimuisti_paivitetty BOOLEAN NOT NULL DEFAULT FALSE,
  sopimuksen_valimuisti_paivitetty BOOLEAN NOT NULL DEFAULT FALSE,
  muokattu TIMESTAMP,
  muokkaaja INTEGER
);

CREATE OR REPLACE FUNCTION update_toteuma_check_partition()
  RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO materiaalivalimuisti_paivitystarve (toteuma_id, urakka_id, toteuma_alkanut_vanha, luotu, luoja)
    VALUES (NEW.id, NEW.urakka, OLD.alkanut::DATE, CURRENT_TIMESTAMP, (SELECT id FROM kayttaja WHERE kayttajatunnus = 'Integraatio'));
  EXECUTE format('DELETE FROM %I.%I WHERE id = %L::INTEGER;', TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);
  INSERT INTO toteuma VALUES (NEW.*);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;
