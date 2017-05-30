-- name: hae-vesivaylien-laskutusyhteenveto
SELECT
  hinnoittelu.nimi as "hinnoittelu",
  SUM(hinta.maara) as "summa"
FROM vv_hinnoittelu hinnoittelu
  LEFT JOIN vv_hinta hinta ON hinta."hinnoittelu-id" =  hinnoittelu.id
WHERE "urakka-id" = :urakkaid
      AND EXISTS(SELECT id FROM reimari_toimenpide WHERE "hinnoittelu-id" = hinnoittelu.id
                                                         AND suoritettu >= :alkupvm
                                                         AND suoritettu <= :loppupvm)
GROUP BY hinnoittelu.nimi;