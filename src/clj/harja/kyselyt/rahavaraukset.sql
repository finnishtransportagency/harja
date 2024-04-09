-- name: hae-urakoiden-rahavaraukset
SELECT u.id   AS "urakka-id",
       u.nimi AS "urakka-nimi",
       rv.id  AS "id",
       rv.nimi
  FROM urakka u
           LEFT JOIN rahavaraus_urakka rvu ON rvu.urakka_id = u.id
           LEFT JOIN rahavaraus rv ON rv.id = rvu.rahavaraus_id
 WHERE u.tyyppi = 'teiden-hoito';

-- name: hae-rahavaraukset
SELECT id, nimi
  FROM rahavaraus;

-- name: hae-rahavaraukset-tehtavineen
-- Haetaan kaikki rahavaraukset ja niihin liittyvät tehtävät
SELECT rv.id,
       rv.nimi,
       (SELECT ARRAY_AGG(ROW (t.id, t.nimi))
          FROM rahavaraus_tehtava rvt
                   JOIN tehtava t ON t.id = rvt.tehtava_id AND rvt.rahavaraus_id = rv.id) AS tehtavat
  FROM rahavaraus rv
 ORDER BY rv.id ASC;

-- name: hae-rahavaraukselle-mahdolliset-tehtavat
SELECT id, nimi
  FROM tehtava
ORDER BY jarjestys ASC;

-- name: lisaa-urakan-rahavaraus<!
INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, luoja, luotu)
VALUES (:urakka, :rahavaraus, :kayttaja, CURRENT_TIMESTAMP);

-- name: poista-urakan-rahavaraus<!
DELETE
  FROM rahavaraus_urakka
 WHERE urakka_id = :urakka
   AND rahavaraus_id = :rahavaraus;
