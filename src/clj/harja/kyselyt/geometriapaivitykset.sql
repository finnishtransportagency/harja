-- name: hae-paivitys
-- Hakee annetun integraatiotapahtuman viestit
SELECT *
FROM geometriapaivitys
WHERE nimi = :nimi;

-- name: paivita-viimeisin-paivitys
SELECT paivita_geometriapaivityksen_viimeisin_paivitys(:nimi, :viimeisin_paivitys :: TIMESTAMP);

-- name: hae-karttapvm
-- single?: true
SELECT viimeisin_paivitys
FROM geometriapaivitys
WHERE nimi = 'tieverkko';

-- name: poista-viimeisin-paivitys!
UPDATE geometriapaivitys
SET viimeisin_paivitys=NULL
WHERE nimi=:nimi;