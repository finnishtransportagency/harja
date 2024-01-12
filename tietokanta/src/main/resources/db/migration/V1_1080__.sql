-- Otetaan triggerissä huomioon myös poistettava reittipiste.
-- Käytännössä toteuman_reittipisteet ei koskaan päivitetä, toteuman päivittyessä vanhat reittipisteet poistetaan
-- ja uudet luodaan tilalle.
CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS
$$
DECLARE
    m                     reittipiste_materiaali;
    rp                    reittipistedata;
    suolamateriaalikoodit INTEGER[];
    pohjavesialue_tunnus  VARCHAR;
    rajoitusalue_id       INTEGER;
BEGIN
    SELECT ARRAY_AGG(id)
    FROM materiaalikoodi
    WHERE materiaalityyppi IN ('talvisuola', 'erityisalue', 'formiaatti')
    INTO suolamateriaalikoodit;

    -- Muutos edelliseen versioon: Lisätty DELETE-operaation tarkistus
    IF (TG_OP = 'UPDATE' OR TG_OP = 'DELETE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma = OLD.toteuma;
    END IF;

    -- Muutos edelliseen versioon: Lisätty DELETE-operaation tarkistus
    IF (TG_OP != 'DELETE') THEN
        FOREACH rp IN ARRAY NEW.reittipisteet
            LOOP
                FOREACH m IN ARRAY rp.materiaalit
                    LOOP
                        IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                            pohjavesialue_tunnus := pisteen_pohjavesialue(rp.sijainti, 20);
                            rajoitusalue_id := pisteen_rajoitusalue(rp.sijainti, 20, NEW.toteuma);
                            INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara,
                                                                  pohjavesialue, rajoitusalue_id)
                            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tunnus,
                                    rajoitusalue_id);
                        END IF;
                    END LOOP;
            END LOOP;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS toteuman_reittipisteet_trigger ON toteuman_reittipisteet;

-- Muutos aiempaan: Trigger laukeaa myös deletestä, ennen vain insertistä ja updatesta.
CREATE TRIGGER toteuman_reittipisteet_trigger
    AFTER INSERT OR UPDATE OR DELETE
    ON toteuman_reittipisteet
    FOR EACH ROW
EXECUTE PROCEDURE toteuman_reittipisteet_trigger_fn();
