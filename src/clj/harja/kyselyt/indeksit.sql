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

--name: hae-urakan-kuukauden-indeksiarvo
SELECT arvo, nimi
  FROM indeksi
 WHERE nimi = (SELECT indeksi FROM urakka where id = :urakka_id)
       AND vuosi = :vuosi AND kuukausi = :kuukausi ;

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

-- name: hae-indeksien-nimet
-- Hakee indeksien nimet
SELECT DISTINCT nimi
  FROM indeksi

--name: hae-urakkatyypin-indeksit
SELECT id, urakkatyyppi, indeksinimi, raakaaine, koodi
  FROM urakkatyypin_indeksi;


--name: hae-paallystysurakan-indeksitiedot
SELECT
  pui.id,
  urakka, urakkavuosi,
  lahtotason_vuosi as "lahtotason-vuosi", lahtotason_kuukausi as "lahtotason-kuukausi",

  raskasoljy.id as raskas_id, raskasoljy.indeksinimi as raskas_indeksinimi,
  raskasoljy.raakaaine as raskas_raakaaine, raskasoljy.koodi as raskas_koodi,

  kevytoljy.id as kevyt_id, kevytoljy.indeksinimi as kevyt_indeksinimi,
  kevytoljy.raakaaine as kevyt_raakaaine, kevytoljy.koodi as kevyt_koodi,

  nestekaasu.id as nestekaasu_id, nestekaasu.indeksinimi as nestekaasu_indeksinimi,
  nestekaasu.raakaaine as nestekaasu_raakaaine, nestekaasu.koodi as nestekaasu_koodi
  FROM paallystysurakan_indeksit pui
    LEFT JOIN urakkatyypin_indeksi raskasoljy ON raskasoljy.id = pui.indeksi_polttooljyraskas
    LEFT JOIN urakkatyypin_indeksi kevytoljy ON kevytoljy.id = pui.indeksi_polttooljykevyt
    LEFT JOIN urakkatyypin_indeksi nestekaasu ON nestekaasu.id = pui.indeksi_nestekaasu

 WHERE urakka = :urakka and poistettu IS NOT TRUE;

--name: upsert-paallystysurakan-indeksitiedot
INSERT INTO paallystysurakan_indeksit
(indeksi_polttooljyraskas,indeksi_polttooljykevyt,indeksi_nestekaasu,
 urakkavuosi,lahtotason_vuosi,lahtotason_kuukausi,urakka, luoja, luotu)
VALUES (:indeksi_polttooljyraskas,:indeksi_polttooljykevyt,:indeksi_nestekaasu,
        :urakkavuosi,:lahtotason_vuosi,:lahtotason_kuukausi,:urakka, :kayttaja, NOW())
ON CONFLICT ON CONSTRAINT uniikki_paallystysindeksi
  DO UPDATE SET indeksi_polttooljyraskas = :indeksi_polttooljyraskas,indeksi_polttooljykevyt = :indeksi_polttooljykevyt,indeksi_nestekaasu = :indeksi_nestekaasu,
    urakkavuosi = :urakkavuosi,lahtotason_vuosi = :lahtotason_vuosi,lahtotason_kuukausi = :lahtotason_kuukausi,urakka = :urakka,
    muokkaaja = :kayttaja, muokattu = NOW();
