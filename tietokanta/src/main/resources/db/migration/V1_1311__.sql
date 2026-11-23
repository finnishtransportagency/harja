-- POT2-päällystysilmoituksen luonnoksen tulee voida tallentaa, vaikka päällystekerrokselta puuttuisi materiaali.
-- Materiaali on edelleen pakollinen muulla kuin KAR-toimenpiteellä (41), kun ilmoitus ei ole luonnos (tila 'aloitettu').
-- Aiempi CHECK-rajoite korvataan viivästetyllä tarkistuksella, koska CHECK ei voi katsoa toista taulua.
-- Tarkistus tehdään transaktion lopussa, jotta ilmoituksen tilan ja päällystekerroksen rivit voi päivittää
-- samassa transaktiossa missä tahansa järjestyksessä.
ALTER TABLE pot2_paallystekerros DROP CONSTRAINT IF EXISTS materiaali_not_null_jos_ei_ole_toimenpide_kar;

CREATE OR REPLACE FUNCTION tarkista_pot2_paallystekerroksen_materiaali() RETURNS TRIGGER AS
$$
DECLARE
    ilmoitus_id INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'pot2_paallystekerros' THEN
        ilmoitus_id := NEW.pot2_id;
    ELSE
        ilmoitus_id := NEW.id;
    END IF;

    IF EXISTS(SELECT 1
                FROM pot2_paallystekerros pk
                     JOIN paallystysilmoitus pio ON pio.id = pk.pot2_id
               WHERE pk.pot2_id = ilmoitus_id
                 AND pk.materiaali IS NULL
                 AND pk.toimenpide <> 41
                 AND pio.tila IS DISTINCT FROM 'aloitettu') THEN
        RAISE EXCEPTION 'Päällystekerroksen materiaali on pakollinen, kun toimenpide ei ole KAR ja päällystysilmoitus ei ole luonnos (pot2_id %)', ilmoitus_id
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS pot2_paallystekerros_materiaali_tarkistus ON pot2_paallystekerros;
CREATE CONSTRAINT TRIGGER pot2_paallystekerros_materiaali_tarkistus
    AFTER INSERT OR UPDATE OF materiaali, toimenpide, pot2_id
    ON pot2_paallystekerros
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE tarkista_pot2_paallystekerroksen_materiaali();

DROP TRIGGER IF EXISTS paallystysilmoitus_pot2_materiaali_tarkistus ON paallystysilmoitus;
CREATE CONSTRAINT TRIGGER paallystysilmoitus_pot2_materiaali_tarkistus
    AFTER UPDATE OF tila
    ON paallystysilmoitus
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE PROCEDURE tarkista_pot2_paallystekerroksen_materiaali();
