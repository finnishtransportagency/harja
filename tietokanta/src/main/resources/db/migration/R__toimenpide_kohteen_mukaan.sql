CREATE OR REPLACE FUNCTION toimenpide_kohteen_mukaan_proc()
  RETURNS TRIGGER AS
$$
DECLARE osan_tyyppi KOHTEENOSA_TYYPPI;
BEGIN
  osan_tyyppi := (SELECT kan_kohteenosa.tyyppi
                     FROM kan_kohteenosa
                     WHERE id = NEW."kohteenosa-id");
  IF ((NEW.toimenpide IN ('avaus', 'ei-avausta')
       AND
       (osan_tyyppi IN ('silta', 'rautatiesilta')))
      OR
      (NEW.toimenpide IN ('sulutus', 'tyhjennys')
       AND
       (osan_tyyppi IN ('sulku'))))
  THEN
    RETURN NEW;
  ELSE
    RAISE EXCEPTION 'Liikennetapahtuman toimenpide % ei vastaa kohteen tyyppiä %', NEW.toimenpide, osan_tyyppi;
    RETURN NULL;
  END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS toimenpide_kohteen_mukaan_trigger ON kan_liikennetapahtuma_toiminto;

CREATE TRIGGER toimenpide_kohteen_mukaan_trigger
BEFORE INSERT OR UPDATE ON kan_liikennetapahtuma_toiminto
FOR EACH ROW
EXECUTE PROCEDURE toimenpide_kohteen_mukaan_proc();