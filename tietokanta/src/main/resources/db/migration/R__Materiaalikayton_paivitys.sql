-- Päivittää sopimuksen materiaalikäytön päivälle
CREATE OR REPLACE FUNCTION paivita_sopimuksen_materiaalin_kaytto(sopimus INTEGER, pvm DATE, urakkaid INTEGER)
    RETURNS VOID AS
$$
DECLARE
    mat RECORD;
    sop INTEGER;
BEGIN
    sop := sopimus;
    DELETE
      FROM sopimuksen_kaytetty_materiaali skm
     WHERE skm.sopimus = sop
       AND skm.alkupvm = pvm;
    FOR mat IN SELECT DATE_TRUNC('day', t.alkanut) AS alkupvm, tm.materiaalikoodi,
                      SUM(CASE
                              WHEN (t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE)
                                  THEN tm.maara
                              ELSE 0
                          END)                     AS maara
                 FROM toteuma t
                      JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                WHERE t.alkanut BETWEEN pvm::DATE AND pvm::DATE + '1 days'::INTERVAL
                  -- Lisättiin urakkaid ehto, jotta se osuu paremmin indeksiin
                  AND t.urakka = urakkaid
                  AND t.sopimus = sop
                GROUP BY DATE_TRUNC('day', t.alkanut), tm.materiaalikoodi
        LOOP
            INSERT
              INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
            VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara, CURRENT_TIMESTAMP)
                ON CONFLICT ON CONSTRAINT uniikki_sop_pvm_mk
                    DO UPDATE SET maara = mat.maara;
        END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Päivittää tietyn sopimuksen kaiken materiaalin käytön
CREATE OR REPLACE FUNCTION paivita_koko_sopimuksen_materiaalin_kaytto(
  sopimus INTEGER)
RETURNS void AS $$
DECLARE
  mat RECORD;
  sop INTEGER;
BEGIN
  sop := sopimus;
  DELETE FROM sopimuksen_kaytetty_materiaali skm WHERE skm.sopimus = sop;
  FOR mat IN SELECT date_trunc('day',t.alkanut) as alkupvm, tm.materiaalikoodi,
                    SUM(CASE
                          WHEN (t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE)
                          THEN tm.maara
                          ELSE 0
                        END) as maara
               FROM toteuma t
                    JOIN toteuma_materiaali tm ON tm.toteuma = t.id
              WHERE t.sopimus = sop
              GROUP BY date_trunc('day',t.alkanut), tm.materiaalikoodi
  LOOP
    INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
    VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara, current_timestamp);
  END LOOP;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION paivita_kaikki_sopimuksen_kaytetty_materiaali() RETURNS void AS $$
BEGIN
  -- Poistetaan kaikki
  DELETE FROM sopimuksen_kaytetty_materiaali;

  -- Luodaan uudet haun perusteella
  INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
      SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara), current_timestamp
        FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
       WHERE t.poistettu IS NOT TRUE and tm.poistettu IS NOT TRUE
	GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
END;
$$ LANGUAGE plpgsql;

-- Allaoleva funktio päivittää sopimuksen käytetyn materiaalin cachen päivämääräväliltä
-- Se voidaan suorittaa hätätilanteessa esim. SQL-tulkin avulla tuotantokantaa vasten AINA transaktion sisällä (BEGIN... do stuff; COMMIT/ROLLBACK;)
-- Mahdolliset rivien deletoinnit tehtävä käsin ennen ajoa.
CREATE OR REPLACE FUNCTION paivita_sopimuksen_kaytetty_materiaali_pvm_aikavalille(alku DATE, loppu DATE)
RETURNS void AS $$
DECLARE
BEGIN
        INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
        SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara), current_timestamp
          FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
         WHERE t.poistettu IS NOT TRUE and tm.poistettu IS NOT TRUE
            AND t.alkanut BETWEEN alku AND (select date_trunc('day', loppu) + interval '1 day' - interval '1 second')
         GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
    RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_sopimuksen_kaytetty_materiaali_muutospaivalla(
    urakka_id INTEGER, muutospvm DATE
) RETURNS void AS $$
BEGIN
    CREATE TEMP TABLE IF NOT EXISTS muuttuneet_pvmt (
      sopimus INTEGER,
      pvm DATE
    ) ON COMMIT DELETE ROWS;

    TRUNCATE muuttuneet_pvmt;

    INSERT INTO muuttuneet_pvmt (sopimus, pvm)
    SELECT DISTINCT t.sopimus, t.alkanut::date
    FROM toteuma t
    WHERE t.urakka = urakka_id
      AND ((t.luotu >= muutospvm AND t.luotu < muutospvm + INTERVAL '1 day')
       OR (t.muokattu >= muutospvm AND t.muokattu < muutospvm + INTERVAL '1 day'));

    -- Poistetaan kaikki päivitettävät rivit kerralla    
    DELETE FROM sopimuksen_kaytetty_materiaali skm
    USING muuttuneet_pvmt m
    WHERE skm.sopimus = m.sopimus
      AND skm.alkupvm = m.pvm;

    -- Lisätään uudet aggregoidut rivit kerralla
    INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara, muokattu)
    SELECT t.sopimus,
           DATE_TRUNC('day', t.alkanut)::date,
           tm.materiaalikoodi,
           SUM(CASE WHEN t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE
                    THEN tm.maara ELSE 0 END),
           CURRENT_TIMESTAMP
    FROM toteuma t
         JOIN toteuma_materiaali tm ON tm.toteuma = t.id
         JOIN muuttuneet_pvmt m ON m.sopimus = t.sopimus
                               AND m.pvm = t.alkanut::date
    WHERE t.urakka = urakka_id
    GROUP BY t.sopimus, DATE_TRUNC('day', t.alkanut), tm.materiaalikoodi;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_vanhat_alkanut_muutokset(urakka_id INTEGER)
RETURNS void AS $$
DECLARE
  rivi RECORD;
BEGIN
  FOR rivi IN
    SELECT toteuma_id, vanha_alkanut
    FROM toteuma_alkanut_muutos
    WHERE urakka_id = urakka_id
      AND kasitelty = FALSE
  LOOP

