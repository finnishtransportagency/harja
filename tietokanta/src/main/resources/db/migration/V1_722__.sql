ALTER TABLE silta ADD COLUMN urakat INTEGER[];
CREATE INDEX i_sillan_urakat ON silta USING GIN ("urakat");

-- Arrayn elementeille ei voi määritellä FOREIGN KEY constrainttia, joten
-- luodaan se triggereiden avulla urakat sarakkeelle.
CREATE FUNCTION sillan_urakat_olemassa()
RETURNS TRIGGER AS $$
DECLARE urakka_ INTEGER;
BEGIN
  FOREACH urakka_ IN ARRAY NEW.urakat ::INT[] LOOP
    IF NOT EXISTS(SELECT 1 FROM urakka WHERE id=urakka_) THEN
      RAISE EXCEPTION 'Sillalle % yritettiin asettaa urakka %, mutta sitä ei ole olemassa.', NEW.id, urakka_;
      RETURN NULL;
    END IF;
  END LOOP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION silta_ei_referoi_urakkaa()
RETURNS TRIGGER AS $$
DECLARE silta_ INTEGER;
BEGIN
  SELECT INTO silta_ id FROM silta WHERE ARRAY[OLD.id] ::INT[] <@ urakat LIMIT 1;
  IF silta_ IS NOT NULL THEN
    RAISE EXCEPTION 'Yritettiin poistaa urakka %, mutta silta % referoi sitä.', OLD.id, silta_;
    RETURN NULL;
  END IF;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER t_sillan_urakat_olemassa
  BEFORE INSERT OR UPDATE OF urakat ON silta
  FOR EACH ROW EXECUTE PROCEDURE sillan_urakat_olemassa();

CREATE TRIGGER t_silta_ei_referoi_urakkaa
  BEFORE DELETE ON urakka
  FOR EACH ROW EXECUTE PROCEDURE silta_ei_referoi_urakkaa();

DO $$ DECLARE
  silta_urakka_rivi sillat_alueurakoittain%ROWTYPE;
  sillan_urakat_ INT[];
BEGIN
  FOR silta_urakka_rivi IN (SELECT * FROM sillat_alueurakoittain) LOOP
    SELECT INTO sillan_urakat_ urakat
    FROM silta
    WHERE id=silta_urakka_rivi.silta;

    IF sillan_urakat_ IS NULL THEN
      UPDATE silta SET urakat = ARRAY[silta_urakka_rivi.urakka] ::INT[] WHERE id=silta_urakka_rivi.silta;
    ELSIF NOT (SELECT sillan_urakat_ @> ARRAY[silta_urakka_rivi.urakka]) THEN
      UPDATE silta SET urakat = urakat || silta_urakka_rivi.urakka WHERE id=silta_urakka_rivi.silta;
    END IF;
  END LOOP;
END $$;

DROP FUNCTION IF EXISTS paivita_sillat_alueurakoittain();
DROP MATERIALIZED VIEW IF EXISTS sillat_alueurakoittain;