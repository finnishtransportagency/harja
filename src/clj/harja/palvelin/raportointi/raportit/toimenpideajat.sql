-- name: hae-toimenpideajat
SELECT
  -- Kaikki toteuma/toimenpide parit
  COUNT(t.id) as lkm,
  u.nimi as urakka, tpk.nimi,
  (EXTRACT(HOUR FROM t.alkanut))::integer as tunti,
  rp.talvihoitoluokka as luokka
  FROM toteuma t
       JOIN urakka u ON t.urakka = u.id
       JOIN reittipiste rp ON rp.toteuma = t.id
       JOIN reitti_tehtava rt ON rt.reittipiste = rp.id
       JOIN toimenpidekoodi tpk ON rt.toimenpidekoodi = tpk.id
 WHERE (t.alkanut BETWEEN :alkupvm AND :loppupvm)
   AND t.poistettu IS NOT TRUE
   AND rp.talvihoitoluokka IN (:hoitoluokat)
   AND (:urakka::integer IS NULL OR u.id = :urakka)
   AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
GROUP BY u.nimi, tpk.nimi, tunti, rp.talvihoitoluokka
