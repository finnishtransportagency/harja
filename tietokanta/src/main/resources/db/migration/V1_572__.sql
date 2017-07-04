CREATE TABLE reimari_sopimuslinkki (
"harja-sopimus-id"     INTEGER
                       UNIQUE
                       REFERENCES sopimus,
"reimari-sopimus-id"   INTEGER
                       UNIQUE);

CREATE OR REPLACE FUNCTION toimenpiteen_sopimus_id_trigger_proc()
  RETURNS TRIGGER AS
$$
DECLARE id_temp INTEGER;
BEGIN
  id_temp := (SELECT id FROM sopimus hs, reimari_sopimuslinkki sl
    WHERE
      sl."harja-sopimus-id" = hs.id AND
      sl."reimari-sopimus-id" = (NEW."reimari-sopimus").nro LIMIT 1);

  NEW."sopimus-id" = id_temp;
                -- id:ksi tulee NULL jos ei löydy, joka on ok
  RAISE NOTICE 'trigger: sopimus-id arvoksi %', NEW."sopimus-id";
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS toimenpiteen_sopimus_id_trigger ON reimari_toimenpide;
CREATE TRIGGER toimenpiteen_sopimus_id_trigger
  BEFORE INSERT OR UPDATE ON reimari_toimenpide
  FOR EACH ROW
  EXECUTE PROCEDURE toimenpiteen_sopimus_id_trigger_proc();
