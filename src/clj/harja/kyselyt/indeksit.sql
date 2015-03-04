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

-- name: luo-indeksi<!
-- Tekee uuden indeksin
INSERT INTO indeksi (nimi, vuosi, kuukausi, arvo)
     VALUES (:nimi, :vuosi, :kuukausi, :arvo)

-- name: paivita-indeksi!
-- Päivittää indeksin tiedot
UPDATE indeksi
   SET arvo=:arvo
 WHERE nimi = :nimi AND vuosi = :vuosi AND kuukausi = :kuukausi

-- name: poista-indeksi!
-- Poistaa indeksin
DELETE FROM indeksi 
	  WHERE nimi=:nimi AND vuosi=:vuosi AND kuukausi = :kuukausi
