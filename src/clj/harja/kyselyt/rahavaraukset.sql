-- name: hae-urakoiden-rahavaraukset
SELECT u.id  AS "urakka-id",
       u.nimi AS "urakka-nimi",
       rv.id AS "id",
       rv.nimi
FROM urakka u
         LEFT JOIN rahavaraus_urakka rvu ON rvu.urakka_id = u.id
         LEFT JOIN rahavaraus rv ON rv.id = rvu.rahavaraus_id
WHERE u.tyyppi = 'teiden-hoito';

-- name: hae-rahavaraukset
SELECT nimi,
       id
FROM rahavaraus;

-- name: poista-urakan-rahavaraukset<!
DELETE
FROM rahavaraus_urakka
WHERE urakka_id = :urakka_id
  AND rahavaraus_id NOT IN (:rahavaraukset);

-- name: lisaa-urakan-rahavaraus<!
INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id)
VALUES (:urakka, :rahavaraus);
