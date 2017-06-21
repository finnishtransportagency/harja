ALTER TABLE reimari_toimenpide ALTER COLUMN "urakka-id" DROP NOT NULL;
DROP TRIGGER IF EXISTS toimenpiteen_urakka_id_trigger ON reimari_toimenpide;
DROP FUNCTION IF EXISTS toimenpiteen_urakka_id_trigger_proc();
CREATE OR REPLACE FUNCTION toimenpiteen_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp TEXT;
BEGIN
  id_temp := (SELECT id FROM urakka u
    WHERE
    u.urakkanro = (NEW."reimari-turvalaite").ryhma::text) LIMIT 1;
  IF NEW.lisatyo IS TRUE THEN
    NEW.hintatyyppi = 'yksikkohintainen';
  ELSE
    NEW.hintatyyppi = 'kokonaishintainen';
  END IF;

  NEW."urakka-id" = id_temp;
                -- urakka-id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide trigger: urakka-id arvoksi %', NEW."urakka-id";
  RAISE NOTICE 'reimari_toimenpide trigger: hintatyyppi arvoksi %', NEW."hintatyyppi";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS toimenpiteen_trigger_proc ON reimari_toimenpide;
CREATE TRIGGER toimenpiteen_urakka_id_trigger
  BEFORE INSERT OR UPDATE ON reimari_toimenpide
  FOR EACH ROW
  EXECUTE PROCEDURE toimenpiteen_trigger_proc();
