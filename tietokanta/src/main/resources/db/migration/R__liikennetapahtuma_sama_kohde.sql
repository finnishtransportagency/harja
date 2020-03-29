CREATE OR REPLACE FUNCTION sama_kohde_tapahtumalla_ja_osalla()
  RETURNS TRIGGER AS
$$
DECLARE tapahtuman_kohde INTEGER;
BEGIN
  tapahtuman_kohde := (SELECT "kohde-id" FROM kan_liikennetapahtuma WHERE id = NEW."liikennetapahtuma-id");
  IF (tapahtuman_kohde = NEW."kohde-id")
  THEN
    RETURN NEW;
  ELSE
    RAISE EXCEPTION 'Liikennetapahtuma ja kohteenosan palvelumuotorivi eivät käytä samaa kohdetta';
    RETURN NULL;
  END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS sama_kohde_tapahtumalla_ja_osalla_trigger ON kan_liikennetapahtuma_toiminto;

CREATE TRIGGER sama_kohde_tapahtumalla_ja_osalla_trigger
BEFORE INSERT OR UPDATE ON kan_liikennetapahtuma_toiminto
FOR EACH ROW
EXECUTE FUNCTION sama_kohde_tapahtumalla_ja_osalla();