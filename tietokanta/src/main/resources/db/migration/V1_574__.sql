ALTER TABLE reimari_toimenpide ADD COLUMN "reimari-lisatyo" BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE reimari_toimenpide SET "reimari-lisatyo" = FALSE WHERE hintatyyppi = 'kokonaishintainen';
UPDATE reimari_toimenpide SET "reimari-lisatyo" = TRUE WHERE hintatyyppi = 'yksikkohintainen';
CREATE OR REPLACE FUNCTION toimenpiteen_hintatyyppi_trigger_proc()
  RETURNS TRIGGER AS
$$
BEGIN
  IF NEW."hintatyyppi" IS NULL THEN
        NEW."hintatyyppi" = CASE WHEN NEW."reimari-lisatyo" IS FALSE THEN 'kokonaishintainen'
                                 WHEN NEW."reimari-lisatyo" IS TRUE THEN 'yksikkohintainen'
                                 ELSE 'kokonaishintainen'
                            END;
     RAISE NOTICE 'reimari_toimenpide hintatyyppi trigger: hintatyypiksi %', NEW."hintatyyppi";
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS toimenpiteen_hintatyyppi_trigger ON reimari_toimenpide;
CREATE TRIGGER toimenpiteen_hintatyyppi_trigger
  BEFORE INSERT OR UPDATE ON reimari_toimenpide
  FOR EACH ROW
  EXECUTE PROCEDURE toimenpiteen_hintatyyppi_trigger_proc();
