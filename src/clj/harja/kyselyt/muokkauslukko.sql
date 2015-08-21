-- name: hae-lukko-idlla
-- Hakee lukon id:llä
SELECT
muokkauslukko.id as "id",
muokkauslukko.kayttaja as "kayttaja",
aikaleima,
etunimi,
sukunimi
FROM muokkauslukko
  JOIN kayttaja ON kayttaja.id = muokkauslukko.kayttaja
WHERE muokkauslukko.id = :id;

-- name: luo-lukko<!
-- Tekee uuden lukon
INSERT INTO muokkauslukko (id, kayttaja, aikaleima)
VALUES (:id, :kayttaja, NOW());

-- name: virkista-lukko<!
-- Virkistää lukon aikaleiman
UPDATE muokkauslukko
   SET aikaleima = NOW()
 WHERE id = :id
 AND kayttaja = :kayttaja;

-- name: vapauta-lukko!
-- Vapauttaa lukon
DELETE FROM muokkauslukko
WHERE id = :id
AND kayttaja = :kayttaja