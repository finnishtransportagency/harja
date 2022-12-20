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
  laatupoikkeama      = :laatupoikkeama,
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
  t.koodi           AS tyyppi_koodi
FROM sanktio s
  LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
WHERE laatupoikkeama = :laatupoikkeama
      AND s.poistettu IS NOT TRUE;

-- name: hae-suorasanktion-tiedot
-- Hae yksittäisen suora sanktion tiedot
SELECT s.id,
       s.perintapvm,
       s.maara AS summa,
       s.sakkoryhma AS laji,
       s.suorasanktio,
       s.toimenpideinstanssi,
       s.indeksi,
       s.vakiofraasi,
       s.laatupoikkeama AS laatupoikkeama_id,
       t.id AS tyyppi_id,
       t.nimi AS tyyppi_nimi,
       t.toimenpidekoodi AS tyyppi_toimenpidekoodi,
       t.koodi AS tyyppi_koodi
  FROM sanktio s
           LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
 WHERE s.id = :id;

-- name: poista-sanktio!
UPDATE sanktio
   SET poistettu = TRUE,
       muokattu  = NOW(),
       muokkaaja = :muokkaaja
 WHERE id = :id;


-- name: hae-urakan-sanktiot
-- row-fn: muunna-urakan-sanktio
-- Palauttaa kaikki urakalle kirjatut sanktiot perintäpäivämäärällä ja toimenpideinstanssilla rajattuna
-- Käytetään siis mm. Laadunseuranta/sanktiot välilehdellä
SELECT
  s.id,
  s.perintapvm,
  -- Haetaan kasittelyaika sanktioiden ja bonusten listausta varten.
  -- Huomaa, että sama käsittelyaika haetaan myös erikseen hierarkiana laatupoikkeamaa varten ja sitä käytetään lomakkeella
  -- sanktion laatupoikkeamassa.
  lp.kasittelyaika                    AS kasittelyaika,
  s.maara                             AS summa,
  s.sakkoryhma::text                  AS laji,
  s.indeksi,
  s.suorasanktio,
  s.toimenpideinstanssi,
  s.vakiofraasi,
  (SELECT korotus FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi,s.maara, u.id, s.sakkoryhma)) AS indeksikorjaus,
  lp.id                               AS laatupoikkeama_id,
  lp.kohde                            AS laatupoikkeama_kohde,
  lp.aika                             AS laatupoikkeama_aika,
  lp.tekija                           AS laatupoikkeama_tekija,
  lp.urakka                           AS laatupoikkeama_urakka,
  CONCAT(k.etunimi, ' ', k.sukunimi)  AS laatupoikkeama_tekijanimi,
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

  ypk.tr_numero                       AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa                      AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys                 AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa                     AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys                AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero                     AS yllapitokohde_numero,
  ypk.nimi                            AS yllapitokohde_nimi,
  ypk.id                              AS yllapitokohde_id,

  t.nimi                              AS tyyppi_nimi,
  t.id                                AS tyyppi_id,
  t.toimenpidekoodi                   AS tyyppi_toimenpidekoodi,
  t.koodi                             AS tyyppi_koodi

