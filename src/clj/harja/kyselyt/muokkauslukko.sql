-- name: onko-olemassa-idlla
-- Tarkistaa löytyykö lukkoaa id:llä
SELECT exists(
    SELECT id
    FROM muokkauslukko
    WHERE id = :id);

-- name: hae-lukko-idlla
-- Hakee lukon id:llä
SELECT * FROM muokkauslukko
WHERE id = :id;

-- name: luo-lukko<!
-- Tekee uuden lukon
INSERT INTO muokkauslukko (id, kayttaja, aikaleima)
VALUES (:id, :kayttaja, NOW());

-- name: luo-lukko<!
-- Tekee uuden lukon
INSERT INTO muokkauslukko (id, kayttaja, aikaleima)
VALUES (:id, :kayttaja, NOW());

-- name: virkista-lukko!
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