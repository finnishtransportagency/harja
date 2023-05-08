-- name: hae-tiedot
-- Mock data
SELECT
   id, sampoid, nimi, alkupvm, loppupvm, sopimustyyppi
FROM urakka ORDER BY alkupvm DESC LIMIT 10;
