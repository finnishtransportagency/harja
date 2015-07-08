-- name: luo-organisaatio<!
-- Luo uuden organisaation.
INSERT INTO organisaatio (sampoid, nimi, ytunnus, katuosoite, postinumero)
VALUES (:sampoid, :nimi, :ytunnus, :katuosoite, :postinumero);

-- name: paivita-organisaatio!
-- Paivittaa organisaation.
UPDATE
  organisaatio
SET
  nimi        = :nimi,
  ytunnus     = :ytunnus,
  katuosoite  = :katuosoite,
  postinumero = :postinumero
WHERE
  id = :id;

-- name: hae-id-sampoidlla
-- Hakee organisaation id:n sampo id:llä
SELECT id
FROM organisaatio
WHERE sampoid = :sampoid;


-- name: hae-organisaatio
-- Hakee organisaation id:llä
SELECT id, nimi, lyhenne, ytunnus, liikennemuoto, katuosoite, postinumero,
       sampoid, elynumero, tyyppi
  FROM organisaatio
 WHERE id = :id;


