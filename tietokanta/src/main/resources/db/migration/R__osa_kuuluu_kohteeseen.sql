CREATE OR REPLACE FUNCTION osa_kuuluu_kohteeseen()
  RETURNS TRIGGER AS
$$
DECLARE kohteenosan_kohde INTEGER;
BEGIN
  IF ((NEW."kohteenosa-id" IS NULL AND NEW."kohde-id" IS NOT NULL) OR
      (NEW."kohteenosa-id" IS NULL AND NEW."kohde-id" IS NULL))
  THEN
    RETURN NEW;
  ELSEIF (NEW."kohteenosa-id" IS NOT NULL AND NEW."kohde-id" IS NULL)
    THEN
      RAISE EXCEPTION 'Annettiin kohteenosa, mutta ei kohdetta';
      RETURN NULL;
  ELSE
    kohteenosan_kohde := (SELECT "kohde-id"
                          FROM kan_kohteenosa
                          WHERE id = NEW."kohteenosa-id");
    IF (NEW."kohde-id" = kohteenosan_kohde)
    THEN
      RETURN NEW;
    ELSE
      RAISE EXCEPTION 'Kanavakohteenosa % ei kuulu annettuun kohteeseen %', NEW."kohteenosa-id", NEW."kohde-id";
      RETURN NULL;
    END IF;
  END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS hairion_kohteen_tarkastus
ON kan_hairio;
CREATE TRIGGER hairion_kohteen_tarkastus
BEFORE INSERT OR UPDATE ON kan_hairio
FOR EACH ROW
EXECUTE FUNCTION osa_kuuluu_kohteeseen();

DROP TRIGGER IF EXISTS toimenpiteen_kohteen_tarkastus
ON kan_toimenpide;
CREATE TRIGGER toimenpiteen_kohteen_tarkastus
BEFORE INSERT OR UPDATE ON kan_toimenpide
FOR EACH ROW
EXECUTE FUNCTION osa_kuuluu_kohteeseen();

DROP TRIGGER IF EXISTS liikennetapahtuman_kohteen_tarkastus
ON kan_liikennetapahtuma_toiminto;
CREATE TRIGGER liikennetapahtuman_kohteen_tarkastus
BEFORE INSERT OR UPDATE ON kan_liikennetapahtuma_toiminto
FOR EACH ROW
EXECUTE FUNCTION osa_kuuluu_kohteeseen();