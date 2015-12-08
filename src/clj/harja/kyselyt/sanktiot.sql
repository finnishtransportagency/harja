-- name: luo-sanktio<!
-- Luo uuden sanktion annetulle laatupoikkeamalle
INSERT
INTO sanktio
(perintapvm, sakkoryhma, tyyppi, toimenpideinstanssi, maara, indeksi, laatupoikkeama, suorasanktio)
VALUES (:perintapvm, :ryhma :: sanktiolaji, :tyyppi,
        (
          SELECT t.id
          FROM toimenpideinstanssi t
            JOIN sanktiotyyppi s ON s.toimenpidekoodi = t.toimenpide
          WHERE s.id = :tyyppi AND t.urakka = :urakka
        ),
        :summa, :indeksi, :laatupoikkeama, :suorasanktio);

-- name: paivita-sanktio!
-- Päivittää olemassaolevan sanktion
UPDATE sanktio
SET perintapvm        = :perintapvm,
  sakkoryhma          = :ryhma :: sanktiolaji,
  tyyppi              = :tyyppi,
  toimenpideinstanssi = (
    SELECT t.id
    FROM toimenpideinstanssi t
      JOIN sanktiotyyppi s ON s.toimenpidekoodi = t.toimenpide
    WHERE s.id = :tyyppi AND t.urakka = :urakka
  ),
  maara               = :summa,
  indeksi             = :indeksi,
  laatupoikkeama            = :laatupoikkeama,
  suorasanktio        = :suorasanktio
WHERE id = :id;

-- name: hae-laatupoikkeaman-sanktiot
-- Palauttaa kaikki annetun laatupoikkeaman sanktiot
SELECT
  s.id,
  s.perintapvm,
  s.maara           AS summa,
  s.sakkoryhma      AS laji,
  s.toimenpideinstanssi,
  s.indeksi,
  t.id              AS tyyppi_id,
  t.nimi            AS tyyppi_nimi,
  t.toimenpidekoodi AS tyyppi_toimenpidekoodi,
  t.sanktiolaji     AS tyyppi_sanktiolaji
FROM sanktio s
  JOIN sanktiotyyppi t ON s.tyyppi = t.id
WHERE laatupoikkeama = :laatupoikkeama;

-- name: hae-urakan-sanktiot
-- Palauttaa kaikki urakalle kirjatut sanktiot perintäpäivämäärällä ja toimenpideinstanssilla rajattuna
-- Käytetään siis mm. Laadunseuranta/sanktiot välilehdellä
SELECT
  s.id,
  s.perintapvm,
  s.maara                            AS summa,
  s.sakkoryhma                       AS laji,
  s.indeksi,
  s.suorasanktio,
  s.toimenpideinstanssi,

  h.id                               AS laatupoikkeama_id,
  h.kohde                            AS laatupoikkeama_kohde,
  h.aika                             AS laatupoikkeama_aika,
  h.tekija                           AS laatupoikkeama_tekija,
  h.urakka                           AS laatupoikkeama_urakka,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS laatupoikkeama_tekijanimi,
  h.kasittelyaika                    AS laatupoikkeama_paatos_kasittelyaika,
  h.paatos                           AS laatupoikkeama_paatos_paatos,
  h.kasittelytapa                    AS laatupoikkeama_paatos_kasittelytapa,
  h.muu_kasittelytapa                AS laatupoikkeama_paatos_muukasittelytapa,
  h.kuvaus                           AS laatupoikkeama_kuvaus,
  h.perustelu                        AS laatupoikkeama_paatos_perustelu,
  h.tr_numero                        AS laatupoikkeama_tr_numero,
  h.tr_alkuosa                       AS laatupoikkeama_tr_alkuosa,
  h.tr_loppuosa                      AS laatupoikkeama_tr_loppuosa,
  h.tr_alkuetaisyys                  AS laatupoikkeama_tr_alkuetaisyys,
  h.tr_loppuetaisyys                 AS laatupoikkeama_tr_loppuetaisyys,
  h.sijainti                         AS laatupoikkeama_sijainti,
  h.tarkastuspiste                   AS laatupoikkeama_tarkastuspiste,
  h.selvitys_pyydetty                AS laatupoikkeama_selvityspyydetty,
  h.selvitys_annettu                 AS laatupoikkeama_selvitysannettu,

  t.nimi                             AS tyyppi_nimi,
  t.id                               AS tyyppi_id,
  t.toimenpidekoodi                  AS tyyppi_toimenpidekoodi

FROM sanktio s
  JOIN laatupoikkeama h ON s.laatupoikkeama = h.id
  JOIN kayttaja k ON h.luoja = k.id
  JOIN sanktiotyyppi t ON s.tyyppi = t.id
WHERE
  h.urakka = :urakka
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
SELECT
  id,
  nimi,
  toimenpidekoodi,
  sanktiolaji AS laji
FROM sanktiotyyppi
