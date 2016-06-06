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
   AND u.tyyppi = :urakkatyyppi::urakkatyyppi
   AND (:urakka::integer IS NULL OR u.id = :urakka)
   AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
GROUP BY u.nimi, tpk.nimi, tunti, rp.talvihoitoluokka

-- name: hae-toimenpidepaivien-lukumaarat
SELECT
  t.alkanut::date as pvm, --count(distinct date_trunc('day', t.alkanut)) AS lkm,
  u.id as urakka,
  o.id as hallintayksikko,
  rt.toimenpidekoodi,
  rp.talvihoitoluokka as luokka
FROM reittipiste rp
  JOIN toteuma t ON rp.toteuma = t.id
  JOIN urakka u ON t.urakka = u.id
  JOIN organisaatio o ON u.hallintayksikko = o.id
  JOIN reitti_tehtava rt ON rt.reittipiste = rp.id
WHERE rp.talvihoitoluokka IN (:hoitoluokat)
      AND (t.alkanut BETWEEN :alkupvm AND :loppupvm)
      AND t.poistettu IS NOT TRUE
      AND u.tyyppi = :urakkatyyppi::urakkatyyppi
      AND (:urakka::integer IS NULL OR u.id = :urakka)
      AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
