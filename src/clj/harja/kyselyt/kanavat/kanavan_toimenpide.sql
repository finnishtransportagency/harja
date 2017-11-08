-- name: hae-sopimuksen-kanavatoimenpiteet-aikavalilta
SELECT kt.id
FROM kan_toimenpide kt
  JOIN kan_kohde kh ON kt.kohde = kh.id
  JOIN toimenpidekoodi tpk4 ON kt.toimenpidekoodi = tpk4.id
  JOIN toimenpidekoodi tpk3 ON tpk3.id = tpk4.emo
WHERE kt.urakka = :urakka AND
      kt.id = :sopimus AND
      (kt.pvm BETWEEN :alkupvm AND :loppupvm) AND
      (:toimenpidekoodi :: INTEGER IS NULL OR tpk3.id = :toimenpidekoodi) AND
      (kt.tyyppi = :tyyppi :: KAN_TOIMENPIDETYYPPI);