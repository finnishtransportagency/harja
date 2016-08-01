-- Tee toteumalle envelope
DROP INDEX toteuma_reitti_idx;

ALTER TABLE toteuma ADD COLUMN envelope geometry;

CREATE INDEX toteuma_envelope_idx ON toteuma USING GIST (envelope);

CREATE OR REPLACE FUNCTION muodosta_toteuman_envelope() RETURNS trigger AS $$
BEGIN
  IF NEW.reitti IS NOT NULL THEN
    NEW.envelope := ST_Envelope(NEW.reitti);
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_muodosta_toteuman_envelope
  BEFORE INSERT OR UPDATE
  ON toteuma
  FOR EACH ROW
  EXECUTE PROCEDURE muodosta_toteuman_envelope();

UPDATE toteuma SET envelope = ST_Envelope(reitti);
