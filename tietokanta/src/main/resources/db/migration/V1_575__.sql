DROP TRIGGER IF EXISTS toimenpiteen_sopimus_id_trigger ON reimari_toimenpide;
DROP FUNCTION IF EXISTS toimenpiteen_sopimus_id_trigger_proc();

ALTER TABLE reimari_sopimuslinkki ALTER COLUMN "reimari-sopimus-id" DROP NOT NULL;
ALTER TABLE reimari_sopimuslinkki DROP CONSTRAINT "reimari_sopimuslinkki_harja-sopimus-id_key";
ALTER TABLE reimari_sopimuslinkki DROP CONSTRAINT "reimari_sopimuslinkki_reimari-sopimus-id_key";
ALTER TABLE reimari_sopimuslinkki ADD COLUMN "reimari-diaarinro" TEXT;

CREATE OR REPLACE FUNCTION toimenpiteen_linkit_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp INTEGER;
BEGIN
  id_temp := (SELECT id FROM sopimus hs, reimari_sopimuslinkki sl
    WHERE
      sl."harja-sopimus-id" = hs.id AND
      sl."reimari-sopimus-id" = (NEW."reimari-sopimus").nro LIMIT 1);
  IF id_temp IS NULL THEN
    id_temp := (SELECT id FROM sopimus hs, reimari_sopimuslinkki sl
                  WHERE
                  sl."harja-sopimus-id" = hs.id AND
                  sl."reimari-diaarinro" = (NEW."reimari-sopimus").diaarinro LIMIT 1);
  END IF;
  NEW."sopimus-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide linkit trigger: sopimus-id arvoksi %', NEW."sopimus-id";

  id_temp := (SELECT id FROM vv_turvalaite
               WHERE tunniste IS NOT NULL AND tunniste = (NEW."reimari-turvalaite").nro LIMIT 1);

  NEW."turvalaite-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'reimari_toimenpide linkit trigger: turvalaite-id arvoksi %', NEW."turvalaite-id";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS toimenpiteen_linkit_trigger ON reimari_toimenpide;
CREATE TRIGGER toimenpiteen_linkit_trigger
  BEFORE INSERT OR UPDATE ON reimari_toimenpide
  FOR EACH ROW
  EXECUTE PROCEDURE toimenpiteen_linkit_trigger_proc();
