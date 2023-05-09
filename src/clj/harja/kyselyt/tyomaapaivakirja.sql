-- name: hae-tiedot
-- Mock data
SELECT
   id, 
   sampoid, 
   nimi, 
   alkupvm, 
   loppupvm, 
   sopimustyyppi, 
   floor(random() * 3 + 0)::int AS tila 
FROM urakka ORDER BY alkupvm DESC LIMIT 10;
