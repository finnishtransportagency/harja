-- name: hae-urakan-siltatarkastukset
-- Hakee urakan kaikki sillat ja niiden annettuna vuonna tehdyn uusimman siltatarkastuksen
SELECT
  s.id,
  siltanro,
  siltanimi,
  s1.tarkastusaika,
  s1.tarkastaja,
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'A'
         AND siltatarkastus = s1.id) AS "a",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'B'
         AND siltatarkastus = s1.id) AS "b",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'C'
         AND siltatarkastus = s1.id) AS "c",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'D'
         AND siltatarkastus = s1.id) AS "d",
  l.id AS liite_id,
  l.tyyppi AS liite_tyyppi,
  l.koko AS liite_koko,
  l.nimi AS liite_nimi
FROM silta s
  -- Sillan uusin siltatarkastus: s1
  LEFT JOIN siltatarkastus s1 ON (s1.silta = s.id
                                  AND EXTRACT(YEAR FROM s1.tarkastusaika) = :vuosi
                                  AND s1.urakka = :urakka
                                  AND s1.poistettu = FALSE)
  LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id
                                  AND s2.tarkastusaika > s1.tarkastusaika
                                  AND EXTRACT(YEAR FROM s2.tarkastusaika) = :vuosi
                                  AND s2.urakka = :urakka
                                  AND s2.poistettu = FALSE)
  LEFT JOIN liite l ON l.id IN (SELECT liite
                                FROM siltatarkastus_kohde_liite
                                WHERE siltatarkastus = s1.id)
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka)
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
   WHERE tulos = 'A'
         AND siltatarkastus IN (SELECT id
                               FROM siltatarkastus st
                               WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND urakka = u.id
                                     AND st.poistettu = FALSE)) AS "a",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'B'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka = u.id
                                      AND st.poistettu = FALSE)) AS "b",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'C'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka = u.id
                                      AND st.poistettu = FALSE)) AS "c",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'D'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka = u.id
                                      AND st.poistettu = FALSE)) AS "d"
FROM urakka u
  WHERE u.hallintayksikko = :hallintayksikko AND u.tyyppi = 'hoito'
  AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm) AND EXTRACT(YEAR FROM loppupvm)
ORDER BY u.nimi

-- name: hae-koko-maan-siltatarkastukset
-- Hakee koko maan siltatarkastukset ELYittäin valitulta vuodelta
SELECT
  concat(lpad(cast(elynumero as varchar), 2, '0'), ' ', h.nimi) as nimi,
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'A'
         AND siltatarkastus IN (SELECT id
                               FROM siltatarkastus st
                               WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                     AND urakka IN (SELECT id
                                                    FROM urakka
                                                    WHERE hallintayksikko = h.id
                                                          AND tyyppi = 'hoito'
                                                          AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                          AND EXTRACT(YEAR FROM loppupvm))
                                     AND st.poistettu = FALSE))     AS "a",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'B'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko = h.id
                                                           AND tyyppi = 'hoito'
                                                           AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                           AND EXTRACT(YEAR FROM loppupvm))
                                      AND st.poistettu = FALSE)) AS "b",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'C'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko = h.id
                                                           AND tyyppi = 'hoito'
                                                           AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                           AND EXTRACT(YEAR FROM loppupvm))
                                      AND st.poistettu = FALSE)) AS "c",
  (SELECT COUNT(*)
   FROM siltatarkastuskohde
   WHERE tulos = 'D'
         AND siltatarkastus IN (SELECT id
                                FROM siltatarkastus st
                                WHERE EXTRACT(YEAR FROM tarkastusaika) = :vuosi
                                      AND urakka IN (SELECT id
                                                     FROM urakka
                                                     WHERE hallintayksikko = h.id
                                                           AND tyyppi = 'hoito'
                                                           AND :vuosi BETWEEN EXTRACT(YEAR FROM alkupvm)
                                                           AND EXTRACT(YEAR FROM loppupvm))
                                      AND st.poistettu = FALSE)) AS "d"
FROM organisaatio h
  WHERE tyyppi = 'hallintayksikko'
ORDER BY h.elynumero;