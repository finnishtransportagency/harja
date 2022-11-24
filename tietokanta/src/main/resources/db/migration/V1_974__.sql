-- Sanktiotyyppien korjaus VHAR-7050

-- Palauta takaisin 'Ei tarvita sanktiotyyppiä' sanktiotyyppi, joka oli vahingossa merkitty poistetuksi
UPDATE sanktiotyyppi
   SET poistettu = FALSE
 WHERE koodi = 0;

-- Merkitse talvihoito-tyyppi poistetuksi
UPDATE sanktiotyyppi
   SET poistettu = TRUE
 WHERE koodi = 2;


-- Muuta vanhat Talvihoito-tyyppiset sanktiot 'Talvihoito, muut tiet'-tyyppisiksi
UPDATE sanktio
   SET tyyppi = (SELECT id FROM sanktiotyyppi WHERE koodi = 14)
 WHERE tyyppi = 2 -- 'Talvihoito'
   AND perintapvm >= '2021-10-01';
