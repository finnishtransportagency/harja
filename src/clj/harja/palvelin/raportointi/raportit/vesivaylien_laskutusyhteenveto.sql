-- name: hae-yksikkohintaiset-toimenpiteet
SELECT
  hinnoittelu.nimi                          AS "hinnoittelu",
  (SELECT SUM(maara)
   FROM vv_hinta
   WHERE "hinnoittelu-id" = hinnoittelu.id
   AND poistettu IS NOT TRUE) AS "summa",
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
                                            AS vaylatyyppi
FROM vv_hinnoittelu hinnoittelu
WHERE "urakka-id" = :urakkaid
      AND poistettu IS NOT TRUE
      -- Hinnoittelulle on kirjattu toimenpiteitä valitulla aikavälillä
      AND EXISTS(SELECT id
                 FROM reimari_toimenpide
                 WHERE suoritettu >= :alkupvm
                       AND suoritettu <= :loppupvm
                       AND hintatyyppi = 'yksikkohintainen'
                       AND id IN (SELECT "toimenpide-id"
                                  FROM vv_hinnoittelu_toimenpide
                                  WHERE "hinnoittelu-id" = hinnoittelu.id));

-- name: hae-kokonaishintaiset-toimenpiteet
-- Kokonaishintaisia toimenpiteitä ei hinnoitella Harjassa
-- Työt kuitenkin suunnitellaan, ja käytännössä suunnitelma = toteuma
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