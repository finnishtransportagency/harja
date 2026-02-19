UPDATE urakka_parametrit
SET laskutusraja_kaytossa = TRUE
FROM urakka
WHERE urakka_parametrit.urakkaid = urakka.id
  AND alkupvm >= '2025-01-01'
  AND urakka.tyyppi = 'teiden-hoito';
