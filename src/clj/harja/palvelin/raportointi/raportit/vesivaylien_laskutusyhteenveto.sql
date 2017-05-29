-- name: hae-vesivaylien-laskutusyhteenveto
SELECT
  "reimari-toimenpidetyyppi"
FROM reimari_toimenpide
WHERE "toteuma-id" IN (SELECT id FROM toteuma WHERE urakka = :urakkaid)
AND suoritettu >= :alkupvm AND suoritettu <= :loppupvm;