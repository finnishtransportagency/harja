DROP TRIGGER IF EXISTS tg_poista_muistetut_laskutusyht_kohdistetut ON lasku;

alter table if exists lasku rename to kulu;

alter table if exists lasku_kohdistus rename to kulu_kohdistus;

alter table if exists lasku_liite rename to kulu_liite;

alter table if exists kulu_kohdistus rename column lasku to kulu;
alter table if exists kulu_liite rename column lasku to kulu;

-- Nimetty uudelleen lasku -> kulu, lasku_kohdistus -> kulu_kohdistus
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_kohdistetut() RETURNS trigger AS
$$
DECLARE
    alku    DATE;
    loppu   DATE;
    ur      INTEGER;
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

    tpi_idt := ARRAY(SELECT toimenpideinstanssi FROM kulu_kohdistus WHERE kulu = NEW.id);

    SELECT INTO ur urakka
    FROM toimenpideinstanssi tpi
    WHERE ARRAY [tpi.id]::INTEGER[] <@ tpi_idt;

    loppu := alku + interval '31 days';

    RAISE NOTICE 'Poistetaan urakan % muistetut laskutusyhteenvedot % - %. Syy: kohdistettavien kulujen muutos.', ur, alku, loppu;
    DELETE
    FROM laskutusyhteenveto_cache
    WHERE urakka = ur
      AND alkupvm >= alku
      AND loppupvm <= loppu;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_kohdistetut
    AFTER INSERT OR UPDATE OR DELETE
    ON kulu
    FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_kohdistetut();
