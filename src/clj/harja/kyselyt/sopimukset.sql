-- name: luo-sopimus<!
-- Luo uuden sopimukset.
INSERT INTO sopimus (sampoid, nimi, alkupvm, loppupvm, urakka_sampoid, urakoitsija_sampoid)
VALUES (:sampoid, :nimi, :alkupvm, :loppupvm, :urakka_sampoid, :urakoitsija_sampoid);

-- name: paivita-sopimus!
-- Paivittaa sopimukset.
UPDATE sopimus
SET nimi              = :nimi,
  alkupvm             = :alkupvm,
  loppupvm            = :loppupvm,
  urakka_sampoid      = :urakka_sampoid,
  urakoitsija_sampoid = :urakoitsija_sampoid
WHERE id = :id;

-- name: hae-id-sampoidlla
-- Hakee sopimuksen id:n sampo id:llä
SELECT id
FROM sopimus
WHERE sampoid = :sampoid;