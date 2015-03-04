-- name: listaa-indeksit
-- Hakee kaikki indeksit
SELECT nimi, vuosi, kuukausi, arvo
  FROM indeksi
  	ORDER BY nimi, vuosi, kuukausi

-- name: hae-indeksi
-- Hakee indeksin nimellä
  SELECT nimi, vuosi, kuukausi, arvo
    FROM indeksi
   WHERE nimi = :nimi
ORDER BY nimi, vuosi, kuukausi

-- name: poista-indeksi!
-- Poistaa indeksin
-- DELETE FROM indeksi WHERE nimi=:nimi AND vuosi=:vuosi TODO
