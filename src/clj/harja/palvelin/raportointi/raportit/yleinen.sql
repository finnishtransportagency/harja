-- name: hae-kontekstin-urakat
-- Listaa kaikki ne urakat, joita haku koskee
SELECT
  u.id           AS "urakka-id",
  u.nimi         AS "nimi"
FROM urakka u
WHERE
  (:urakka :: INTEGER IS NULL OR u.id = :urakka)
  AND (:hallintayksikko :: INTEGER IS NULL OR hallintayksikko = :hallintayksikko)
  AND (:urakka :: INTEGER IS NOT NULL OR
       (:urakka :: INTEGER IS NULL AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                        u.tyyppi = :urakkatyyppi :: urakkatyyppi)))
  AND (:urakka :: INTEGER IS NOT NULL OR :urakka :: INTEGER IS NULL AND ((alkupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (loppupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (:alku >= alkupvm AND :loppu <= loppupvm)))
ORDER BY nimi;

-- name: hae-kontekstin-hallintayksikot
-- Listaa kaikki ne hallintayksikot, joita haku koskee
SELECT
  o.id           AS "hallintayksikko-id",
  o.nimi         AS "nimi",
  o.elynumero    AS "elynumero"
FROM organisaatio o
WHERE elynumero IS NOT NULL
ORDER BY elynumero;
