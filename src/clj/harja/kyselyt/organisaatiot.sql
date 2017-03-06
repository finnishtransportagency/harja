-- name: luo-organisaatio<!
-- Luo uuden organisaation.
INSERT INTO organisaatio (sampoid, nimi, ytunnus, katuosoite, postinumero, tyyppi)
VALUES (:sampoid, :nimi, :ytunnus, :katuosoite, :postinumero, :tyyppi::organisaatiotyyppi);

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

-- name: hae-id-y-tunnuksella
-- Hakee organisaation id:n y-tunnuksella
SELECT id
FROM organisaatio
WHERE ytunnus = :ytunnus;

-- name: hae-id-sampoidlla
-- Hakee organisaation id:n sampo id:llä
SELECT id
FROM organisaatio
WHERE sampoid = :sampoid;


-- name: hae-organisaatio
-- Hakee organisaation id:llä
SELECT
  id,
  nimi,
  lyhenne,
  ytunnus,
  liikennemuoto,
  katuosoite,
  postinumero,
  sampoid,
  elynumero,
  tyyppi
FROM organisaatio
WHERE id = :id;

-- name: hae-ely
SELECT *
FROM organisaatio
WHERE elynumero = :elynumero;

-- name: luo-ely<!
INSERT INTO organisaatio (nimi, lyhenne, liikennemuoto, elynumero, alue, tyyppi)
VALUES (:nimi, :lyhenne, :liikennemuoto :: liikennemuoto, :elynumero, ST_GeomFromText(:alue) :: GEOMETRY,
        'hallintayksikko' :: organisaatiotyyppi)

-- name: paivita-ely!
UPDATE organisaatio
SET
  nimi          = :nimi,
  lyhenne       = :lyhenne,
  liikennemuoto = :liikennemuoto :: liikennemuoto,
  elynumero     = :elynumero,
  alue          = ST_GeomFromText(:alue) :: GEOMETRY
WHERE elynumero = :elynumero;

-- name: hae-ely-id-sampo-hashilla
SELECT id
FROM organisaatio
WHERE sampo_ely_hash = :ely_hash;

-- name: listaa-organisaatiot
SELECT
  id,
  ytunnus,
  nimi,
  tyyppi
FROM organisaatio;