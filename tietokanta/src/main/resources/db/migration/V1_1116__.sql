ALTER TABLE kan_kohteenosa
    ADD COLUMN oletustoimenpide liikennetapahtuma_toimenpidetyyppi;

UPDATE kan_kohteenosa
SET oletustoimenpide = 'ei-avausta'
WHERE tyyppi IN ('silta', 'rautatiesilta');

UPDATE kan_kohteenosa
SET oletustoimenpide = 'sulutus'
WHERE tyyppi = 'sulku';

CREATE OR REPLACE FUNCTION aseta_kan_kohteenosa_oletustoimenpide() RETURNS TRIGGER
AS
$$
BEGIN
    NEW.oletustoimenpide = CASE NEW.tyyppi
                               WHEN 'sulku'::kohteenosa_tyyppi THEN 'sulutus'::liikennetapahtuma_toimenpidetyyppi
                               ELSE 'ei-avausta'::liikennetapahtuma_toimenpidetyyppi
        END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_kan_kohteenosa_oletustoimenpide
    BEFORE INSERT
    ON kan_kohteenosa
    FOR EACH ROW
    WHEN (new.oletustoimenpide IS NULL AND new.tyyppi IS NOT NULL)
EXECUTE FUNCTION aseta_kan_kohteenosa_oletustoimenpide();
