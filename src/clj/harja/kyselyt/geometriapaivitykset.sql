-- name: hae-paivitys
-- Hakee geometriapäivityksen tiedot nimellä
SELECT *
  FROM geometriapaivitys
 WHERE nimi = :nimi;

-- name: paivita-viimeisin-paivitys
SELECT paivita_geometriapaivityksen_viimeisin_paivitys(:nimi, :viimeisin_paivitys :: TIMESTAMP);

-- name: paivita-viimeisin-lahde!
-- Päivittää geometriapäivityksen viimeisimmän lähteen (tiedostopolku tai URL)
INSERT INTO geometriapaivitys (nimi, viimeisin_lahde)
     VALUES (:nimi, :viimeisin_lahde)
         ON CONFLICT (nimi)
         DO UPDATE
                SET viimeisin_lahde = EXCLUDED.viimeisin_lahde;

-- name: hae-karttapvm
-- single?: true
SELECT viimeisin_paivitys
  FROM geometriapaivitys
 WHERE nimi = 'tieverkko';
