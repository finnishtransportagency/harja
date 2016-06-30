-- name: hae-raportit
-- Hakee kaikki raportit
SELECT r.nimi, r.kuvaus, r.konteksti, r.koodi, r.urakkatyyppi,
  r.nimi as id,             -- raportin ja sen
  r.nimi as parametri_id,   -- parametrien yhdistämistä varten
  -- Puretaan parametrit omiksi riveiksi, myös silloin kun raportilla ei ole parametreja
  CASE WHEN parametrit <> '{}' THEN (unnest(parametrit)).nimi END AS parametri_nimi,
  CASE WHEN parametrit <> '{}' THEN (unnest(parametrit)).tyyppi END AS parametri_tyyppi,
  CASE WHEN parametrit <> '{}' THEN (unnest(parametrit)).pakollinen END AS parametri_pakollinen,
  CASE WHEN parametrit <> '{}' THEN (unnest(parametrit)).konteksti END AS parametri_konteksti
FROM raportti r
ORDER BY nimi;