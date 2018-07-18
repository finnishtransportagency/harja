ALTER TABLE silta ADD COLUMN urakat INTEGER[];
CREATE INDEX i_sillan_urakat ON silta USING GIN ("urakat");

DO $$ DECLARE
  silta_urakka_rivi sillat_alueurakoittain%ROWTYPE;
BEGIN
  FOR silta_urakka_rivi IN (SELECT * FROM sillat_alueurakoittain) LOOP
    IF NOT EXISTS (SELECT 1 FROM silta WHERE silta_urakka_rivi.urakka=ANY(urakat)) THEN
      UPDATE silta SET urakat = urakat || silta_urakka_rivi.urakka WHERE id=silta_urakka_rivi.silta;
    END IF;
  END LOOP;
END $$;

DROP FUNCTION IF EXISTS  paivita_sillat_alueurakoittain();
DROP MATERIALIZED VIEW IF EXISTS sillat_alueurakoittain;