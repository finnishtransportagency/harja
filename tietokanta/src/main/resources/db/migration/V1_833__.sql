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

CREATE OR REPLACE FUNCTION julkaise_tapahtuma(kanava_ TEXT, data JSONB) RETURNS bool AS
$$
DECLARE
    _sqlstate TEXT;
    message  TEXT;
    detail   TEXT;
    hint     TEXT;
BEGIN
    UPDATE tapahtuma
    SET uusin_arvo=data
    WHERE kanava = kanava_;

    SELECT pg_notify(kanava_, data);
    RETURN true;

EXCEPTION
    WHEN others THEN
        GET STACKED DIAGNOSTICS _sqlstate = RETURNED_SQLSTATE,
            message = MESSAGE_TEXT,
            detail = PG_EXCEPTION_DETAIL,
            hint = PG_EXCEPTION_HINT;
        RAISE NOTICE E'pg_notify käsittely aiheutti virheen %\nViesti: %\nYksityiskohdat: %\nVihje: %', _sqlstate, message, detail, hint;
        RETURN false;
END;
$$ LANGUAGE plpgsql;