FROM sanktio s
  JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
  JOIN urakka u ON lp.urakka = u.id
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN sanktiotyyppi t ON s.tyyppi = t.id
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
WHERE
  lp.urakka = :urakka
  -- Ei haeta tässä ylläpidon bonus 'sanktioita', vaan haetaan ne bonuksina eri kyselyssä.
  --   Tämä edistää ylläpidon bonusten käsittelyn refaktorointia myöhemmin siten, että niitäkin käsiteltäisiin samalla
  --   logiikalla kuin muidenkin urakkatyyppien bonuksia.
  AND s.sakkoryhma != 'yllapidon_bonus'::SANKTIOLAJI
  AND lp.poistettu IS NOT TRUE AND s.poistettu IS NOT TRUE
  AND (s.perintapvm >= :alku AND s.perintapvm <= :loppu
   -- VHAR-5849 halutaan että urakan päättymisen jälkeiset sanktiot näkyvät viimeisen hoitokauden listauksessa
   OR
        (CASE
                    date_part('year', :loppu::date)::integer = date_part('year', u.loppupvm)::integer
                AND date_part('month', :loppu::date)::integer = date_part('month', u.loppupvm)::integer
             WHEN TRUE THEN s.perintapvm > u.loppupvm
             ELSE FALSE
            END))
        -- Ei kuulu poistettuun ylläpitokohteeseen
        AND (lp.yllapitokohde IS NULL
        OR
             lp.yllapitokohde IS NOT NULL AND
             (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS FALSE);

-- name: hae-urakan-bonukset
-- row-fn: muunna-urakan-bonus
-- Palauttaa kaikki urakalle kirjatut bonukset perintäpäivämäärällä ja toimenpideinstanssilla rajattuna
-- Käytetään siis mm. Laadunseuranta/sanktiot välilehdellä

-- Bonukset erilliskustannuksista
SELECT ek.id,
       ek.laskutuskuukausi    as perintapvm,
       ek.pvm                 AS kasittelyaika,
       ek.rahasumma           AS summa,
       ek.tyyppi::TEXT        AS laji,
       ek.indeksin_nimi       AS indeksi,
       TRUE                   AS suorasanktio,
       TRUE                   as bonus,
       ek.kasittelytapa       as kasittelytapa,
       ek.toimenpideinstanssi AS toimenpideinstanssi,
       CASE
           WHEN ek.tyyppi::TEXT IN ('lupausbonus', 'asiakastyytyvaisyysbonus')
               THEN (SELECT korotus
                       FROM sanktion_indeksikorotus(ek.pvm, ek.indeksin_nimi, ek.rahasumma, :urakka::INTEGER,
                                                    NULL::SANKTIOLAJI))
           ELSE 0
           END                AS indeksikorjaus,   -- TODO Varmista laskusäännöt
       ek.lisatieto           AS lisatieto,
       --  Muilla urakkatyypeillä kuin ylläpidon urakoilla ei voi olla bonukseen liitettyä ylläpitokohdetta
       NULL AS yllapitokohde_tr_numero,
       NULL AS yllapitokohde_tr_alkuosa,
       NULL AS yllapitokohde_tr_alkuetaisyys,
       NULL AS yllapitokohde_tr_loppuosa,
       NULL AS yllapitokohde_tr_loppuetaisyys,
       NULL AS yllapitokohde_numero,
       NULL AS yllapitokohde_nimi,
       NULL AS yllapitokohde_id
  FROM erilliskustannus ek
 WHERE ek.urakka = :urakka
   AND ek.toimenpideinstanssi = (SELECT tpi.id AS id
                                   FROM toimenpideinstanssi tpi
                                            JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                                            JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
                                        maksuera m
                                  WHERE tpi.urakka = :urakka
                                    AND m.toimenpideinstanssi = tpi.id
                                    AND tpk2.koodi = '23150'
                                  LIMIT 1)
   AND ek.pvm BETWEEN :alku AND :loppu
   AND ek.poistettu IS NOT TRUE

UNION

-- Hae ylläpidon urakoille poikkeuksellisesti bonus sanktio-taulusta
-- TODO refaktoroidaan myöhemmin ylläpidon bonusten käsittely sellaiseksi, että poikkeuksellista käsittelyä ei
--      tarvitsisi tehdä.
SELECT s.id,
       -- perintapvm sanktiolla vastaa erilliskustannuksen laskutuskuukautta
       s.perintapvm AS perintapvm,
       -- Kasittelyaika haetaan sanktion suhteen laatupoikkeaman puolelta, erilliskustannuksissa se on 'pvm'-sarake.
       lp.kasittelyaika AS kasittelyaika,
       -- Muunna ylläpidon bonuksen summa positiiviseksi (se on käytännössä negatiivinen sanktio nykytoteuksella)
       s.maara * -1 AS summa,
       'yllapidon_bonus' AS laji,
       s.indeksi AS indeksi,
       TRUE AS suorasanktio,
       TRUE AS bonus,
       lp.kasittelytapa AS kasittelytapa,
       s.toimenpideinstanssi AS toimenpideinstanssi,
       0 AS indeksikorjaus,
       lp.perustelu AS lisatieto,
       -- Ylläpitourakoilla voi olla bonukseen liitetty ylläpitokohde
       ypk.tr_numero                       AS yllapitokohde_tr_numero,
       ypk.tr_alkuosa                      AS yllapitokohde_tr_alkuosa,
       ypk.tr_alkuetaisyys                 AS yllapitokohde_tr_alkuetaisyys,
       ypk.tr_loppuosa                     AS yllapitokohde_tr_loppuosa,
       ypk.tr_loppuetaisyys                AS yllapitokohde_tr_loppuetaisyys,
       ypk.kohdenumero                     AS yllapitokohde_numero,
       ypk.nimi                            AS yllapitokohde_nimi,
       ypk.id                              AS yllapitokohde_id
  FROM sanktio s
           JOIN laatupoikkeama lp ON s.laatupoikkeama = lp.id
           LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
 WHERE lp.urakka = :urakka
   AND s.sakkoryhma = 'yllapidon_bonus'::SANKTIOLAJI
   AND s.perintapvm BETWEEN :alku AND :loppu
   AND s.poistettu IS NOT TRUE;

-- name: merkitse-maksuera-likaiseksi!
-- Merkitsee sanktiota vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
-- Merkitään vain jos toimenpideinstanssi on voimassa tai sen vanhenemisesta on 3 kk.
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE tyyppi = 'sakko' AND
      toimenpideinstanssi IN (
        SELECT toimenpideinstanssi
        FROM sanktio
        WHERE id = :sanktio) AND
       toimenpideinstanssi IN (select id from toimenpideinstanssi where loppupvm > current_timestamp - INTERVAL '3 months');

-- name: hae-sanktiotyypit
-- Hakee kaikki sanktiotyypit
SELECT
  id,
  koodi,
  nimi,
  toimenpidekoodi
FROM sanktiotyyppi;

--name: hae-sanktiotyyppi-koodilla
SELECT id
  FROM sanktiotyyppi
 WHERE koodi IN (:koodit);

--name: hae-sanktiotyypin-tiedot-koodilla
SELECT id, nimi, toimenpidekoodi, koodi
FROM sanktiotyyppi
WHERE koodi = :koodit
  AND poistettu = false;


--name: hae-sanktion-urakka-id
SELECT urakka FROM laatupoikkeama lp
JOIN sanktio s ON lp.id = s.laatupoikkeama
WHERE s.id = :sanktioid;

-- name: hae-sanktio
SELECT s.id AS id, s.perintapvm, s.indeksi, s.maara, s.laatupoikkeama as "laatupoikkeama-id", s.toimenpideinstanssi, s.tyyppi, s.suorasanktio,
       s.ulkoinen_id, s.vakiofraasi, s.sakkoryhma, s.muokattu, s.muokkaaja, s.luoja, s.luotu, s.poistettu
FROM sanktio s
WHERE s.poistettu IS NOT TRUE
  AND s.id = :id;
