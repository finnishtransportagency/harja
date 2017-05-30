-- name: hae-yksikkohintaiset-toimenpiteet
SELECT
  hinnoittelu.nimi as "hinnoittelu",
  (SELECT SUM(maara) FROM vv_hinta WHERE "hinnoittelu-id" = hinnoittelu.id) as "summa",
  -- Hinnoittelut, jotka ovat hinnoitteluryhmiä, sisältävät useita reimari-toimenpiteitä
  -- jotka voivat potentiaalisesti liittyä eri väyliin / väylätyyppeihin
  -- Listataan hinnoitteluun liittyvät väylätyypit taulukossa.
  (SELECT ARRAY(SELECT DISTINCT(tyyppi) FROM vv_vayla WHERE id IN
                                                            (SELECT "vayla-id" FROM reimari_toimenpide WHERE id IN
                                                                                                             (SELECT "toimenpide-id" FROM vv_hinnoittelu_toimenpide WHERE "hinnoittelu-id" = hinnoittelu.id))))
    as vaylatyyppi
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