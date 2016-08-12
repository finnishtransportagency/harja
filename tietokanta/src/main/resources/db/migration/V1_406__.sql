-- Tee suljetulle tieosuudelle envelope. Käytetään valitsemaan kartalla näkyvät osuudet.

ALTER TABLE suljettu_tieosuus ADD COLUMN envelope geometry;

CREATE INDEX suljettu_tieosuus_envelope_idx ON suljettu_tieosuus USING GIST (envelope);

CREATE OR REPLACE FUNCTION muodosta_suljetun_tieosuuden_envelope() RETURNS trigger AS $$
BEGIN
  IF NEW.geometria IS NOT NULL THEN
    NEW.envelope := ST_Envelope(NEW.geometria);
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_muodosta_suljetun_tieosuuden_envelope
BEFORE INSERT OR UPDATE
ON suljettu_tieosuus
FOR EACH ROW
EXECUTE PROCEDURE muodosta_suljetun_tieosuuden_envelope();

UPDATE suljettu_tieosuus SET envelope = ST_Envelope(envelope);