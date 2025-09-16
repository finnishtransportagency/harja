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

-- name: hae-2025-urakoiden-toimenkuvat
-- Palautetaan vain 2025-> eteenpäin alkavien urakoiden toimenkuvat, koska aiemmilla toimenkuvat ovat tulleet koodista
SELECT u.id                     AS "urakka-id",
       u.nimi                   AS "urakka-nimi",
       t.urakkakohtainen_nimi AS "urakkakohtainen-nimi",
       t.id                    AS "id",
       t.toimenkuva            AS "nimi"
FROM urakka u
         LEFT JOIN johto_ja_hallintokorvaus_toimenkuva t ON t."urakka-id" = u.id
WHERE u.tyyppi = 'teiden-hoito'
    AND u.alkupvm >= '2025-10-01'::DATE
    ORDER BY t.id ASC;

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
WHERE t.toimenkuva = :toimenkuva
  AND t."urakka-id" = :urakkaid;

-- name: hae-toimenkuva
-- Haetaan yksittäinen toimenkuva.
SELECT t.id, t.toimenkuva, COALESCE(t.urakkakohtainen_nimi, '') AS urakkakohtainen_nimi, maksukuukaudet
FROM johto_ja_hallintokorvaus_toimenkuva t
WHERE t.toimenkuva = :toimenkuva
  AND t."urakka-id" IS NULL;

-- name: hae-toimenkuva-idlla
-- Haetaan yksittäinen toimenkuva.
SELECT t.id, "urakka-id", t.toimenkuva, COALESCE(t.urakkakohtainen_nimi, '') AS urakkakohtainen_nimi, maksukuukaudet
FROM johto_ja_hallintokorvaus_toimenkuva t
WHERE t.id = :id;

-- name: onko-toimenkuva-olemassa?
-- single?: true
SELECT exists(SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE id = :toimenkuva-id :: BIGINT);

-- name: hae-urakan-toimenkuvat-alkuvuoden-perusteella
-- Hae urakkakohtaiset toimenkuvat
WITH urakka_toimenkuvat AS (SELECT toimenkuva
                            FROM unnest(
                                     CASE
                                         WHEN (:urakan-alkuvuosi >= 2019 AND :urakan-alkuvuosi <= 2021)
                                             THEN ARRAY ['sopimusvastaava', 'vastuunalainen työnjohtaja', 'päätoiminen apulainen', 'apulainen/työnjohtaja', 'viherhoidosta vastaava henkilö', 'hankintavastaava', 'harjoittelija']
                                         WHEN (:urakan-alkuvuosi >= 2022 AND :urakan-alkuvuosi <= 2023)
                                             THEN ARRAY ['valmistelukausi ennen urakka-ajan alkua','vastuunalainen työnjohtaja', 'päätoiminen apulainen','apulainen/työnjohtaja', 'viherhoidosta vastaava henkilö', 'hankintavastaava', 'harjoittelija']
                                         WHEN (:urakan-alkuvuosi = 2024)
                                             THEN ARRAY ['valmistelukausi ennen urakka-ajan alkua','vastuunalainen työnjohtaja','2. työnjohtaja', '3. työnjohtaja', 'viherhoidosta vastaava henkilö', 'harjoittelija']
                                         END
                                 ) AS toimenkuva)
SELECT id, toimenkuva
FROM johto_ja_hallintokorvaus_toimenkuva jht
WHERE jht."urakka-id" = :urakka-id
  AND jht.toimenkuva is not null
UNION
SELECT (select MIN(id) from johto_ja_hallintokorvaus_toimenkuva where toimenkuva = ut.toimenkuva) AS id,
       toimenkuva
from urakka_toimenkuvat ut
ORDER BY ID;
