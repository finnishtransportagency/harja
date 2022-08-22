-- Vanhassa puhdistustriggerissä poistetaan kaikki yli 10 min vanhat tapahtumat. Tämä johtaa ongelmiin palvelinten uudelleenkäynnistyksessä,
-- Tällöin uudelleenkäynnistettävä palvelin ei saa tietoa muiden palvelinten tilasta, ja näyttää virheellisesti virhetilan.
DROP TRIGGER tg_poista_vanhat_tapahtumat ON tapahtuman_tiedot;

-- Säilytetään saman kanavan viimeisin viesti. Tällöin tieto esim. app1 käynnistyksestä jää kantaan, mikäli app2
-- käynnistetään uudelleen yli 10min päästä app1 käynnistymisestä.
CREATE OR REPLACE FUNCTION poista_vanhat_tapahtumat() RETURNS TRIGGER AS $$
BEGIN
    DELETE
      FROM tapahtuman_tiedot
     WHERE luotu < NOW() - INTERVAL '10 minutes'
       AND (kanava = new.kanava
         OR kanava IN (SELECT tt.kanava
                         FROM tapahtuman_tiedot tt
                                  JOIN tapahtumatyyppi t ON t.kanava = tt.kanava
                        WHERE t.nimi ILIKE 'urakan_%'
                           OR t.nimi ILIKE 'ilmoitus_%'));
    RETURN new;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_vanhat_tapahtumat
    AFTER INSERT
    ON tapahtuman_tiedot
    FOR EACH ROW
EXECUTE PROCEDURE poista_vanhat_tapahtumat();

