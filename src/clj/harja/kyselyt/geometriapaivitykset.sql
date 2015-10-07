-- name: hae-paivitys
-- Hakee annetun integraatiotapahtuman viestit
SELECT *
FROM geometriapaivitys
WHERE nimi = :nimi;
