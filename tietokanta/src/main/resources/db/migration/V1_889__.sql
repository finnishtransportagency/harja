DROP TRIGGER tg_update_paivita_toteutuneet_kustannukset_kat ON kustannusarvioitu_tyo;
CREATE TRIGGER tg_update_paivita_toteutuneet_kustannukset_kat
    AFTER INSERT OR UPDATE OR DELETE
    ON kustannusarvioitu_tyo
    FOR EACH ROW
EXECUTE PROCEDURE paivita_toteutuneet_kustannukset_kat();


DROP TRIGGER tg_update_paivita_toteutuneet_kustannukset_jjh ON johto_ja_hallintokorvaus;
CREATE TRIGGER tg_update_paivita_toteutuneet_kustannukset_jjh
    AFTER INSERT OR UPDATE OR DELETE
    ON johto_ja_hallintokorvaus
    FOR EACH ROW
EXECUTE PROCEDURE paivita_toteutuneet_kustannukset_jjh();
