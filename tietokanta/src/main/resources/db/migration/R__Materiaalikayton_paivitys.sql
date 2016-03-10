-- Päivittää sopimuksen materiaalikäytön päivälle
CREATE OR REPLACE FUNCTION paivita_sopimuksen_materiaalin_kaytto(sopimus INTEGER, pvm DATE)
RETURNS void AS $$
DECLARE
  mat RECORD;
BEGIN
  FOR mat IN SELECT t.sopimus, date_trunc('day',t.alkanut) as alkupvm, tm.materiaalikoodi,
                    SUM(CASE
		          WHEN (t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE)
			  THEN tm.maara
			  ELSE 0
			END) as maara
             FROM toteuma t
                  JOIN toteuma_materiaali tm ON tm.toteuma = t.id
             WHERE date_trunc('day', t.alkanut) = pvm
             GROUP BY t.sopimus, date_trunc('day',t.alkanut), tm.materiaalikoodi
  LOOP
    IF mat.maara = 0 THEN
      DELETE FROM sopimuksen_kaytetty_materiaali skm
       WHERE skm.sopimus = mat.sopimus AND
             skm.alkupvm = mat.alkupvm AND
	     skm.materiaalikoodi = mat.materiaalikoodi;
    ELSE
      INSERT
        INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
        VALUES (mat.sopimus, mat.alkupvm, mat.materiaalikoodi, mat.maara)
      ON CONFLICT ON CONSTRAINT uniikki_sop_pvm_mk
        DO UPDATE SET maara = mat.maara;
    END IF;
  END LOOP;
END;
$$ LANGUAGE plpgsql;
