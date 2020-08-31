-- Siirrä trigger Repeatable migraatioon & tyhjennä envelope jos reitti tyhjenee
CREATE OR REPLACE FUNCTION muodosta_toteuman_envelope() RETURNS trigger AS $$
BEGIN
  IF NEW.reitti IS NOT NULL THEN
    NEW.envelope := ST_Envelope(NEW.reitti);
  ELSIF NEW.REITTI IS NULL THEN
    NEW.envelope = NULL;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_muodosta_toteuman_envelope ON toteuma;