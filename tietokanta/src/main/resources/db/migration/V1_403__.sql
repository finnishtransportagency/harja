-- Tee tarkastukselle envelope

ALTER TABLE tarkastus ADD COLUMN envelope geometry;

CREATE INDEX tarkastus_envelope_idx ON tarkastus USING GIST (envelope);

CREATE OR REPLACE FUNCTION muodosta_tarkastuksen_envelope() RETURNS trigger AS $$
BEGIN
  IF NEW.sijainti IS NOT NULL THEN
    NEW.envelope := ST_Envelope(NEW.sijainti);
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_muodosta_tarkastuksen_envelope
  BEFORE INSERT OR UPDATE
  ON tarkastus
  FOR EACH ROW
  EXECUTE PROCEDURE muodosta_tarkastuksen_envelope();

UPDATE tarkastus SET envelope = ST_Envelope(sijainti);
