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
-- Palauttaa kaikki urakalle kirjatut sanktiot perintäpäivämäärällä ja toimenpideinstanssilla rajattuna
-- Käytetään siis mm. Laadunseuranta/sanktiot välilehdellä
SELECT
  s.id,
  s.perintapvm,
  s.maara      AS summa,
  s.sakkoryhma AS laji,
  s.indeksi,
  s.suorasanktio,

  h.id         AS havainto_id,
  h.kohde      AS havainto_kohde,
  h.aika AS havainto_aika,
  h.tekija AS havainto_tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS havainto_tekijanimi,
  h.kasittelyaika AS havainto_paatos_kasittelyaika,
  h.paatos AS havainto_paatos_paatos,
  h.kasittelytapa AS havainto_paatos_kasittelytapa,
  h.kuvaus AS havainto_kuvaus,

  t.nimi AS tyyppi_nimi,
  t.id AS tyyppi_id,
  t.toimenpidekoodi AS tyyppi_toimenpidekoodi
FROM sanktio s
  JOIN havainto h ON s.havainto = h.id
  JOIN kayttaja k ON h.luoja = k.id
  JOIN sanktiotyyppi t ON s.tyyppi = t.id
WHERE
  h.urakka = :urakka
  AND s.perintapvm >= :alku AND s.perintapvm <= :loppu
  AND s.toimenpideinstanssi = :tpi;

SELECT
  h.id,
  h.aika,
  h.kohde,
  h.tekija,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  h.kasittelyaika                    AS paatos_kasittelyaika,
  h.paatos                           AS paatos_paatos,
  h.kasittelytapa                    AS paatos_kasittelytapa,
  h.kuvaus
FROM havainto h
  JOIN kayttaja k ON h.luoja = k.id
  LEFT JOIN sanktio s ON h.id=s.havainto
WHERE h.urakka = :urakka
      AND (aika >= :alku AND aika <= :loppu)
      AND s.suorasanktio IS NOT TRUE;

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
