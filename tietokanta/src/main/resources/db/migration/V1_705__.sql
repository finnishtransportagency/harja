CREATE OR REPLACE VIEW reimari_toimenpiteen_vika AS
   SELECT id AS tpid, (UNNEST("reimari-viat")).id AS vikaid FROM reimari_toimenpide;

DROP FUNCTION IF EXISTS vv_vikailmoituksen_toimenpide_id_trigger_proc();

CREATE OR REPLACE FUNCTION vv_vikailmoituksen_toimenpide_id_trigger_proc()
  RETURNS TRIGGER AS
$$
BEGIN
   NEW."toimenpide-id" = (SELECT tpid FROM reimari_toimenpiteen_vika
                           WHERE vikaid = NEW."reimari-id" LIMIT 1);
   -- RAISE NOTICE 'löydettiiin vikailmoitukselle toimenpide-id %', NEW."toimenpide-id";
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS vv_vikailmoituksen_toimenpide_id_trigger ON vv_vikailmoitus;
CREATE TRIGGER vv_vikailmoituksen_toimenpide_id_trigger
  BEFORE INSERT OR UPDATE ON vv_vikailmoitus
  FOR EACH ROW
  EXECUTE PROCEDURE vv_vikailmoituksen_toimenpide_id_trigger_proc();

