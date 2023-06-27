-- name: hae-kontekstin-urakat
-- Listaa kaikki ne urakat, joita haku koskee
SELECT
  u.id           AS "urakka-id",
  u.nimi         AS "nimi",
  u.loppupvm     AS loppupvm
FROM urakka u
WHERE
  ((:urakka :: INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka)
  AND (:hallintayksikko :: INTEGER IS NULL OR hallintayksikko = :hallintayksikko)
  AND (:urakka :: INTEGER IS NOT NULL OR
       (:urakka :: INTEGER IS NULL AND (TRUE IN (SELECT unnest(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR
                                        u.tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))))
  AND (:urakka :: INTEGER IS NOT NULL OR :urakka :: INTEGER IS NULL AND ((alkupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (loppupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (:alku >= alkupvm AND :loppu <= loppupvm)))
ORDER BY nimi;

-- name: hae-kontekstin-hallintayksikot
-- Listaa kaikki ne hallintayksikot, joita haku koskee
SELECT
  o.id           AS "hallintayksikko-id",
  o.nimi         AS "nimi",
  lpad(cast(elynumero as varchar), 2, '0') AS "elynumero"
FROM organisaatio o
WHERE :liikennemuoto::liikennemuoto = liikennemuoto AND
      tyyppi='hallintayksikko'
ORDER BY elynumero;


-- name: hae-urakoiden-nimet
SELECT id,nimi FROM urakka WHERE id IN (:urakka)

-- name: hae-organisaatioiden-nimet
SELECT id,nimi FROM organisaatio WHERE id IN (:organisaatio)

-- name: hae-toimenpidekoodien-nimet
SELECT id,nimi FROM tehtava WHERE id IN (:toimenpidekoodi)
