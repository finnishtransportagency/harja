-- name: hae-urakan-siltatarkastukset
-- Hakee urakan kaikki sillat ja niiden annettuna vuonna tehdyn uusimman siltatarkastuksen
SELECT
  s.id,
  siltanro,
  siltanimi,
  (SELECT tarkastusaika
   FROM siltatarkastus st
   WHERE st.silta = s.id
         AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1),
  (SELECT tarkastaja
   FROM siltatarkastus st
   WHERE st.silta = s.id
         AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1),
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{A}'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "a",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{B}'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "b",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{C}'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "c",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{D}'
         AND siltatarkastus = (SELECT id
                               FROM siltatarkastus st
                               WHERE st.silta = s.id
                                     AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND st.poistettu = FALSE
                               ORDER BY tarkastusaika DESC
                               LIMIT 1)) AS "d",
  l.id AS liite_id,
  l.tyyppi AS liite_tyyppi,
  l.koko AS liite_koko,
  l.nimi AS liite_nimi
FROM silta s
  LEFT JOIN liite l ON l.id IN (SELECT id
                                FROM
                                  liite l
                                  JOIN siltatarkastus_kohde_liite skl ON l.id = skl.liite
                                WHERE skl.siltatarkastus IN (SELECT id
                                                             FROM siltatarkastus st
                                                             WHERE st.silta = s.id
                                                                   AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                                                   AND st.poistettu = FALSE
                                                                   AND urakka = :urakka
                                                             ORDER BY tarkastusaika DESC
                                                             LIMIT 1))
WHERE s.urakat @> ARRAY[:urakka] ::INT[]
ORDER BY siltanro;

-- name: hae-sillan-tarkastus
SELECT
 s.siltanimi,
 s.siltatunnus,
 tarkastusaika,
 tarkastaja
FROM siltatarkastus st
 JOIN silta s ON st.silta = s.id
WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
      AND urakka = :urakka
      AND silta = :silta
      AND st.poistettu = FALSE
ORDER BY tarkastusaika DESC
LIMIT 1;

-- name: hae-sillan-tarkastuskohteet
-- Hakee valitun sillan annettuna vuonna tehdyn uusimman siltatarkastuksen
SELECT
  kohde,
  tulos,
  lisatieto,
  (SELECT
     tarkastusaika
   FROM siltatarkastus st
   WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND urakka = :urakka
         AND silta = :silta
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1),
  (SELECT
     tarkastaja
   FROM siltatarkastus st
   WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND urakka = :urakka
         AND silta = :silta
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1),
  l.id AS liite_id,
  l.tyyppi AS liite_tyyppi,
  l.koko AS liite_koko,
  l.nimi AS liite_nimi
FROM siltatarkastuskohde stk
  LEFT JOIN liite l ON l.id IN (SELECT id
                                FROM
                                  liite l
                                  JOIN siltatarkastus_kohde_liite skl ON l.id = skl.liite
                                WHERE skl.siltatarkastus IN (SELECT id
                                                             FROM siltatarkastus st
                                                             WHERE st.silta = :silta
                                                                   AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                                                   AND st.poistettu = FALSE
                                                                   AND urakka = :urakka
                                                             ORDER BY tarkastusaika DESC
                                                             LIMIT 1)
                                      AND skl.kohde = stk.kohde)
WHERE siltatarkastus = (SELECT id
                        FROM siltatarkastus st
                        WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                              AND urakka = :urakka
                              AND silta = :silta
                              AND st.poistettu = FALSE
                        ORDER BY tarkastusaika DESC
                        LIMIT 1)
ORDER BY kohde;

-- name: hae-hallintayksikon-siltatarkastukset
-- Hakee hallintayksikön siltatarkastukset valitulta vuodelta.
SELECT
  u.nimi,
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{A}'
         AND siltatarkastus IN (SELECT id
                               FROM siltatarkastus st
                               WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND urakka = u.id
                                     AND st.poistettu = FALSE)) AS "a",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{B}'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka = u.id
                                      AND st.poistettu = FALSE)) AS "b",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{C}'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka = u.id
                                      AND st.poistettu = FALSE)) AS "c",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{D}'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka = u.id
                                      AND st.poistettu = FALSE)) AS "d"
FROM urakka u
  WHERE u.hallintayksikko = :hallintayksikko AND u.tyyppi IN ('hoito', 'teiden-hoito')
  AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm) AND EXTRACT(YEAR FROM loppupvm)
ORDER BY u.nimi;

-- name: hae-koko-maan-siltatarkastukset
-- Hakee koko maan siltatarkastukset ELYittäin valitulta vuodelta
SELECT
  concat(lpad(cast(elynumero as varchar), 2, '0'), ' ', h.nimi) as nimi,
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{A}'
         AND siltatarkastus IN (SELECT id
                               FROM siltatarkastus st
                               WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND urakka IN (SELECT id
                                                    FROM urakka
                                                    WHERE hallintayksikko = h.id
                                                          AND tyyppi IN ('hoito', 'teiden-hoito')
                                                          AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                          AND EXTRACT(YEAR FROM loppupvm))
                                     AND st.poistettu = FALSE))     AS "a",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{B}'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko = h.id
                                                           AND tyyppi IN ('hoito', 'teiden-hoito')
                                                           AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                           AND EXTRACT(YEAR FROM loppupvm))
                                      AND st.poistettu = FALSE)) AS "b",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{C}'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko = h.id
                                                           AND tyyppi IN ('hoito', 'teiden-hoito')
                                                           AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                           AND EXTRACT(YEAR FROM loppupvm))
                                      AND st.poistettu = FALSE)) AS "c",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos && '{D}'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko = h.id
                                                           AND tyyppi IN ('hoito', 'teiden-hoito')
                                                           AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                           AND EXTRACT(YEAR FROM loppupvm))
                                      AND st.poistettu = FALSE)) AS "d"
FROM organisaatio h
  WHERE tyyppi = 'hallintayksikko'
  AND elynumero IS NOT NULL
ORDER BY h.elynumero;

-- name: hae-urakan-tarkastettujen-siltojen-lkm
SELECT
  (SELECT COUNT(*)
   FROM silta s
   WHERE urakat @> ARRAY[:urakka] ::INT[]) AS "sillat-lkm",

  (SELECT COUNT(*)
   FROM silta s
   WHERE urakat @> ARRAY[:urakka] ::INT[] AND
         EXISTS(SELECT tarkastusaika
                FROM siltatarkastus st
                WHERE st.silta = s.id AND
                      EXTRACT(YEAR FROM tarkastusaika) = :vuosi AND
                      st.poistettu = FALSE
                LIMIT 1)) AS "tarkastukset-lkm";
