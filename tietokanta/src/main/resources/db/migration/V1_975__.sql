-- Sanktiotyyppien korjaus VHAR-7050

-- Palauta takaisin 'Ei tarvita sanktiotyyppiä' sanktiotyyppi, joka oli vahingossa merkitty poistetuksi
UPDATE sanktiotyyppi
   SET poistettu = FALSE
 WHERE koodi = 0;

-- Merkitse talvihoito-tyyppi poistetuksi
UPDATE sanktiotyyppi
   SET poistettu = TRUE
 WHERE koodi = 2;
