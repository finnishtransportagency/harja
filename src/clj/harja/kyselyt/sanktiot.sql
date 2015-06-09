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

-- name: hae-urakan-sanktiot
-- Palauttaa kaikki urakalle kirjatut sanktiot perintäpäivämäärällä rajattuna
SELECT s.id, s.perintapvm, s.maara as summa, s.sakkoryhma as ryhma, s.indeksi,
       h.id as havainto_id, h.kohde as havainto_kohde
  FROM sanktio s
       JOIN havainto h ON s.havainto = h.id
       JOIN toimenpideinstanssi tpi ON h.toimenpideinstanssi = tpi.id
 WHERE tpi.urakka = :urakka
   AND s.perintapvm >= :alku AND s.perintapvm <= :loppu
