-- Lisätään poistettu-sarake, jotta taulu päivitetään myös poistettaessa toteuma
ALTER TABLE toteuman_reittipisteet
 ADD COLUMN poistettu BOOLEAN;

-- Reittipisteiden poistamista ei ole aiemmin käsitelty.
-- Toteuman reittipisteitä ei yleensä poisteta deletellä, mutta jos niin joskus tehdään, täytyy suolatoteumien reittipisteiden tilanne myös päivittää.
-- Jos poistettu = true tai jos operaatio on DELETE, poistetaan suolatoteuman reittipisteet, eikä päätellä niitä uudelleen.
CREATE OR REPLACE FUNCTION toteuman_reittipisteet_trigger_fn() RETURNS TRIGGER AS $$
DECLARE
    m reittipiste_materiaali;
    rp reittipistedata;
    suolamateriaalikoodit INTEGER[];
    pohjavesialue_tunnus VARCHAR;
BEGIN
    SELECT array_agg(id) FROM materiaalikoodi
                              -- Muuttunut koodi
    WHERE materiaalityyppi IN ('talvisuola','erityisalue') INTO suolamateriaalikoodit;
    -- Muuttunut koodi päättyy

    IF (TG_OP = 'UPDATE' OR TG_OP = 'DELETE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma=NEW.toteuma;
    END IF;

    RAISE NOTICE 'NEW.poistettu %', NEW.poistettu;

    IF NOT (TG_OP = 'DELETE') AND NEW.poistettu = FALSE THEN
        RAISE NOTICE 'Päivitetään';
        FOREACH rp IN ARRAY NEW.reittipisteet
            LOOP
                FOREACH m IN ARRAY rp.materiaalit
                    LOOP
                        IF suolamateriaalikoodit @> ARRAY [m.materiaalikoodi] THEN
                            pohjavesialue_tunnus := pisteen_pohjavesialue(rp.sijainti, 20);
                            INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue)
                            VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara,
                                    pohjavesialue_tunnus);
                        END IF;
                    END LOOP;
            END LOOP;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
