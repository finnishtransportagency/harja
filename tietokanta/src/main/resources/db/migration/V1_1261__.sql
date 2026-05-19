-- Triggerit, jotka päivittävät hoitokauden_alkuvuosi-sarakkeen toteuma_tehtava- ja toteuma_materiaali-tauluihin
-- kun niihin tehdään INSERT tai UPDATE. Hoitokauden alkuvuosi lasketaan toteuma.alkanut-arvon perusteella.

CREATE OR REPLACE FUNCTION paivita_hoitokauden_alkuvuosi_tehtavalle()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    hk_alkuvuosi INTEGER;
BEGIN
    SELECT CASE
               WHEN EXTRACT(MONTH FROM t.alkanut) >= 10
                   THEN EXTRACT(YEAR FROM t.alkanut)::INTEGER
               ELSE EXTRACT(YEAR FROM t.alkanut)::INTEGER - 1
           END
      INTO hk_alkuvuosi
      FROM toteuma t
     WHERE t.id = NEW.toteuma;

    IF hk_alkuvuosi IS NOT NULL THEN
        NEW.hoitokauden_alkuvuosi := hk_alkuvuosi;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION paivita_hoitokauden_alkuvuosi_materiaalille()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    hk_alkuvuosi INTEGER;
BEGIN
    SELECT CASE
               WHEN EXTRACT(MONTH FROM t.alkanut) >= 10
                   THEN EXTRACT(YEAR FROM t.alkanut)::INTEGER
               ELSE EXTRACT(YEAR FROM t.alkanut)::INTEGER - 1
           END
      INTO hk_alkuvuosi
      FROM toteuma t
     WHERE t.id = NEW.toteuma;

    IF hk_alkuvuosi IS NOT NULL THEN
        NEW.hoitokauden_alkuvuosi := hk_alkuvuosi;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_toteuma_tehtava_hoitokauden_alkuvuosi
    BEFORE INSERT OR UPDATE
    ON toteuma_tehtava
    FOR EACH ROW
EXECUTE FUNCTION paivita_hoitokauden_alkuvuosi_tehtavalle();

CREATE TRIGGER tg_toteuma_materiaali_hoitokauden_alkuvuosi
    BEFORE INSERT OR UPDATE
    ON toteuma_materiaali
    FOR EACH ROW
EXECUTE FUNCTION paivita_hoitokauden_alkuvuosi_materiaalille();

-- Triggeri toteuma-tauluun: kun alkanut muuttuu, päivitetään toteuma_tehtava ja toteuma_materiaali taulujen hoitokauden_alkuvuosi.
-- Päivitys tehdään vain, jos uusi hoitokausi eroaa vanhasta.

CREATE OR REPLACE FUNCTION paivita_hoitokauden_alkuvuosi_tehtava_ja_materiaalitauluista()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    uusi_hk INTEGER;
BEGIN
    uusi_hk := CASE
                   WHEN EXTRACT(MONTH FROM NEW.alkanut) >= 10
                       THEN EXTRACT(YEAR FROM NEW.alkanut)::INTEGER
                   ELSE EXTRACT(YEAR FROM NEW.alkanut)::INTEGER - 1
        END;

    UPDATE toteuma_tehtava
    SET hoitokauden_alkuvuosi = uusi_hk
    WHERE toteuma = NEW.id
      AND hoitokauden_alkuvuosi IS DISTINCT FROM uusi_hk;

    UPDATE toteuma_materiaali
    SET hoitokauden_alkuvuosi = uusi_hk
    WHERE toteuma = NEW.id
      AND hoitokauden_alkuvuosi IS DISTINCT FROM uusi_hk;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_toteuma_alkanut_paivitta_tehtavamateriaali_hoitokausi
    AFTER UPDATE OF alkanut
    ON toteuma
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
EXECUTE FUNCTION paivita_hoitokauden_alkuvuosi_tehtava_ja_materiaalitauluista();
