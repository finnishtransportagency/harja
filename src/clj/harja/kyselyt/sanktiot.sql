-- name: luo-sanktio<!
-- Luo uuden sanktion annetulle havainnolle
INSERT
INTO sanktio
(perintapvm, sakkoryhma, maara, indeksi, havainto)
VALUES (:perintapvm, :ryhma :: sakkoryhma, :summa, :indeksi, :havainto);


-- name: hae-havainnon-sanktiot
-- Palauttaa kaikki annetun havainnon sanktiot
SELECT
  id,
  perintapvm,
  maara      AS summa,
  sakkoryhma AS ryhma,
  indeksi
FROM sanktio
WHERE havainto = :havainto;

-- name: hae-urakan-sanktiot
-- Palauttaa kaikki urakalle kirjatut sanktiot perintäpäivämäärällä rajattuna
SELECT
  s.id,
  s.perintapvm,
  s.maara      AS summa,
  s.sakkoryhma AS laji,
  s.indeksi,
  h.id         AS havainto_id,
  h.kohde      AS havainto_kohde
FROM sanktio s
  JOIN havainto h ON s.havainto = h.id
  JOIN toimenpideinstanssi tpi ON h.toimenpideinstanssi = tpi.id
WHERE tpi.urakka = :urakka
      AND s.perintapvm >= :alku AND s.perintapvm <= :loppu;

-- name: merkitse-maksuera-likaiseksi!
-- Merkitsee sanktiota vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE tyyppi = 'sakko' AND
      toimenpideinstanssi IN (
        SELECT toimenpideinstanssi
        FROM sanktio
        WHERE id = :sanktio);

-- name: hae-sanktiotyypit
-- Hakee kaikki sanktiotyypit
SELECT id, nimi, toimenpidekoodi, sanktiolaji as laji FROM sanktiotyyppi
