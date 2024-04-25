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
