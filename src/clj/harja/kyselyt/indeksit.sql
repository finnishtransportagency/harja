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
SELECT id, urakkatyyppi, indeksinimi, materiaali, koodi
  FROM urakkatyypin_indeksi;


--name: hae-paallystysurakan-indeksit
SELECT pui.id,
       urakkavuosi,lahtotaso_vuosi,lahtotaso_kuukausi,urakka,
  raskasoljy.id as raskas_id, raskasoljy.indeksinimi as raskas_indeksinimi,
  raskasoljy.materiaali as raskas_materiaali, raskasoljy.koodi as raskas_koodi,
  kevytoljy.id as kevyt_id, kevytoljy.indeksinimi as kevyt_indeksinimi,
  kevytoljy.materiaali as kevyt_materiaali, kevytoljy.koodi as kevyt_koodi,
  nestekaasu.id as nestekaasu_id, nestekaasu.indeksinimi as nestekaasu_indeksinimi,
  nestekaasu.materiaali as nestekaasu_materiaali, nestekaasu.koodi as nestekaasu_koodi
  FROM paallystysurakan_indeksit pui
    LEFT JOIN urakkatyypin_indeksi raskasoljy ON raskasoljy.id = pui.indeksi_polttooljyraskas
    LEFT JOIN urakkatyypin_indeksi kevytoljy ON kevytoljy.id = pui.indeksi_polttooljykevyt
    LEFT JOIN urakkatyypin_indeksi nestekaasu ON nestekaasu.id = pui.indeksi_nestekaasu

 WHERE urakka = :urakka and poistettu IS NOT TRUE;

--name: upsert-paallystysurakan-indeksit
INSERT INTO paallystysurakan_indeksit
(indeksi_polttooljyraskas,indeksi_polttooljykevyt,indeksi_nestekaasu,
 urakkavuosi,lahtotaso_vuosi,lahtotaso_kuukausi,urakka, luoja, luotu)
VALUES (:indeksi_polttooljyraskas,:indeksi_polttooljykevyt,:indeksi_nestekaasu,
        :urakkavuosi,:lahtotaso_vuosi,:lahtotaso_kuukausi,:urakka, :kayttaja, NOW())
ON CONFLICT ON CONSTRAINT uniikki_paallystysindeksi
  DO UPDATE SET indeksi_polttooljyraskas = :indeksi_polttooljyraskas,indeksi_polttooljykevyt = :indeksi_polttooljykevyt,indeksi_nestekaasu = :indeksi_nestekaasu,
    urakkavuosi = :urakkavuosi,lahtotaso_vuosi = :lahtotaso_vuosi,lahtotaso_kuukausi = :lahtotaso_kuukausi,urakka = :urakka,
    muokkaaja = :kayttaja, muokattu = NOW();
