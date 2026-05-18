-- Triggeri, joka päivittää hoitokauden_alkuvuosi-sarakkeen toteuma_tehtava- ja toteuma_materiaali-tauluihin
-- kun toteuma-tauluun tehdään INSERT tai UPDATE.

CREATE OR REPLACE FUNCTION paivita_hoitokauden_alkuvuosi_toteumalle()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    hk_alkuvuosi INTEGER;
BEGIN
    hk_alkuvuosi := CASE
                        WHEN EXTRACT(MONTH FROM NEW.alkanut) >= 10
                            THEN EXTRACT(YEAR FROM NEW.alkanut)::INTEGER
                        ELSE EXTRACT(YEAR FROM NEW.alkanut)::INTEGER - 1
                    END;

    UPDATE toteuma_tehtava
       SET hoitokauden_alkuvuosi = hk_alkuvuosi
     WHERE toteuma = NEW.id
       AND hoitokauden_alkuvuosi IS DISTINCT FROM hk_alkuvuosi;

    UPDATE toteuma_materiaali
       SET hoitokauden_alkuvuosi = hk_alkuvuosi
     WHERE toteuma = NEW.id
       AND hoitokauden_alkuvuosi IS DISTINCT FROM hk_alkuvuosi;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_toteuma_hoitokauden_alkuvuosi
    AFTER INSERT OR UPDATE OF alkanut
    ON toteuma
    FOR EACH ROW
EXECUTE FUNCTION paivita_hoitokauden_alkuvuosi_toteumalle();

