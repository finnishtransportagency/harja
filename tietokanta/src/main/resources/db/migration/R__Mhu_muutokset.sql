DROP TRIGGER IF EXISTS mhu_muutos_historia_trigger ON mhu_muutos;

CREATE OR REPLACE FUNCTION paivita_mhu_muutos_historia()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO mhu_muutos_historia
    VALUES (OLD.*);

    UPDATE mhu_muutos_historia
       SET validi_aikana = TSTZRANGE(LOWER(OLD.validi_aikana), CURRENT_TIMESTAMP)
     WHERE id = OLD.id
       AND versio = OLD.versio;

    NEW.validi_aikana = TSTZRANGE(CURRENT_TIMESTAMP, 'infinity');

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER mhu_muutos_historia_trigger
    BEFORE UPDATE OR DELETE ON mhu_muutos
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_historia();
