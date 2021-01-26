-- Tässä tehdään edelliseen nähden vain sellainen muutos, että tapahtuman tietoja ei poisteta triggerissä, jos
-- uusi data ei ole samalta serveriltä kuin aikaisempi.
DROP TRIGGER tg_poista_vanhat_tapahtumat ON tapahtuman_tiedot;

CREATE OR REPLACE FUNCTION poista_vanhat_tapahtumat() RETURNS TRIGGER AS $$
BEGIN
    DELETE
      FROM tapahtuman_tiedot
     WHERE luotu < NOW() - INTERVAL '10 minutes'
       AND ((kanava = new.kanava
         AND palvelin = new.palvelin)
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

