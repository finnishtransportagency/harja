<<<<<<< HEAD
CREATE TYPE laheinen_osoiterivi AS (
 tie INTEGER,      -- TR tie
 osa INTEGER,      -- TR osa
 etaisyys INTEGER, -- TR etäisyys tieosan alusta
 ajorata INTEGER,  -- TR ajorata
 d NUMERIC 	   -- ST_Distance annetusta pisteesta
);
=======
-- Jos erilliskustannuksia muokataan, poistetaan hoitokauden laskutusyhteenvedot
CREATE OR REPLACE FUNCTION poista_muistetut_laskutusyht_erilliskustannus() RETURNS trigger AS $$
BEGIN
        PERFORM poista_hoitokauden_muistetut_laskutusyht(NEW.urakka, NEW.pvm::DATE);
        RETURN NULL;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER tg_poista_muistetut_laskutusyht_erilliskustannus
AFTER INSERT OR UPDATE
        ON erilliskustannus
FOR EACH ROW
EXECUTE PROCEDURE poista_muistetut_laskutusyht_erilliskustannus();


ALTER TABLE sanktio ADD COLUMN poistettu BOOLEAN DEFAULT FALSE;
>>>>>>> develop
