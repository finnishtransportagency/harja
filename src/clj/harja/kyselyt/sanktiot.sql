-- name: luo-sanktio<!
-- Luo uuden sanktion annetulle havainnolle
INSERT
  INTO sanktio
       (perintapvm, sakkoryhma, maara, indeksi, havainto)
VALUES (:perintapvm, :ryhma::sakkoryhma, :summa, :indeksi, :havainto)


-- name: hae-havainnon-sanktiot
-- Palauttaa kaikki annetun havainnon sanktiot
SELECT id, perintapvm, maara as summa, sakkoryhma as ryhma, indeksi
  FROM sanktio
 WHERE havainto = :havainto
