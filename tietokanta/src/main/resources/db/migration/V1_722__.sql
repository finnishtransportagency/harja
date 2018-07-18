ALTER TABLE silta ADD COLUMN urakat INTEGER[];
CREATE INDEX i_sillan_urakat ON silta USING GIN ("urakat");

DO $$ DECLARE
  silta_urakka_rivi sillat_alueurakoittain%ROWTYPE;
  sillan_urakat_ INT[];
BEGIN
  FOR silta_urakka_rivi IN (SELECT * FROM sillat_alueurakoittain) LOOP
    SELECT INTO sillan_urakat_ urakat
    FROM silta
    WHERE id=silta_urakka_rivi.silta;

    IF sillan_urakat_ IS NULL THEN
      UPDATE silta SET urakat = ARRAY[silta_urakka_rivi.urakka] ::INT[] WHERE id=silta_urakka_rivi.silta;
    ELSIF NOT (SELECT sillan_urakat_ @> ARRAY[silta_urakka_rivi.urakka]) THEN
      UPDATE silta SET urakat = urakat || silta_urakka_rivi.urakka WHERE id=silta_urakka_rivi.silta;
    END IF;
  END LOOP;
END $$;

DROP FUNCTION IF EXISTS  paivita_sillat_alueurakoittain();
DROP MATERIALIZED VIEW IF EXISTS sillat_alueurakoittain;