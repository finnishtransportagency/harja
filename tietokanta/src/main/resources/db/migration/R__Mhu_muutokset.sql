-- Kaikki muutokset-tietorakenteen historian tallennukseen liittyvät funktiot ja triggerit

CREATE OR REPLACE FUNCTION paivita_mhu_muutos_historia()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO mhu_muutos_historia
    VALUES (OLD.*);

    UPDATE mhu_muutos_historia
       SET validi_aikana = TSTZRANGE(LOWER(OLD.validi_aikana), CURRENT_TIMESTAMP)
     WHERE id = OLD.id
       AND versio = OLD.versio;

    NEW.validi_aikana = TSTZRANGE(CURRENT_TIMESTAMP, 'infinity');
    NEW.versio = OLD.versio + 1;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER mhu_muutos_historia_trigger
    BEFORE UPDATE OR DELETE
    ON mhu_muutos
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_historia();


--

CREATE OR REPLACE FUNCTION paivita_mhu_muutos_liite_historia()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO mhu_muutos_liite_historia
    VALUES (OLD.*);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER mhu_muutos_liite_historia_trigger
    BEFORE UPDATE OR DELETE
    ON mhu_muutos_liite
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_liite_historia();

--

CREATE OR REPLACE FUNCTION paivita_mhu_muutos_kustannusvaikutus_historia()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        INSERT INTO mhu_muutos_kustannusvaikutus_historia
        VALUES (OLD.*);
    END IF;

    IF TG_OP = 'UPDATE' THEN
        NEW.versio := OLD.versio + 1;
        RETURN NEW;
    ELSE
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER paivita_mhu_muutos_kustannusvaikutus_historia_trigger
    BEFORE UPDATE OR DELETE
    ON mhu_muutos_kustannusvaikutus
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_kustannusvaikutus_historia();

--

CREATE OR REPLACE FUNCTION paivita_mhu_muutos_tehtava_ja_maaraluettelo_historia()
    RETURNS TRIGGER AS
$$
BEGIN
    INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo_historia
    VALUES (OLD.*);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER mhu_muutos_tehtava_ja_maaraluettelo_historia_trigger
    BEFORE UPDATE OR DELETE
    ON mhu_muutos_tehtava_ja_maaraluettelo
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_tehtava_ja_maaraluettelo_historia();
