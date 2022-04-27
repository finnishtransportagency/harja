-- Päivitetään edellisessä transaktiossa luodut materiaalityypit materiaalikoodeille
UPDATE materiaalikoodi SET materiaalityyppi  = 'erityisalue'::materiaalityyppi WHERE nimi = 'Erityisalueet CaCl2-liuos';
UPDATE materiaalikoodi SET materiaalityyppi  = 'erityisalue'::materiaalityyppi WHERE nimi = 'Erityisalueet NaCl';
UPDATE materiaalikoodi SET materiaalityyppi  = 'erityisalue'::materiaalityyppi WHERE nimi = 'Erityisalueet NaCl-liuos';
UPDATE materiaalikoodi SET materiaalityyppi  = 'talvisuola'::materiaalityyppi WHERE nimi = 'Hiekoitushiekan suola';
UPDATE materiaalikoodi SET materiaalityyppi  = 'formiaatti'::materiaalityyppi WHERE nimi = 'Kaliumformiaatti';
UPDATE materiaalikoodi SET materiaalityyppi  = 'formiaatti'::materiaalityyppi WHERE nimi = 'Natriumformiaatti';
UPDATE materiaalikoodi SET materiaalityyppi  = 'formiaatti'::materiaalityyppi WHERE nimi = 'Natriumformiaattiliuos';
UPDATE materiaalikoodi SET materiaalityyppi  = 'kesasuola'::materiaalityyppi WHERE nimi = 'Kesäsuola (pölynsidonta)';
UPDATE materiaalikoodi SET materiaalityyppi  = 'kesasuola'::materiaalityyppi WHERE nimi = 'Kesäsuola (sorateiden kevätkunnostus)';
UPDATE materiaalikoodi SET materiaalityyppi  = 'hiekoitushiekka'::materiaalityyppi WHERE nimi = 'Hiekoitushiekka';
UPDATE materiaalikoodi SET materiaalityyppi  = 'muu'::materiaalityyppi WHERE nimi = 'Jätteet kaatopaikalle';
UPDATE materiaalikoodi SET materiaalityyppi  = 'murske'::materiaalityyppi WHERE nimi = 'Murskeet';
UPDATE materiaalikoodi SET materiaalityyppi  = 'muu'::materiaalityyppi WHERE nimi = 'Rikkaruohojen torjunta-aineet';

-- Koska yllä olevat update lauseet muuttavat osan talvisuola materiaalityypeistä erityisalueiksi,
-- muutetaan triggerin toimintaa niin, että se hakee ne molemmat.
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

    IF (TG_OP = 'UPDATE') THEN
        DELETE FROM suolatoteuma_reittipiste WHERE toteuma=NEW.toteuma;
    END IF;

    FOREACH rp IN ARRAY NEW.reittipisteet LOOP
            FOREACH m IN ARRAY rp.materiaalit LOOP
                    IF suolamateriaalikoodit @> ARRAY[m.materiaalikoodi] THEN
                        pohjavesialue_tunnus := pisteen_pohjavesialue(rp.sijainti, 20);
                        INSERT INTO suolatoteuma_reittipiste (toteuma, aika, sijainti, materiaalikoodi, maara, pohjavesialue)
                        VALUES (NEW.toteuma, rp.aika, rp.sijainti, m.materiaalikoodi, m.maara, pohjavesialue_tunnus);
                    END IF;
                END LOOP;
        END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
