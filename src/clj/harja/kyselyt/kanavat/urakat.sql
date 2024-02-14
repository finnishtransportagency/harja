-- name: hae-kayttajan-kanavaurakat
-- Palauttaa käyttäjän kanavaurakat 
SELECT u.id        AS urakka_id,
       u.nimi      AS urakka_nimi,
       u.tyyppi    AS tyyppi,
       o.id        AS hallintayksikko_id,
       o.nimi      AS hallintayksikko_nimi,
       o.elynumero AS hallintayksikko_elynumero,
       u.urakkanro AS urakka_urakkanro
FROM urakka u
         JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE (u.tyyppi = 'vesivayla-kanavien-hoito')
  AND (:hallintayksikko_annettu = FALSE OR u.hallintayksikko IN (:hallintayksikko))
  AND u.poistettu = FALSE;
