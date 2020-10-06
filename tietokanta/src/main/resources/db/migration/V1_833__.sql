CREATE TABLE tapahtuma (
   nimi TEXT PRIMARY KEY,
   kanava TEXT UNIQUE NOT NULL,
   uusin_arvo JSONB
);

CREATE OR REPLACE FUNCTION esta_nimen_ja_kanavan_paivitys() RETURNS trigger AS $$
BEGIN
  IF (NEW.nimi != OLD.nimi AND NEW.kanava != OLD.kanava)
  THEN
    RETURN NULL;
  ELSE
    RETURN NEW;
  END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER esta_nimen_ja_kanavan_paivitys
BEFORE UPDATE
  ON tapahtuma
FOR EACH ROW
EXECUTE PROCEDURE esta_nimen_ja_kanavan_paivitys();

CREATE OR REPLACE FUNCTION julkaise_tapahtuma(kanava_ TEXT, data JSONB) RETURNS void AS $$
BEGIN
  UPDATE tapahtuma
  SET uusin_arvo=data
  WHERE kanava=kanava_;

  SELECT pg_notify(kanava_, data);
END;
$$ LANGUAGE plpgsql;