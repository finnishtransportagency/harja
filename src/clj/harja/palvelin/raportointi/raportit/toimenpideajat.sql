-- name: hae-toimenpideajat
SELECT
  -- Kaikki toteuma/toimenpide parit
  COUNT(t.id) as lkm,
  u.nimi as urakka,
  tpk.nimi,
  (EXTRACT(HOUR FROM t.alkanut))::integer as tunti,
  rp.talvihoitoluokka as luokka
  FROM toteuma t
       JOIN urakka u ON t.urakka = u.id
       JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
       LEFT JOIN LATERAL unnest(tr.reittipisteet) AS rp ON true
       JOIN toteuma_tehtava tt ON t.id = tt.toteuma
       JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
 WHERE (t.alkanut BETWEEN :alkupvm AND :loppupvm)
   AND t.poistettu IS NOT TRUE
   AND (rp.talvihoitoluokka IN (:hoitoluokat) OR rp.talvihoitoluokka IS NULL)
   AND u.tyyppi = :urakkatyyppi::urakkatyyppi
   AND (:urakka::integer IS NULL OR u.id = :urakka)
   AND u.urakkanro IS NOT NULL
   AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
GROUP BY u.nimi, tpk.nimi, tunti, rp.talvihoitoluokka;

-- name: hae-toimenpidepaivien-lukumaarat
SELECT
  u.id as urakka,
  o.id as hallintayksikko,
  toimkood.id AS tpk,
  rp.talvihoitoluokka as luokka,
  COUNT(DISTINCT t.alkanut::date) as lkm
FROM toteuma t
  JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
  LEFT JOIN LATERAL unnest(tr.reittipisteet) AS rp ON true
  JOIN urakka u ON t.urakka = u.id
  JOIN organisaatio o ON u.hallintayksikko = o.id
  JOIN toteuma_tehtava tt ON t.id = tt.toteuma
  JOIN toimenpidekoodi toimkood ON tt.toimenpidekoodi = toimkood.id
WHERE (rp.talvihoitoluokka IN (:hoitoluokat) OR rp.talvihoitoluokka IS NULL)
      AND (t.alkanut BETWEEN :alku AND :loppu)
      AND t.poistettu IS NOT TRUE
      AND u.tyyppi = :urakkatyyppi::urakkatyyppi
      AND (:urakka::integer IS NULL OR u.id = :urakka)
      AND u.urakkanro IS NOT NULL
      AND (:hallintayksikko::integer IS NULL OR u.hallintayksikko = :hallintayksikko)
GROUP BY u.id, o.id, tpk, rp.talvihoitoluokka;
