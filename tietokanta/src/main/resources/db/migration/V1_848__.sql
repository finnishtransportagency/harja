CREATE TABLE tapahtumatyyppi (
                           id SERIAL PRIMARY KEY,
                           kanava TEXT UNIQUE NOT NULL,
                           nimi TEXT UNIQUE NOT NULL
);

CREATE TABLE tapahtuman_tiedot (
                                   id BIGSERIAL PRIMARY KEY,
                                   arvo TEXT NOT NULL,
                                   hash TEXT NOT NULL,
                                   kanava TEXT NOT NULL REFERENCES tapahtumatyyppi(kanava),
                                   palvelin TEXT NOT NULL,
                                   luotu TIMESTAMP NOT NULL
);

CREATE OR REPLACE FUNCTION esta_nimen_ja_kanavan_paivitys() RETURNS trigger AS $$
BEGIN
    IF (NEW.nimi != OLD.nimi OR NEW.kanava != OLD.kanava)
    THEN
        RETURN NULL;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_esta_nimen_ja_kanavan_paivitys
    BEFORE UPDATE
    ON tapahtumatyyppi
    FOR EACH ROW
EXECUTE PROCEDURE esta_nimen_ja_kanavan_paivitys();

CREATE OR REPLACE FUNCTION poista_vanhat_tapahtumat() RETURNS trigger AS $$
BEGIN
    DELETE FROM tapahtuman_tiedot
    WHERE luotu < NOW() - interval '10 minutes';
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION esta_tapahtumien_muokkaus_ja_ennenaikainen_poisto() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'DELETE' AND NOW() - interval '10 minutes' >= OLD.luotu::TIMESTAMP) OR
       (TG_OP = 'UPDATE')
    THEN
        RETURN NULL;
    ELSE
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Ei välttämättä ideaalia hoitaa vanhdan datan
-- poistoa triggerin kautta, mutta toimiipi
CREATE TRIGGER tg_poista_vanhat_tapahtumat
    AFTER INSERT
    ON tapahtuman_tiedot
    FOR EACH STATEMENT
EXECUTE PROCEDURE poista_vanhat_tapahtumat();

CREATE TRIGGER tg_esta_tapahtumien_muokkaus_ja_poisto
    BEFORE UPDATE OR DELETE
    ON tapahtuman_tiedot
    FOR EACH ROW
EXECUTE PROCEDURE esta_tapahtumien_muokkaus_ja_ennenaikainen_poisto();

CREATE OR REPLACE FUNCTION julkaise_tapahtuma(_kanava TEXT, data_text TEXT, _hash TEXT, _palvelin TEXT) RETURNS bool AS
$$
DECLARE
    _sqlstate TEXT;
    _message  TEXT;
    _detail   TEXT;
    _hint     TEXT;
    _id INTEGER;
BEGIN
    INSERT INTO tapahtuman_tiedot (arvo, kanava, luotu, hash, palvelin)
    VALUES(data_text, _kanava, NOW(), _hash, _palvelin)
    RETURNING id
        INTO _id;

    PERFORM pg_notify(_kanava, _id::TEXT);

    RETURN true;

EXCEPTION
    WHEN others THEN
        GET STACKED DIAGNOSTICS _sqlstate = RETURNED_SQLSTATE,
            _message = MESSAGE_TEXT,
            _detail = PG_EXCEPTION_DETAIL,
            _hint = PG_EXCEPTION_HINT;
        RAISE NOTICE E'pg_notify käsittely aiheutti virheen %\nViesti: %\nYksityiskohdat: %\nVihje: %', _sqlstate, _message, _detail, _hint;
        RETURN false;
END;
$$ LANGUAGE plpgsql;
