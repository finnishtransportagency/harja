-- NOTE TO SELF: Hinnoittelut on aina yks. hint. toimenpiteitä, kokonaishintaisia ei hinnoitella

-- name: hae-yksikkohintaiset-ryhmattomat-toimenpiteet
SELECT
  hinnoittelu.nimi as "hinnoittelu",
  (SELECT SUM(maara) FROM vv_hinta WHERE "hinnoittelu-id" = hinnoittelu.id) as "summa",
  -- Hinnoittelut, jotka eivät ole hintaryhmiä, sisältävät vain yhden reimari-toimenpiteen
  -- Siitä voidaan päätellä hintaryhmän väylätyyppi.
  -- Hintaryhmälliset hinnoittelut pitää käsitellä erikseen
  (SELECT tyyppi FROM vv_vayla WHERE id =
                                     (SELECT "vayla-id" FROM reimari_toimenpide WHERE id =
                                                                                      (SELECT "toimenpide-id" FROM vv_hinnoittelu_toimenpide WHERE "hinnoittelu-id" = hinnoittelu.id LIMIT 1)))
    as vaylatyyppi
FROM vv_hinnoittelu hinnoittelu
WHERE "urakka-id" = :urakkaid
      AND hintaryhma IS NOT TRUE
      -- Hinnoittelulle on kirjattu toimenpiteitä valitulla aikavälillä
      -- Vain yksikköhintaiset, koska kok. hint. toimenpiteitä ei hinnoitella Harjassa
      AND EXISTS(SELECT id
                 FROM reimari_toimenpide
                 WHERE suoritettu >= :alkupvm
                       AND suoritettu <= :loppupvm
                       AND hintatyyppi = 'yksikkohintainen'
                       AND id IN (SELECT "toimenpide-id"
                                  FROM vv_hinnoittelu_toimenpide
                                  WHERE "hinnoittelu-id" = hinnoittelu.id))


-- name: hae-yksikkohintaiset-ryhmalliset-toimenpiteet
SELECT
  hinnoittelu.nimi as "hinnoittelu",
  (SELECT SUM(maara) FROM vv_hinta WHERE "hinnoittelu-id" = hinnoittelu.id) as "summa"
-- Hinnoittelut, jotka ovat ryhmiä, sisältävät useita reimari-toimenpiteitä,
-- jotka voivat potentiaalisesti liittyä eri väyliin / väylätyyppeihin
FROM vv_hinnoittelu hinnoittelu
WHERE "urakka-id" = :urakkaid
      AND hintaryhma IS TRUE
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