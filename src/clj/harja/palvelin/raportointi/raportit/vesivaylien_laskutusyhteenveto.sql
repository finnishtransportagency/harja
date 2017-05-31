-- name: hae-yksikkohintaiset-toimenpiteet
SELECT
  hinnoittelu.nimi                          AS "hinnoittelu",
  (SELECT SUM(maara)
   FROM vv_hinta
   WHERE "hinnoittelu-id" = hinnoittelu.id) AS "summa",
  -- Hinnoittelut, jotka ovat hinnoitteluryhmiä, sisältävät useita reimari-toimenpiteitä
  -- jotka voivat potentiaalisesti liittyä eri väyliin / väylätyyppeihin
  -- Listataan hinnoitteluun liittyvät väylätyypit taulukossa.
  (SELECT ARRAY(SELECT DISTINCT (tyyppi)
                FROM vv_vayla
                WHERE id IN
                      (SELECT "vayla-id"
                       FROM reimari_toimenpide
                       WHERE id IN
                             (SELECT "toimenpide-id"
                              FROM vv_hinnoittelu_toimenpide
                              WHERE "hinnoittelu-id" = hinnoittelu.id))))
                                            AS vaylatyyppi,
  (SELECT suoritettu
   FROM reimari_toimenpide
   WHERE id IN (SELECT "toimenpide-id"
                FROM vv_hinnoittelu_toimenpide
                WHERE "hinnoittelu-id" = hinnoittelu.id)
   ORDER BY suoritettu
   LIMIT 1)
                                            AS ensimmainen_toimenpide,
  (SELECT suoritettu
   FROM reimari_toimenpide
   WHERE id IN (SELECT "toimenpide-id"
                FROM vv_hinnoittelu_toimenpide
                WHERE "hinnoittelu-id" = hinnoittelu.id)
   ORDER BY suoritettu DESC
   LIMIT 1)
                                            AS viimeinen_toimenpide
FROM vv_hinnoittelu hinnoittelu
WHERE "urakka-id" = :urakkaid
      -- Hinnoittelulle on kirjattu toimenpiteitä valitulla aikavälillä
      -- Vain yksikköhintaiset, koska kok. hint. toimenpiteitä ei hinnoitella Harjassa
      AND EXISTS(SELECT id
                 FROM reimari_toimenpide
                 WHERE suoritettu >= :alkupvm
                       AND suoritettu <= :loppupvm
                       AND hintatyyppi = 'yksikkohintainen'
                       AND id IN (SELECT "toimenpide-id"
                                  FROM vv_hinnoittelu_toimenpide
                                  WHERE "hinnoittelu-id" = hinnoittelu.id));

-- name: hae-kokonaishintaiset-toimenpiteet
SELECT
  "reimari-toimenpidetyyppi"                                             AS koodi,
  (SELECT COUNT(id)
   FROM reimari_toimenpide
   WHERE "urakka-id" = :urakkaid
         AND suoritettu >= :alkupvm
         AND suoritettu <= :loppupvm
         AND hintatyyppi = 'kokonaishintainen'
         AND "reimari-toimenpidetyyppi" = rt."reimari-toimenpidetyyppi") AS maara,
  vayla.tyyppi                                                           AS vaylatyyppi
FROM reimari_toimenpide rt
  LEFT JOIN vv_vayla vayla ON rt."vayla-id" = vayla.id
WHERE "urakka-id" = :urakkaid
      AND suoritettu >= :alkupvm
      AND suoritettu <= :loppupvm
      AND hintatyyppi = 'kokonaishintainen'
GROUP BY koodi, vayla.tyyppi;