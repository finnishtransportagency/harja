CREATE OR REPLACE FUNCTION kaikille_osille_palvelumuoto()
  RETURNS TRIGGER AS
$$
DECLARE tapahtuman_kohde INTEGER;
DECLARE kohteen_osat INTEGER[];
DECLARE palvelumuodot INTEGER[];

BEGIN
  tapahtuman_kohde := NEW."kohde-id";
  kohteen_osat := (SELECT ARRAY((SELECT id FROM kan_kohteenosa WHERE "kohde-id" = tapahtuman_kohde)));
  palvelumuodot := (SELECT ARRAY((SELECT "kohteenosa-id" FROM kan_liikennetapahtuma_toiminto WHERE "liikennetapahtuma-id" = NEW.id)));
  IF (kohteen_osat <@ palvelumuodot AND palvelumuodot <@ kohteen_osat)
  THEN
    RETURN NEW;
  ELSE
    RAISE EXCEPTION 'Liikennetapahtumalle pitää kirjata palvelumuoto jokaiselle kohteessa olevalle kohteenosalle. Liikennetapahtuman % kohde sisältää osat %, mutta palvelumuodot kohteille %', NEW.id, kohteen_osat, palvelumuodot;
    RETURN NULL;
  END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS kaikille_osille_palvelumuoto_trigger
ON kan_liikennetapahtuma;

CREATE CONSTRAINT TRIGGER kaikille_osille_palvelumuoto_trigger
AFTER INSERT OR UPDATE ON kan_liikennetapahtuma
  DEFERRABLE
FOR EACH ROW EXECUTE PROCEDURE kaikille_osille_palvelumuoto();