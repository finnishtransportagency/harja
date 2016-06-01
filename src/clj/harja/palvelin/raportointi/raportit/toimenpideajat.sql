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

-- name: hae-toimenpidepaivien-lukumaarat
SELECT
  count(DISTINCT(date_trunc('day', t.alkanut))) AS lkm,
  u.nimi as urakka,
  o.nimi as hallintayksikko,
  u.id as "urakka-id",
  o.id as "hallintayksikko-id",
  tpk.nimi,
  rp.talvihoitoluokka as luokka
FROM toteuma t
  JOIN urakka u ON t.urakka = u.id
  JOIN organisaatio o ON u.hallintayksikko = o.id
  JOIN reittipiste rp ON rp.toteuma = t.id
  JOIN reitti_tehtava rt ON rt.reittipiste = rp.id
  JOIN toimenpidekoodi tpk ON rt.toimenpidekoodi = tpk.id
WHERE (t.alkanut BETWEEN :alkupvm AND :loppupvm)
      AND t.poistettu IS NOT TRUE
      AND rp.talvihoitoluokka IN (:hoitoluokat)
      AND (:urakka::integer IS NULL OR u.id = :urakka)
      AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
GROUP BY u.nimi, tpk.nimi, rp.talvihoitoluokka, o.nimi, u.id, o.id;