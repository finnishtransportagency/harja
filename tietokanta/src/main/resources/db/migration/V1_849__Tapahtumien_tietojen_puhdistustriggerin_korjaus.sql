-- Vanhassa triggerissä if-lauseke oli väärin päin. Pudotetaan vanha ja luodaan uusi korjattuna.

DROP TRIGGER IF EXISTS tg_esta_tapahtumien_muokkaus_ja_poisto ON tapahtuman_tiedot;

CREATE OR REPLACE FUNCTION esta_tapahtumien_muokkaus_ja_ennenaikainen_poisto() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'DELETE' AND NOW() - interval '10 minutes' >= OLD.luotu::TIMESTAMP) OR
       (TG_OP = 'UPDATE')
    THEN
        RETURN OLD;
    ELSE
        RETURN NULL;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_esta_tapahtumien_muokkaus_ja_poisto
    BEFORE UPDATE OR DELETE
    ON tapahtuman_tiedot
    FOR EACH ROW
EXECUTE PROCEDURE esta_tapahtumien_muokkaus_ja_ennenaikainen_poisto();

-- Poistetaan samalla kaikki yli 10 minuuttia vanhat tapahtuman tiedot.
-- Jatkossa olemassa olevan poista_vanhat_tapahtumat() - triggerin pitäisi hoitaa tämä.

DELETE FROM tapahtuman_tiedot WHERE luotu < NOW() - interval '10 minutes';
