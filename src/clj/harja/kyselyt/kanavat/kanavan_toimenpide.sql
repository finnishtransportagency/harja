-- name: hae-sopimuksen-kanavatoimenpiteet-aikavalilta
SELECT kt.id
FROM kan_toimenpide kt
  JOIN kan_kohde kh ON kt.kohde = kh.id
  JOIN kan_kohde_urakka khu ON kh.id = khu."kohde-id"
  JOIN urakka u ON khu."urakka-id" = u.id
  JOIN sopimus s ON u.id = s.urakka
WHERE s.id = :sopimus AND
      (kt.pvm BETWEEN :alkupvm AND :loppupvm) AND
      (:toimenpidekoodi :: INTEGER IS NULL OR kt.toimenpidekoodi = :toimenpidekoodi) AND
      (:tyyppi :: KAN_TOIMENPIDETYYPPI IS NULL OR kt.tyyppi = :tyyppi :: KAN_TOIMENPIDETYYPPI);