-- name: hae-kokonaishintaiset-toteumat
SELECT
  urakka,
  hallintayksikko,
  SUM(rt.maara) AS maara,
  tpk.id   AS "toimenpidekoodi-id",
  tpk.nimi AS "toimenpidekoodi-nimi",
  yksikko  AS "toimenpidekoodi-yksikko",
  rp.talvihoitoluokka AS hoitoluokka
FROM toteuma t
  JOIN urakka u ON t.urakka = u.id
  JOIN toteuma_tehtava tt ON t.id = tt.toteuma
                             AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  JOIN reittipiste rp ON t.id = rp.toteuma
  JOIN reitti_tehtava rt ON rp.id = rt.reittipiste
WHERE (:urakka::INTEGER IS NULL OR t.urakka = :urakka)
      AND (:hallintayksikko::INTEGER IS NULL OR t.urakka IN (SELECT id
                                                              FROM urakka
                                                              WHERE hallintayksikko =
                                                                    :hallintayksikko))
      AND (:urakka::INTEGER IS NOT NULL OR
           (:urakka::INTEGER IS NULL AND ((:urakkatyyppi :: urakkatyyppi IS NULL OR
                                          u.tyyppi = :urakkatyyppi :: urakkatyyppi)
                                          AND urakkanro IS NOT NULL)))
      AND t.alkanut :: DATE BETWEEN :alku AND :loppu
      AND tpk.yksikko IN ('tiekm', 'jkm')
AND t.tyyppi = 'kokonaishintainen'
AND t.poistettu IS NOT TRUE
GROUP BY urakka, hallintayksikko, tpk.id, talvihoitoluokka;