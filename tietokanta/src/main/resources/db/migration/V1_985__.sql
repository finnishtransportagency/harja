-- Lisätään triggeriin mukaan myös formiaattimateriaalikoodit sekä rajoitusalue_id
CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS $$
DECLARE
    m reittipiste_materiaali;
    rp reittipistedata;
    suolamateriaalikoodit INTEGER[];
    pohjavesialue_tunnus VARCHAR;
    rajoitusalue_id INTEGER;
BEGIN
    SELECT array_agg(id) FROM materiaalikoodi
                              -- Muuttuneet rivit
    WHERE materiaalityyppi IN ('talvisuola','erityisalue','formiaatti') INTO suolamateriaalikoodit;
    -- Muuttuneet rivit päättyy

    IF (TG_OP = 'UPDATE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma=NEW.toteuma;
    END IF;

    FOREACH rp IN ARRAY NEW.reittipisteet LOOP
            FOREACH m IN ARRAY rp.materiaalit LOOP
                    IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
                        pohjavesialue_tunnus := pisteen_pohjavesialue(rp.sijainti, 20);
                        rajoitusalue_id := pisteen_rajoitusalue(rp.sijainti, 20, NEW.toteuma);
                        -- Muuttuneet rivit
                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, rajoitusalue_id)
                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tunnus, rajoitusalue_id);
                        -- Muuttuneet rivit päättyy
                    END IF;
                END LOOP;
        END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
