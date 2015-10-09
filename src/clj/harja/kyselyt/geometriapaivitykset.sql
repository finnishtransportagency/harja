-- name: hae-paivitys
-- Hakee annetun integraatiotapahtuman viestit
SELECT *
FROM geometriapaivitys
WHERE nimi = :nimi;

-- name: paivita-viimeisin-paivitys<!
UPDATE geometriapaivitys
SET viimeisin_paivitys = :viimeisin_paivitys
WHERE nimi = :nimi;

-- name: lukitse-paivitys!
UPDATE geometriapaivitys
SET lukko = :lukko, lukittu = current_timestamp
WHERE nimi = :nimi AND (lukko IS NULL OR
                        (EXTRACT(EPOCH FROM (current_timestamp - lukittu)) > 300));

-- name: avaa-paivityksen-lukko!
UPDATE geometriapaivitys
SET lukko = NULL, lukittu = NULL
WHERE nimi = :nimi;
