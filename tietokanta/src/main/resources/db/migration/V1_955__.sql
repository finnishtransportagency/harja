-- Lisätään suolatoteuma_reittipiste -tauluun mahdollisuus lisätä, että mille rajoitusalueelle piste kuuluu
ALTER TABLE suolatoteuma_reittipiste ADD rajoitusalue_id integer;
CREATE INDEX suolatoteuma_rajoitusalue_id_aika_idx ON suolatoteuma_reittipiste (rajoitusalue_id, aika);

-- Luodaan funktio, jolla päätellään mihin rajoitusalueeseen piste osuu
CREATE OR REPLACE FUNCTION pisteen_rajoitusalue(piste POINT, threshold INTEGER, toteuma_id INTEGER) RETURNS INTEGER AS $$
DECLARE
    rajoitusalue_id INTEGER;
BEGIN
    SELECT id FROM rajoitusalue r
     WHERE r.urakka_id = (select t.urakka from toteuma t where t.id = toteuma_id)
       AND ST_DWithin(r.sijainti, piste::geometry, threshold)
     ORDER BY ST_Distance(r.sijainti, piste::geometry)
    LIMIT 1
    INTO rajoitusalue_id;
    RETURN rajoitusalue_id;
END;
$$ LANGUAGE plpgsql;

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
                        -- Muuttuneet rivit
                        rajoitusalue_id := pisteen_rajoitusalue(rp.sijainti, 20, NEW.toteuma);
                        -- Muuttuneet rivit päättyy
                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue)
                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tunnus);
                    END IF;
                END LOOP;
        END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Laske hoitokauden alkuvuoden perusteella urakalle suolatoteuman_reittipisteet uusiksi formiaatin kanssa
-- Tätä käytetään, kun rajoitusaluetta muokataan
CREATE OR REPLACE FUNCTION paivita_suolatoteumat_urakalle(urakka_id INTEGER, alkupvm DATE, loppupvm DATE)
    RETURNS VOID AS $$
DECLARE

    rivi RECORD;
    m reittipiste_materiaali;
    rp reittipistedata;
    suolamateriaalikoodit INTEGER[];
    pohjavesialue_tunnus VARCHAR;
    rajoitusalueid INTEGER;

BEGIN
    SELECT array_agg(id) FROM materiaalikoodi
    WHERE materiaalityyppi IN ('talvisuola','erityisalue','formiaatti') INTO suolamateriaalikoodit;

    RAISE NOTICE 'Aloitetaan deletointi';
    -- Poistetaan kaikki urakan suolatoteuma_reittipisteet ja lasketaan ne uusiksi
    DELETE FROM suolatoteuma_reittipiste sr WHERE sr.toteuma IN (SELECT t.id FROM toteuma t
                                                                             WHERE t.urakka = urakka_id
                                                                               AND t.alkanut BETWEEN alkupvm AND loppupvm);
    RAISE NOTICE 'Lopetetaan deletointi';

    FOR rivi IN SELECT tr.toteuma, tr.luotu, tr.reittipisteet
                FROM toteuman_reittipisteet tr join toteuma t on tr.toteuma = t.id
                                                                     AND t.urakka = urakka_id
                                                                     AND t.alkanut BETWEEN alkupvm AND loppupvm
        LOOP
            --RAISE NOTICE 'Käsitellään rivi';
            FOREACH rp IN ARRAY rivi.reittipisteet LOOP
                    FOREACH m IN ARRAY rp.materiaalit LOOP
                            IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
                                pohjavesialue_tunnus := pisteen_pohjavesialue(rp.sijainti, 20);
                                -- Muuttuneet rivit
                                rajoitusalueid := pisteen_rajoitusalue(rp.sijainti, 20, rivi.toteuma);

                                INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue, rajoitusalue_id)
                                VALUES (rivi.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tunnus, rajoitusalueid);
                                -- Muuttuneet rivit päättyy
                            END IF;
                        END LOOP;
                END LOOP;
        END LOOP;
END;
$$ LANGUAGE plpgsql;
