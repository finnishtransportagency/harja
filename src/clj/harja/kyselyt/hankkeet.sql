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

-- name: hae-harjassa-luodut-hankkeet
SELECT
  h.id,
  h.nimi,
  h.alkupvm,
  h.loppupvm,
  u.nimi AS urakka_nimi,
  u.id AS urakka_id
FROM hanke h
  LEFT JOIN urakka u ON h.id = u.hanke
  WHERE h.harjassa_luotu IS TRUE
ORDER BY h.alkupvm DESC, h.nimi;

-- name: luo-harjassa-luotu-hanke<!
INSERT INTO hanke (nimi, alkupvm, loppupvm, luoja, luotu, harjassa_luotu)
VALUES (:nimi, :alkupvm, :loppupvm, :kayttaja, NOW(), TRUE);

-- name: paivita-harjassa-luotu-hanke<!
UPDATE hanke
SET
  nimi      = :nimi,
  alkupvm   = :alkupvm,
  loppupvm  = :loppupvm,
  muokkaaja = :kayttaja,
  muokattu  = NOW()
WHERE id = :id;
