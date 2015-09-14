-- name: hae-lukko-idlla
-- Hakee lukon id:llä
SELECT m.id, m.kayttaja, m.aikaleima, k.etunimi, k.sukunimi,
       (EXTRACT(EPOCH FROM NOW()::TIMESTAMP WITHOUT TIME ZONE) - EXTRACT(EPOCH FROM aikaleima)) as ika
  FROM muokkauslukko m
  JOIN kayttaja k ON k.id = m.kayttaja
 WHERE m.id = :id;

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
