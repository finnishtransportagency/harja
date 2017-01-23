CREATE TRIGGER tg_poista_muistetut_laskutusyht_erilliskustannus
AFTER INSERT OR UPDATE
        ON erilliskustannus
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_erilliskustannus();