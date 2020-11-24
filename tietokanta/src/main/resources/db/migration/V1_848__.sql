CREATE TABLE tapahtuma (
                           kanava TEXT PRIMARY KEY,
                           nimi TEXT UNIQUE NOT NULL,
                           uusin_arvo INT,
                           palvelimien_uusimmat_arvot JSONB
);

CREATE TABLE tapahtuman_tiedot (
                                   id SERIAL PRIMARY KEY,
                                   arvo JSONB NOT NULL,
                                   hash TEXT NOT NULL,
                                   kanava TEXT REFERENCES tapahtuma(kanava),
                                   luotu TIMESTAMP
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
    ON tapahtuma
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

CREATE OR REPLACE FUNCTION resetoi_tapahtuman_tiedot_id_serial() RETURNS trigger AS $$
BEGIN
    IF(currval('tapahtuman_tiedot_id_seq') = 2147483647)
    THEN
        PERFORM setval('tapahtuman_tiedot_id_seq', 1);
    END IF;
    RETURN NEW;
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

CREATE TRIGGER tg_resetoi_id_tapahtumien_tiedot_taulukosta
    AFTER INSERT
    ON tapahtuman_tiedot
    FOR EACH STATEMENT
EXECUTE PROCEDURE resetoi_tapahtuman_tiedot_id_serial();

CREATE OR REPLACE FUNCTION julkaise_tapahtuma(_kanava TEXT, data JSONB, _hash TEXT) RETURNS bool AS
$$
DECLARE
    _sqlstate TEXT;
    message  TEXT;
    detail   TEXT;
    hint     TEXT;
    palvelin TEXT;
    _id INTEGER;

    palvelin_index INT = 0;
    el JSONB;
BEGIN

    IF (jsonb_typeof(data) = 'array')
    THEN
        FOR el IN SELECT * FROM jsonb_array_elements(data)
            LOOP
                palvelin_index = palvelin_index + 1;
                IF (el::TEXT ILIKE('%:palvelin%'))
                THEN
                    EXIT;
                END IF;
            END LOOP;

        SELECT data->>palvelin_index
        INTO palvelin;
    END IF;

    INSERT INTO tapahtuman_tiedot (arvo, kanava, luotu, hash)
    VALUES(data, _kanava, NOW(), _hash)
    RETURNING id
        INTO _id;

    UPDATE tapahtuma
    SET uusin_arvo=_id
    WHERE kanava=_kanava;

    IF palvelin IS NOT NULL
    THEN
        UPDATE tapahtuma
        SET palvelimien_uusimmat_arvot=(SELECT jsonb_set(CASE WHEN palvelimien_uusimmat_arvot IS NULL
                                                                  THEN '{}'::JSONB
                                                              ELSE palvelimien_uusimmat_arvot
                                                             END,
                                                         ARRAY[palvelin]::TEXT[],
                                                         _id)
                                        FROM tapahtuma
                                        WHERE kanava=_kanava)
        WHERE kanava=_kanava;
    END IF;

    PERFORM pg_notify(_kanava, _id::TEXT);

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