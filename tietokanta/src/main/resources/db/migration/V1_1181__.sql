CREATE TABLE mhu_muutos_kulu
(
    versio INTEGER DEFAULT 1, -- jokainen tallennus tallentaa täyden version muutoksesta, ml. alitaulut joissa on tietoa
    muutos INTEGER REFERENCES mhu_muutos(id),
    kulu INTEGER REFERENCES kulu(id),

    UNIQUE (versio, muutos, kulu)
);
CREATE TABLE mhu_muutos_kulu_historia () INHERITS (mhu_muutos_kulu);

CREATE FUNCTION paivita_mhu_muutos_kulu_historia()
    RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO mhu_muutos_kulu_historia
    VALUES (OLD.*);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER mhu_muutos_kulu_historia_trigger
    BEFORE UPDATE OR DELETE ON mhu_muutos_kulu
    FOR EACH ROW
EXECUTE FUNCTION paivita_mhu_muutos_kulu_historia();
