-- name: hae-toteumat
-- Hakee kaikki toteumat
SELECT t.id,
       t.tyyppi,
       t.reitti,
       t.alkanut, t.paattynyt,
       tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
       tpk.nimi AS tehtava_toimenpide
  FROM toteuma t
  JOIN toteuma_tehtava tt ON tt.toteuma = t.id
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
 WHERE ST_InterSects(t.reitti, :sijainti)
   AND ((t.alkanut BETWEEN :alku AND :loppu) OR
        (t.paattynyt BETWEEN :alku AND :loppu))
