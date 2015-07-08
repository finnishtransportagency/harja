-- name: luo-sanktio<!
-- Luo uuden sanktion annetulle havainnolle
INSERT
INTO sanktio
       (perintapvm, sakkoryhma, tyyppi, toimenpideinstanssi, maara, indeksi, havainto)
VALUES (:perintapvm, :ryhma::sanktiolaji, :tyyppi,
        (SELECT id FROM toimenpideinstanssi WHERE id = :toimenpideinstanssi AND urakka = :urakka),
        :summa, :indeksi, :havainto);


-- name: hae-havainnon-sanktiot
-- Palauttaa kaikki annetun havainnon sanktiot
SELECT
  s.id,
  s.perintapvm,
  s.maara      AS summa,
  s.sakkoryhma AS laji,
  s.toimenpideinstanssi,
  s.indeksi,
  t.id as tyyppi_id, t.nimi as tyyppi_nimi, t.toimenpidekoodi as tyyppi_toimenpidekoodi,
  t.sanktiolaji as tyyppi_sanktiolaji  
FROM sanktio s
     JOIN sanktiotyyppi t ON s.tyyppi = t.id
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
WHERE h.urakka = :urakka
      AND s.perintapvm >= :alku AND s.perintapvm <= :loppu;

-- name: hae-urakan-sanktiot-hoitokaudella-ja-toimenpiteella
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
  JOIN toimenpideinstanssi ti ON h.urakka = ti.urakka
WHERE h.urakka = :urakka
      AND s.perintapvm >= :alku AND s.perintapvm <= :loppu
      AND ti.id = :toimenpideinstanssi;

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
