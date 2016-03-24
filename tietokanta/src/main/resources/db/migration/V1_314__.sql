-- Luo sopimuksen käytetystä materiaalista summataulu jota ylläpidetään triggereillä

CREATE TABLE sopimuksen_kaytetty_materiaali (
  sopimus integer REFERENCES sopimus (id),
  alkupvm date,
  materiaalikoodi integer REFERENCES materiaalikoodi (id),
  maara numeric,
  CONSTRAINT uniikki_sop_pvm_mk UNIQUE (sopimus,alkupvm,materiaalikoodi)
);

-- Luodaan alkutilanne, summataan nykyiset materiaalit
INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
  SELECT t.sopimus, date_trunc('day', t.alkanut), tm.materiaalikoodi, SUM(tm.maara)
  FROM toteuma t
    JOIN toteuma_materiaali tm ON tm.toteuma = t.id
  WHERE t.poistettu IS NOT TRUE AND tm.poistettu IS NOT TRUE
  GROUP BY t.sopimus, tm.materiaalikoodi, date_trunc('day', t.alkanut);