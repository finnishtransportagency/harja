-- name: hae-paivitys
-- Hakee annetun integraatiotapahtuman viestit
SELECT *
FROM geometriapaivitys
WHERE nimi = :nimi;

-- name: paivita-viimeisin-paivitys<!
UPDATE geometriapaivitys
SET viimeisin_paivitys = :viimeisin_paivitys
WHERE nimi = :nimi;

-- name: hae-karttapvm
-- single?: true
SELECT viimeisin_paivitys
FROM geometriapaivitys
WHERE nimi = 'tieverkko';