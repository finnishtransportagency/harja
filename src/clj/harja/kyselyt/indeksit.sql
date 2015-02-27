-- name: listaa-indeksit
-- Hakee kaikki indeksit
SELECT nimi, vuosi, kuukausi, arvo
  FROM indeksi
  	ORDER BY nimi, vuosi, kuukausi