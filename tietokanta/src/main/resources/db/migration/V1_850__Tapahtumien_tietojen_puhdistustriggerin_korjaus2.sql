-- Vanhassa puhdistustriggerissä poistetaan kaikki yli 10 min vanhat tapahtumat. Tämä johtaa ongelmiin palvelinten uudelleenkäynnistyksessä,
-- Tällöin uudelleenkäynnistettävä palvelin ei saa tietoa muiden palvelinten tilasta, ja näyttää virheellisesti virhetilan.
DROP TRIGGER tg_poista_vanhat_tapahtumat ON tapahtuman_tiedot;

-- Säilytetään saman kanavan viimeisin viesti. Tällöin tieto esim. app1 käynnistyksestä jää kantaan, mikäli app2
-- käynnistetään uudelleen yli 10min päästä app1 käynnistymisestä.
-- TODO: Pitääkö urakan tapahtumat ja ilmoitusten viestit poistaa kanavasta huolimatta? Niiden viimeiset viestit jäävät kummittelemaan tämän muutoksen myötä.
CREATE OR REPLACE FUNCTION poista_vanhat_tapahtumat() RETURNS trigger AS $$
BEGIN
    DELETE FROM tapahtuman_tiedot
     WHERE luotu < NOW() - interval '11 minutes' AND kanava = NEW.kanava;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_vanhat_tapahtumat
    AFTER INSERT
    ON tapahtuman_tiedot
    FOR EACH ROW
EXECUTE PROCEDURE poista_vanhat_tapahtumat();

