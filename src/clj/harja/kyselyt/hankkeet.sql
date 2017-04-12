-- name: luo-hanke<!
-- Luo uuden hankkeen.
INSERT INTO hanke (nimi, alkupvm, loppupvm, sampoid)
VALUES (:nimi, :alkupvm, :loppupvm, :sampoid);

-- name: paivita-hanke-samposta!
-- Paivittaa hankkeen Samposta saaduilla tiedoilla
UPDATE hanke
SET nimi = :nimi, alkupvm = :alkupvm, loppupvm = :loppupvm
WHERE sampoid = :sampoid;

-- name: onko-tuotu-samposta
-- Tarkistaa onko hanke jo tuotu Samposta
SELECT exists(
    SELECT hanke.id
    FROM hanke
    WHERE sampoid = :sampoid);

-- name:hae-sampo-tyypit
-- Hakee Sampo tyypit Sampo id:llä
SELECT sampo_tyypit
FROM hanke
WHERE sampoid = :sampoid;

-- name: hae-paattymattomat-vesivaylahankkeet
SELECT *
FROM hanke
WHERE loppupvm > now();

-- name: hae-harjassa-luodut-hankkeet
SELECT
    id,
    nimi,
    alkupvm,
    loppupvm
FROM hanke;