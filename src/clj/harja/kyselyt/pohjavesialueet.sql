-- name: hae-pohjavesialueet
-- Hakee pohjavesialueet annetulle hallintayksikölle
SELECT id, tunnus, nimi, alue
  FROM pohjavesialue
 WHERE ST_CONTAINS((SELECT alue FROM organisaatio WHERE id=:hallintayksikko), alue);
