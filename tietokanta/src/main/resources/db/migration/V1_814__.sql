-- Kiinteähintainen suunniteltu työ ei päädykään suoraan maksueriin, eikä se niin ollen vaikuta laskutusyhteenvetoon.
DROP TRIGGER tg_poista_muistetut_laskutusyht_kiinteat ON kiinteahintainen_tyo;
DROP FUNCTION poista_muistetut_laskutusyht_kiinteat();

-- Hoidon johdon kustannusarvioidut työt vaikuttavat suunniteltuinakin maksuerään ja laskutusyhteenvetoon
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_kustannusarv() RETURNS trigger AS
$$
DECLARE
    alku   DATE;
    loppu  DATE;
    ur     INTEGER;
    tpi_id INTEGER;
BEGIN
    IF TG_OP != 'DELETE' THEN
        alku := make_date(NEW.vuosi, NEW.kuukausi, 1);
        tpi_id := NEW.toimenpideinstanssi;
    ELSE
        alku := make_date(OLD.vuosi, OLD.kuukausi, 1);
        tpi_id := OLD.toimenpideinstanssi;
    END IF;

    IF alku IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT INTO ur urakka
    FROM toimenpideinstanssi tpi
    WHERE tpi.id = tpi_id;

    loppu := alku + interval '31 days';

    RAISE NOTICE 'Poistetaan urakan % muistetut laskutusyhteenvedot % - %. Syy: kustannusarvioituihin töihin suunniteltujen hoidon johdon töiden muutos.', ur, alku, loppu;
    DELETE
    FROM laskutusyhteenveto_cache
    WHERE urakka = ur
      AND alkupvm >= alku
      AND loppupvm <= loppu;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_kustannusarv
    AFTER INSERT OR UPDATE OR DELETE
    ON kustannusarvioitu_tyo
    FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_kustannusarv();


-- Johto- ja hallintokorvaukset päivittyvät maksuerälle automaattisesti, täytyy putsata laskutusyhteenveto
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_johtojahallinto() RETURNS trigger AS
$$
DECLARE
    alku  DATE;
    loppu DATE;
    ur    INTEGER;
BEGIN
    IF TG_OP != 'DELETE' THEN
        alku := make_date(NEW.vuosi, NEW.kuukausi, 1);
        ur := NEW."urakka-id";
    ELSE
        alku := make_date(OLD.vuosi, OLD.kuukausi, 1);
        ur := OLD."urakka-id";
    END IF;

    IF alku IS NULL THEN
        RETURN NULL;
    END IF;

    loppu := alku + interval '31 days';

    RAISE NOTICE 'Poistetaan urakan % muistetut laskutusyhteenvedot % - %. Syy: johto- ja hallintokorvauksiin suunniteltujen töiden muutos.', ur, alku, loppu;
    DELETE
    FROM laskutusyhteenveto_cache
    WHERE urakka = ur
      AND alkupvm >= alku
      AND loppupvm <= loppu;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql

CREATE TRIGGER tg_poista_muistetut_laskutusyht_johtojahallinto
    AFTER INSERT OR UPDATE OR DELETE
    ON johto_ja_hallintokorvaus
    FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_johtojahallinto();

-- Muutetaan triggerin ja proseduurin nimi
DROP TRIGGER tg_poista_muistetut_laskutusyht_kiinteat ON lasku;
DROP FUNCTION poista_muistetut_laskutusyht_kustannusarvioidut();

-- Kun kohdistetut kulut muuttuvat, poista muistetut laskutusyhteenvedolta
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

    tpi_idt := ARRAY(SELECT toimenpideinstanssi FROM lasku_kohdistus WHERE lasku = NEW.id);

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
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_kohdistetut
    AFTER INSERT OR UPDATE OR DELETE
    ON lasku
    FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_kohdistetut();


