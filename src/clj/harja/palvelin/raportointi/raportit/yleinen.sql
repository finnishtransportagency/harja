-- name: hae-kontekstin-urakat
-- Listaa kaikki ne urakat, joita haku koskee
SELECT
  u.id           AS "urakka-id",
  u.nimi         AS "nimi",
  u.loppupvm     AS loppupvm
FROM urakka u
WHERE
  ((:urakka :: INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR u.id = :urakka)
  AND (:elinvoimakeskus :: INTEGER IS NULL OR elinvoimakeskus_id = :elinvoimakeskus)
  AND (:urakka :: INTEGER IS NOT NULL OR
       (:urakka :: INTEGER IS NULL AND (TRUE IN (SELECT unnest(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR
                                        u.tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))))
  AND (:urakka :: INTEGER IS NOT NULL OR :urakka :: INTEGER IS NULL AND ((alkupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (loppupvm :: DATE BETWEEN :alku AND :loppu)
                                                                         OR (:alku >= alkupvm AND :loppu <= loppupvm)))
ORDER BY nimi;

-- name: hae-kontekstin-elinvoimakeskukset
-- Listaa kaikki ne elinvoimakeskukset, joita haku koskee
SELECT
  o.id           AS "elinvoimakeskus-id",
  o.nimi         AS "nimi",
  right(cast(o.elinvoimakeskusnumero as varchar), 2) AS "elinvoimakeskusnumero"
FROM organisaatio o
WHERE :liikennemuoto::liikennemuoto = liikennemuoto AND
      tyyppi = 'elinvoimakeskus'
ORDER BY elinvoimakeskusnumero;


-- name: hae-urakoiden-nimet
SELECT id,nimi FROM urakka WHERE id IN (:urakka)

-- name: hae-organisaatioiden-nimet
SELECT id,nimi FROM organisaatio WHERE id IN (:organisaatio)

-- name: hae-toimenpidekoodien-nimet
SELECT id,nimi FROM tehtava WHERE id IN (:toimenpidekoodi)
