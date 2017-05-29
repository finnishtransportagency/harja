-- name: hae-vesivaylien-laskutusyhteenveto
SELECT
  "reimari-toimenpidetyyppi"
FROM reimari_toimenpide
WHERE "urakka-id" = :urakkaid
AND suoritettu >= :alkupvm AND suoritettu <= :loppupvm;