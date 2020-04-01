-- Päivittää sopimuksen materiaalikäytön päivälle
CREATE OR REPLACE FUNCTION paivita_sopimuksen_materiaalin_kaytto(sopimus INTEGER, pvm DATE)
RETURNS void AS $$
DECLARE
  mat RECORD;
  sop INTEGER;
BEGIN
  sop := sopimus;
  DELETE FROM sopimuksen_kaytetty_materiaali skm
    WHERE skm.sopimus = sop
    AND skm.alkupvm = pvm;
  FOR mat IN SELECT date_trunc('day',t.alkanut) as alkupvm, tm.materiaalikoodi,
                    SUM(CASE
		          WHEN (t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE)
			  THEN tm.maara
			  ELSE 0
			END) as maara
             FROM toteuma t
                  JOIN toteuma_materiaali tm ON tm.toteuma = t.id
             WHERE t.alkanut between pvm::date AND pvm::date + '1 days'::interval
                   AND t.sopimus = sop
             GROUP BY date_trunc('day',t.alkanut), tm.materiaalikoodi
  LOOP
    INSERT
      INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
      VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara)
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
    INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
    VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara);
  END LOOP;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION paivita_kaikki_sopimuksen_kaytetty_materiaali() RETURNS void AS $$
BEGIN
  -- Poistetaan kaikki
  DELETE FROM sopimuksen_kaytetty_materiaali;

  -- Luodaan uudet haun perusteella
  INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
      SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara)
        FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
       WHERE t.poistettu IS NOT TRUE and tm.poistettu IS NOT TRUE
	GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
END;
$$ LANGUAGE plpgsql;

-- Allaoleva funktio päivittää sopimuksen käytetyn materiaalin cachen päivämääräväliltä
-- Se voidaan suorittaa hätätilanteessa esim. SQL-tulkin avulla tuotantokantaa vasten AINA transaktion sisällä (BEGIN... do stuff; COMMIT/ROLLBACK;)
-- Mahdolliset rivien deletoinnit tehtävä käsin ennen ajoa. Ks. VHAR-1691
CREATE OR REPLACE FUNCTION paivita_sopimuksen_kaytetty_materiaali_pvm_aikavalille(alku DATE, loppu DATE)
RETURNS void AS $$
DECLARE
BEGIN
        INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
        SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara)
          FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
         WHERE t.poistettu IS NOT TRUE and tm.poistettu IS NOT TRUE
            AND t.alkanut BETWEEN alku AND (select date_trunc('day', loppu) + interval '1 day' - interval '1 second')
         GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
    RETURN;
END;
$$ LANGUAGE plpgsql;
