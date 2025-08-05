-- name: listaa-indeksit
-- Hakee kaikki indeksit
SELECT nimi, vuosi, kuukausi, arvo
  FROM indeksi
  	ORDER BY nimi, vuosi, kuukausi;

-- name: hae-indeksi
-- Hakee indeksin nimellä
  SELECT nimi, vuosi, kuukausi, arvo
    FROM indeksi
   WHERE nimi = :nimi
ORDER BY nimi, vuosi, kuukausi;

--name: hae-urakan-kuukauden-indeksiarvo
SELECT arvo, nimi
  FROM indeksi
 WHERE nimi = (SELECT indeksi FROM urakka where id = :urakka_id)
       AND vuosi = :vuosi AND kuukausi = :kuukausi ;

-- name: luo-indeksi<!
-- Tekee uuden indeksin
INSERT INTO indeksi (nimi, vuosi, kuukausi, arvo)
     VALUES (:nimi, :vuosi, :kuukausi, :arvo);

-- name: paivita-indeksi!
-- Päivittää indeksin tiedot
UPDATE indeksi
   SET arvo=:arvo
 WHERE nimi = :nimi AND vuosi = :vuosi AND kuukausi = :kuukausi

-- name: poista-indeksi!
-- Poistaa indeksin
DELETE FROM indeksi
	  WHERE nimi=:nimi AND vuosi=:vuosi AND kuukausi = :kuukausi

--name: hae-urakkatyypin-indeksit
SELECT id, urakkatyyppi, indeksinimi, raakaaine, koodi
  FROM urakkatyypin_indeksi;


--name: hae-paallystysurakan-indeksitiedot
SELECT pui.id,
       urakka,
       pui.lahtotason_vuosi as "lahtotason-vuosi",
       pui.lahtotason_kuukausi as "lahtotason-kuukausi",
       uti.id AS indeksi_id,
       uti.indeksinimi AS indeksi_indeksinimi,
       i.arvo AS indeksi_arvo,
       uti.raakaaine AS indeksi_raakaaine,
       uti.urakkatyyppi AS indeksi_urakkatyyppi
  FROM paallystysurakan_indeksi pui
       JOIN urakkatyypin_indeksi uti ON pui.indeksi = uti.id
       LEFT JOIN indeksi i ON (uti.indeksinimi = i.nimi AND pui.lahtotason_vuosi = i.vuosi AND pui.lahtotason_kuukausi = i.kuukausi)
 WHERE urakka = :urakka and pui.poistettu IS NOT TRUE;

--name: tallenna-paallystysurakan-indeksitiedot!
INSERT
  INTO paallystysurakan_indeksi
       (indeksi,lahtotason_vuosi,lahtotason_kuukausi,urakka, luoja, luotu)
VALUES (:indeksi,:lahtotason-vuosi,:lahtotason-kuukausi,:urakka, :kayttaja, NOW())
ON CONFLICT (urakka, indeksi) WHERE poistettu IS NOT TRUE DO
UPDATE SET indeksi = :indeksi,
   	   lahtotason_vuosi = :lahtotason-vuosi,
    	   lahtotason_kuukausi = :lahtotason-kuukausi,
    	   urakka = :urakka,
    	   muokkaaja = :kayttaja,
    	   poistettu = :poistettu,
    	   muokattu = NOW();

--name: hae-paallystysurakan-indeksin-urakka-id
SELECT urakka
  FROM paallystysurakan_indeksi
 WHERE id = :id;

-- name: hae-urakan-indeksin-perusluku
SELECT indeksilaskennan_perusluku(:urakka-id::INTEGER) AS perusluku;

-- name: hae-urakan-hoitovuoden-indeksit-kuukausinimilla
-- Hae indeksi ja nimeä se kuukauden mukaan
SELECT vuosi, kuukausi, arvo as indeksiluku
FROM indeksi
WHERE (
    (vuosi = :vuosi AND kuukausi between 10 and 12)
        OR
    (vuosi - 1 = :vuosi AND kuukausi between 1 and 9))
  AND nimi = (SELECT indeksi FROM urakka WHERE id = :urakkaid)
ORDER BY vuosi, kuukausi;
