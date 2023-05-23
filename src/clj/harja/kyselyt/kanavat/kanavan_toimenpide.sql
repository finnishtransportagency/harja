-- name: hae-kanavatoimenpiteet-aikavalilta
SELECT kt.id
FROM kan_toimenpide kt
  LEFT JOIN kan_kohde kh ON kt."kohde-id" = kh.id
  JOIN tehtava tpk4 ON kt.toimenpidekoodi = tpk4.id
  JOIN toimenpide tpk3 ON tpk3.id = tpk4.emo
WHERE kt.urakka = :urakka AND
      kt.sopimus = :sopimus AND
      (kt.pvm BETWEEN :alkupvm AND :loppupvm) AND
      (:toimenpidekoodi :: INTEGER IS NULL OR tpk3.id = :toimenpidekoodi) AND
      (kt.tyyppi = :tyyppi :: KAN_TOIMENPIDETYYPPI) AND
      (:kohde :: INTEGER IS NULL OR "kohde-id" = :kohde);
