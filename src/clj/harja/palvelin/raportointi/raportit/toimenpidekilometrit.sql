-- name: hae-kokonaishintaiset-toteumat
SELECT
  alkanut,
  urakka,
  hallintayksikko,
  maara,
  tpk.id   AS toimenpidekoodi_id,
  tpk.nimi AS toimenpidekoodi_nimi,
  yksikko  AS toimenpidekoodi_yksikko
FROM toteuma t
  JOIN urakka u ON t.urakka = u.id
  JOIN toteuma_tehtava tt ON t.id = tt.toteuma
  AND tt.poistettu IS NOT TRUE
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
WHERE (:urakka::INTEGER IS NULL OR t.urakka = :urakka)
      AND (:hallintayksikko::INTEGER IS NULL OR t.urakka IN (SELECT id
                                                              FROM urakka
                                                              WHERE hallintayksikko =
                                                                    :hallintayksikko))
      AND (:urakka::INTEGER IS NOT NULL OR
           (:urakka::INTEGER IS NULL AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                          u.tyyppi = :urakkatyyppi :: urakkatyyppi)))
      AND t.alkanut :: DATE BETWEEN :alku AND :loppu;
AND t.tyyppi = 'kokonaishintainen'
AND t.poistettu IS NOT TRUE;