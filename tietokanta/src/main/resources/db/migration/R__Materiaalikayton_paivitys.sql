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
             WHERE date_trunc('day', t.alkanut) = pvm
               AND t.sopimus = sop
             GROUP BY date_trunc('day',t.alkanut), tm.materiaalikoodi
  LOOP
    INSERT
      INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
      VALUES (sop, mat.alkupvm, mat.materiaalikoodi, mat.maara);
  END LOOP;
END;
$$ LANGUAGE plpgsql;
