-- name: hae-yksikkohintaiset-toimenpiteet
SELECT
  hinnoittelu.nimi                  AS "hinnoittelu",
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
SELECT
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo kt
     LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
     LEFT JOIN toimenpideinstanssi_vesivaylat tpi_vv ON kt.toimenpideinstanssi = tpi_vv."toimenpideinstanssi-id"
   WHERE tpi.urakka = :urakkaid
         AND tpi_vv.vaylatyyppi = :vaylatyyppi::vv_vaylatyyppi
         -- Kok. hint. osuu aikavälille jos eka päivä osuu
         AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') >= :alkupvm
         AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') <= :loppupvm) AS "suunniteltu-maara",
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo kt
     LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
     LEFT JOIN toimenpideinstanssi_vesivaylat tpi_vv ON kt.toimenpideinstanssi = tpi_vv."toimenpideinstanssi-id"
   WHERE tpi.urakka = :urakkaid
         AND tpi_vv.vaylatyyppi = :vaylatyyppi::vv_vaylatyyppi
         -- Toteutunut kok.hint työ on niiden kok.hint maksuerien summa,
         -- joiden maksupvm on menneisyydessä
         AND maksupvm <= NOW()) AS "toteutunut-maara"
