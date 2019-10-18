-- name: luo-sanktio<!
-- Luo uuden sanktion annetulle laatupoikkeamalle
INSERT
INTO sanktio
(perintapvm, sakkoryhma, tyyppi, toimenpideinstanssi, vakiofraasi, maara, indeksi, laatupoikkeama, suorasanktio, luoja, luotu)
VALUES (:perintapvm, :ryhma :: sanktiolaji, :tyyppi,
        COALESCE(
	  (SELECT t.id -- suoraan annettu tpi
	     FROM toimenpideinstanssi t
	    WHERE t.id = :tpi_id AND t.urakka = :urakka),
          (SELECT t.id
	     FROM toimenpideinstanssi t -- sanktiotyyppiin linkattu tpi
            JOIN sanktiotyyppi s ON s.toimenpidekoodi = t.toimenpide
           WHERE s.id = :tyyppi AND t.urakka = :urakka)),
        :vakiofraasi,
        :summa, :indeksi, :laatupoikkeama, :suorasanktio, :luoja, NOW());

-- name: paivita-sanktio!
-- Päivittää olemassaolevan sanktion
UPDATE sanktio
SET perintapvm        = :perintapvm,
  sakkoryhma          = :ryhma :: sanktiolaji,
  tyyppi              = :tyyppi,
  toimenpideinstanssi = COALESCE(
    (SELECT t.id FROM toimenpideinstanssi t WHERE t.id = :tpi_id AND t.urakka = :urakka),
    (SELECT t.id
       FROM toimenpideinstanssi t
       JOIN sanktiotyyppi s ON s.toimenpidekoodi = t.toimenpide
      WHERE s.id = :tyyppi AND t.urakka = :urakka)),
  vakiofraasi         = :vakiofraasi,
  maara               = :summa,
  indeksi             = :indeksi,
  laatupoikkeama            = :laatupoikkeama,
  suorasanktio        = :suorasanktio,
  muokkaaja = :muokkaaja,
  poistettu = :poistettu,
  muokattu = NOW()
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
  s.vakiofraasi,
  t.id              AS tyyppi_id,
  t.nimi            AS tyyppi_nimi,
  t.toimenpidekoodi AS tyyppi_toimenpidekoodi,
  t.sanktiolaji     AS tyyppi_laji
FROM sanktio s
  LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
WHERE laatupoikkeama = :laatupoikkeama
      AND s.poistettu IS NOT TRUE;

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
  s.vakiofraasi,

  lp.id                               AS laatupoikkeama_id,
  lp.kohde                            AS laatupoikkeama_kohde,
  lp.aika                             AS laatupoikkeama_aika,
  lp.tekija                           AS laatupoikkeama_tekija,
  lp.urakka                           AS laatupoikkeama_urakka,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS laatupoikkeama_tekijanimi,
  lp.kasittelyaika                    AS laatupoikkeama_paatos_kasittelyaika,
  lp.paatos                           AS laatupoikkeama_paatos_paatos,
  lp.kasittelytapa                    AS laatupoikkeama_paatos_kasittelytapa,
  lp.muu_kasittelytapa                AS laatupoikkeama_paatos_muukasittelytapa,
  lp.kuvaus                           AS laatupoikkeama_kuvaus,
  lp.perustelu                        AS laatupoikkeama_paatos_perustelu,
  lp.tr_numero                        AS laatupoikkeama_tr_numero,
  lp.tr_alkuosa                       AS laatupoikkeama_tr_alkuosa,
  lp.tr_loppuosa                      AS laatupoikkeama_tr_loppuosa,
  lp.tr_alkuetaisyys                  AS laatupoikkeama_tr_alkuetaisyys,
  lp.tr_loppuetaisyys                 AS laatupoikkeama_tr_loppuetaisyys,
  lp.sijainti                         AS laatupoikkeama_sijainti,
  lp.tarkastuspiste                   AS laatupoikkeama_tarkastuspiste,
  lp.selvitys_pyydetty                AS laatupoikkeama_selvityspyydetty,
  lp.selvitys_annettu                 AS laatupoikkeama_selvitysannettu,

  ypk.tr_numero        AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa       AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys  AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa      AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero      AS yllapitokohde_numero,
  ypk.nimi             AS yllapitokohde_nimi,
  ypk.id               AS yllapitokohde_id,

  t.nimi                             AS tyyppi_nimi,
  t.id                               AS tyyppi_id,
  t.toimenpidekoodi                  AS tyyppi_toimenpidekoodi

FROM sanktio s
  JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE
  lp.urakka = :urakka
  AND lp.poistettu IS NOT TRUE AND s.poistettu IS NOT TRUE
  AND s.perintapvm >= :alku AND s.perintapvm <= :loppu
  -- Ei kuulu poistettuun ylläpitokohteeseen
  AND (lp.yllapitokohde IS NULL
      OR
      lp.yllapitokohde IS NOT NULL AND
        (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: merkitse-maksuera-likaiseksi!
-- Merkitsee sanktiota vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
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
FROM sanktiotyyppi;

--name: hae-urakkatyypin-sanktiolajit
SELECT id, nimi, sanktiolaji, urakkatyyppi
  FROM sanktiotyyppi
 WHERE urakkatyyppi @> ARRAY[:urakkatyyppi::urakkatyyppi];

--name: hae-sanktiotyyppi-sanktiolajilla
SELECT id
  FROM sanktiotyyppi
 WHERE sanktiolaji @> ARRAY[:sanktiolaji::sanktiolaji];


--name: hae-sanktion-urakka-id
SELECT urakka FROM laatupoikkeama lp
JOIN sanktio s ON lp.id = s.laatupoikkeama
WHERE s.id = :sanktioid;
