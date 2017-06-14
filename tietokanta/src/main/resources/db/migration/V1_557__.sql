ALTER TABLE reimari_toimenpide ALTER COLUMN "urakka-id" DROP NOT NULL;
CREATE OR REPLACE FUNCTION toimenpiteen_urakka_id_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp TEXT;
BEGIN
  id_temp := (SELECT id FROM urakka u
    WHERE
    u.urakkanro = (NEW."reimari-turvalaite").ryhma::text) LIMIT 1;

  NEW."urakka-id" = id_temp;
                -- urakka-id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'trigger: urakka-id arvoksi %', NEW."urakka-id";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS toimenpiteen_urakka_id_trigger ON reimari_toimenpide;
CREATE TRIGGER toimenpiteen_urakka_id_trigger
  BEFORE INSERT OR UPDATE ON reimari_toimenpide
  FOR EACH ROW
  EXECUTE PROCEDURE toimenpiteen_urakka_id_trigger_proc();
