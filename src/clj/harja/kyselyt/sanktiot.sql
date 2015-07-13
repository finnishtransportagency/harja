-- name: luo-sanktio<!
-- Luo uuden sanktion annetulle havainnolle
INSERT
INTO sanktio
       (perintapvm, sakkoryhma, tyyppi, toimenpideinstanssi, maara, indeksi, havainto, suorasanktio)
VALUES (:perintapvm, :ryhma::sanktiolaji, :tyyppi,
        (SELECT id FROM toimenpideinstanssi WHERE id = :toimenpideinstanssi AND urakka = :urakka),
        :summa, :indeksi, :havainto, :suorasanktio);

-- name: paivita-sanktio!
-- Päivittää olemassaolevan sanktion
UPDATE sanktio
SET perintapvm = :perintapvm,
  sakkoryhma = :ryhma::sanktiolaji,
  tyyppi = :tyyppi,
  toimenpideinstanssi = (SELECT id FROM toimenpideinstanssi WHERE id = :toimenpideinstanssi AND urakka = :urakka),
  maara = :summa,
  indeksi = :indeksi,
  havainto = :havainto,
  suorasanktio = :suorasanktio
WHERE id = :id;

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
  s.toimenpideinstanssi,

  h.id         AS havainto_id,
  h.kohde      AS havainto_kohde, 
  h.aika AS havainto_aika, 
  h.tekija AS havainto_tekija, 
  h.urakka AS havainto_urakka,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS havainto_tekijanimi, 
  h.kasittelyaika AS havainto_paatos_kasittelyaika, 
  h.paatos AS havainto_paatos_paatos, 
  h.kasittelytapa AS havainto_paatos_kasittelytapa,
  h.muu_kasittelytapa AS havainto_paatos_muukasittelytapa,
  h.kuvaus AS havainto_kuvaus, 
  h.perustelu AS havainto_paatos_perustelu, 
  h.tr_numero AS havainto_tr_numero,
  h.tr_alkuosa AS havainto_tr_alkuosa,
  h.tr_loppuosa AS havainto_tr_loppuosa,
  h.tr_alkuetaisyys AS havainto_tr_alkuetaisyys,
  h.tr_loppuetaisyys AS havainto_tr_loppuetaisyys,
  h.sijainti AS havainto_sijainti,
  h.tarkastuspiste AS havainto_tarkastuspiste,
  h.selvitys_pyydetty AS havainto_selvityspyydetty,
  h.selvitys_annettu AS havainto_selvitysannettu,

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
