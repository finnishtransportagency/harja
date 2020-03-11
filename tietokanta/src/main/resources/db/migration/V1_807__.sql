-- Kun kiinteähintaisten suunniteltujen hankintojen luvut muuttuvat, poista muistetut laskutusyhteenvedolta
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_kiinteat() RETURNS trigger AS $$
DECLARE
    alku DATE;
    loppu DATE;
    ur INTEGER;
    tpi_id INTEGER;
BEGIN
    IF TG_OP != 'DELETE' THEN
        alku := NEW.kuukausi;
        tpi_id := NEW.toimenpideinstanssi;
    ELSE
        alku := OLD.kuukausi;
        tpi_id := OLD.toimenpideinstanssi;
    END IF;

    IF alku IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT INTO ur urakka
    FROM toimenpideinstanssi tpi
    WHERE tpi.id = tpi_id;

    loppu := alku + interval '31 days';

    RAISE NOTICE 'Poistetaan urakan % muistetut laskutusyhteenvedot % - %. Syy: kiinteähintaisten suunniteltujen töiden muutos.', ur, alku, loppu;
    DELETE FROM laskutusyhteenveto_cache
    WHERE urakka = ur
      AND alkupvm >= alku
      AND loppupvm <= loppu;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_kiinteat
    AFTER INSERT OR UPDATE OR DELETE
    ON kiinteahintainen_tyo
    FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_kiinteat();

-- Kun kustannusarvioitujen, määrämitattavien töiden kirjatut kulut muuttuvat, poista muistetut laskutusyhteenvedolta
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_kustannusarvioidut() RETURNS trigger AS $$
DECLARE
    alku DATE;
    loppu DATE;
    ur INTEGER;
    tpi_idt INTEGER[];
BEGIN
    IF TG_OP != 'DELETE' THEN
        alku := date_trunc('month', NEW.erapaiva);
    ELSE
        alku := date_trunc('month', OLD.erapaiva);
    END IF;

    IF alku IS NULL THEN
        RETURN NULL;
    END IF;

    tpi_idt := ARRAY(SELECT toimenpideinstanssi FROM lasku_kohdistus WHERE lasku = NEW.id);

    SELECT INTO ur urakka
    FROM toimenpideinstanssi tpi
    WHERE ARRAY[tpi.id]::INTEGER[] <@ tpi_idt;

    loppu := alku + interval '31 days';

    RAISE NOTICE 'Poistetaan urakan % muistetut laskutusyhteenvedot % - %. Syy: kustannusarvioitujen kulujen muutos.', ur, alku, loppu;
    DELETE FROM laskutusyhteenveto_cache
    WHERE urakka = ur
      AND alkupvm >= alku
      AND loppupvm <= loppu;
    RETURN NULL;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_kiinteat
    AFTER INSERT OR UPDATE OR DELETE
    ON lasku
    FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_kustannusarvioidut();


