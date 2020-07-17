-- name: hae-kokonaishintaiset-toteumat
SELECT
  urakka,
  hallintayksikko,
  SUM(rt.maara) AS maara,
  tpk.id   AS "toimenpidekoodi-id",
  tpk.nimi AS "toimenpidekoodi-nimi",
  yksikko  AS "toimenpidekoodi-yksikko",
  hl.hoitoluokka
FROM toteuma t
  JOIN urakka u ON t.urakka = u.id
  JOIN toteuma_tehtava tt ON t.id = tt.toteuma
                             AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
  LEFT JOIN LATERAL unnest(tr.reittipisteet) AS rp ON TRUE
  LEFT JOIN LATERAL unnest(rp.tehtavat) AS rt ON TRUE
  LEFT JOIN LATERAL (select normalisoi_talvihoitoluokka(rp.talvihoitoluokka::INTEGER, t.alkanut::TIMESTAMP) AS hoitoluokka) hl ON TRUE
WHERE (:urakka::INTEGER IS NULL OR t.urakka = :urakka)
      AND (:hallintayksikko::INTEGER IS NULL OR t.urakka IN (SELECT id
                                                              FROM urakka
                                                              WHERE hallintayksikko =
                                                                    :hallintayksikko))
      AND (:urakka::INTEGER IS NOT NULL OR
           (:urakka::INTEGER IS NULL AND (ARRAY[:urakkatyyppi] :: urakkatyyppi[] IS NULL OR
                                          u.tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[])
                                          AND urakkanro IS NOT NULL)))
      AND t.alkanut BETWEEN :alku AND :loppu
      AND tpk.yksikko IN ('tiekm', 'jkm')
AND t.tyyppi = 'kokonaishintainen'
AND t.poistettu IS NOT TRUE
GROUP BY urakka, hallintayksikko, tpk.id, hl.hoitoluokka
ORDER BY urakka;
