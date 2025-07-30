--name: lisaa-uusi-toimenkuva<!
-- Lisätään järjestelmään uusi toimenkuva, joka voidaan valita useammalle urakalle.
INSERT INTO johto_ja_hallintokorvaus_toimenkuva (toimenkuva) VALUES (:toimenkuva);

-- name: lisaa-urakan-toimenkuva<!
-- Lisätään toimenkuva urakalle. Tätä ei voida lisätä muille urakoille.
INSERT INTO johto_ja_hallintokorvaus_toimenkuva (toimenkuva, "urakka-id", urakkakohtainen_nimi)
VALUES (:toimenkuva, :urakkaid, :urakkakohtainen-nimi);

-- name: paivita-urakan-toimenkuva<!
UPDATE johto_ja_hallintokorvaus_toimenkuva
   SET urakkakohtainen_nimi = :urakkakohtainen-nimi
 WHERE "urakka-id" = :urakkaid
  AND id = :toimenkuvaid;

-- name: poista-urakan-toimenkuva<!
DELETE
  FROM johto_ja_hallintokorvaus_toimenkuva
 WHERE "urakka-id" = :urakkaid
   AND id = :toimenkuvaid;

-- name: onko-toimenkuva-kaytossa?
-- single?: true
SELECT EXISTS(SELECT id
              FROM johto_ja_hallintokorvaus j
              WHERE j."toimenkuva-id" = :id :: BIGINT);

-- name: poista-toimenkuva-urakoilta!
UPDATE johto_ja_hallintokorvaus_toimenkuva
   SET "urakka-id" = NULL
 WHERE id = :id :: BIGINT;

-- name: poista-toimenkuva!
DELETE
  FROM johto_ja_hallintokorvaus_toimenkuva
 WHERE id = :id :: BIGINT;

-- name: hae-toimenkuvat
-- Haetaan kaikki toimenkuvat, joita ei ole lisätty urakalle.
SELECT t.id, t.toimenkuva as nimi
  FROM johto_ja_hallintokorvaus_toimenkuva t
 WHERE t."urakka-id" IS NULL
 ORDER BY t.id ASC;

-- name: hae-urakoiden-toimenkuvat
SELECT u.id                     AS "urakka-id",
       u.nimi                   AS "urakka-nimi",
       t.urakkakohtainen_nimi AS "urakkakohtainen-nimi",
       t.id                    AS "id",
       t.toimenkuva            AS "nimi"
FROM urakka u
         LEFT JOIN johto_ja_hallintokorvaus_toimenkuva t ON t."urakka-id" = u.id
WHERE u.tyyppi = 'teiden-hoito'
    ORDER BY t.id ASC;;

-- name: hae-urakan-toimenkuvat
-- Haetaan urakalle lisätyt toimenkuvat.
SELECT t.id, t.toimenkuva, COALESCE(t.urakkakohtainen_nimi, '') AS urakkakohtainen_nimi
  FROM johto_ja_hallintokorvaus_toimenkuva t
 WHERE t."urakka-id" = :urakkaid
 ORDER BY t.id ASC;

-- name: hae-urakan-toimenkuva
-- Haetaan yksittäinen toimenkuva.
SELECT t.id, t.toimenkuva, COALESCE(t.urakkakohtainen_nimi, '') AS urakkakohtainen_nimi
FROM johto_ja_hallintokorvaus_toimenkuva t
WHERE t.toimenkuva = :nimi
  AND t."urakka-id" = :urakkaid;

-- name: onko-toimenkuva-olemassa?
-- single?: true
SELECT exists(SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE id = :toimenkuva-id :: BIGINT);
