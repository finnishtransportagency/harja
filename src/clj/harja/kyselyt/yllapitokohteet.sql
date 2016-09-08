-- name: hae-urakan-yllapitokohteet-alikohteineen
-- Hakee urakan kaikki yllapitokohteet sekä niiden alikohteet
SELECT
  ypk.id,
  ypk.urakka,
  ypk.sopimus,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.yllapitokohdetyyppi,
  ypk.yllapitokohdetyotyyppi,
  ypk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypk.kaasuindeksi,
  ypk.nykyinen_paallyste                AS "nykyinen-paallyste",
  ypk.keskimaarainen_vuorokausiliikenne AS "keskimaarainen-vuorokausiliikenne",
  ypk.yllapitoluokka,
  ypk.tr_numero                         AS "tr-numero",
  ypk.tr_alkuosa                        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  ypk.tr_ajorata                        AS "tr-ajorata",
  ypk.tr_kaista                         AS "tr-kaista",
  ypk.yhaid,
  ypko.id                               AS "kohdeosa_id",
  ypko.yllapitokohde                    AS "kohdeosa_yllapitokohde",
  ypko.nimi                             AS "kohdeosa_nimi",
  ypko.tunnus                           AS "kohdeosa_tunnus",
  ypko.tr_numero                        AS "kohdeosa_tr-numero",
  ypko.tr_alkuosa                       AS "kohdeosa_tr-alkuosa",
  ypko.tr_alkuetaisyys                  AS "kohdeosa_tr-alkuetaisyys",
  ypko.tr_loppuosa                      AS "kohdeosa_tr-loppuosa",
  ypko.tr_loppuetaisyys                 AS "kohdeosa_tr-loppuetaisyys",
  ypko.tr_ajorata                       AS "kohdeosa_tr-ajorata",
  ypko.tr_kaista                        AS "kohdeosa_tr-kaista",
  ypko.poistettu                        AS "kohdeosa_poistettu",
  ypko.sijainti                         AS "kohdeosa_sijainti",
  ypko.yhaid                            AS "kohdeosa_yhaid",
  ypko.toimenpide                       AS "kohdeosa_toimenpide"
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohdeosa ypko ON ypk.id = ypko.yllapitokohde AND ypko.poistettu IS NOT TRUE
WHERE
  ypk.urakka = :urakka
  AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakan-sopimuksen-yllapitokohteet
-- Hakee urakan sopimuksen kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
SELECT
  ypk.id,
  pi.id                                 AS "paallystysilmoitus-id",
  pi.tila                               AS "paallystysilmoitus-tila",
  pi.muutoshinta,
  pai.id                                AS "paikkausilmoitus-id",
  pai.tila                              AS "paikkausilmoitus-tila",
  pai.toteutunut_hinta                  AS "toteutunut-hinta",
  ypk.kohdenumero,
  ypk.nimi,
  ypk.sopimuksen_mukaiset_tyot          AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi                    AS "bitumi-indeksi",
  ypk.kaasuindeksi,
  ypk.nykyinen_paallyste                AS "nykyinen-paallyste",
  ypk.keskimaarainen_vuorokausiliikenne AS "keskimaarainen-vuorokausiliikenne",
  yllapitoluokka,
  indeksin_kuvaus                       AS "indeksin-kuvaus",
  ypk.tr_numero                         AS "tr-numero",
  ypk.tr_alkuosa                        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  ypk.tr_ajorata                        AS "tr-ajorata",
  ypk.tr_kaista                         AS "tr-kaista",
  ypk.yhaid,
  ypk.yllapitokohdetyyppi
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN paikkausilmoitus pai ON pai.paikkauskohde = ypk.id
                                    AND pai.poistettu IS NOT TRUE
WHERE
  urakka = :urakka
  AND sopimus = :sopimus
  AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakan-yllapitokohteet-lomakkeelle
-- Hakee urakan kaikki yllapitokohteet, listaten vain minimaalisen määrän tietoa
SELECT
  ypk.id,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.tr_numero        AS "tr-numero",
  ypk.tr_alkuosa       AS "tr-alkuosa",
  ypk.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypk.tr_loppuosa      AS "tr-loppuosa",
  ypk.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypk.tr_ajorata       AS "tr-ajorata",
  ypk.tr_kaista        AS "tr-kaista"
FROM yllapitokohde ypk
WHERE
  ((urakka = :urakka AND sopimus = :sopimus)
   OR suorittava_tiemerkintaurakka = :urakka)
  AND ypk.poistettu IS NOT TRUE
ORDER BY tr_numero, tr_alkuosa, tr_alkuetaisyys;

-- name: hae-urakan-yllapitokohteen-yllapitokohdeosat
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  ypko.id,
  ypko.nimi,
  ypko.tunnus,
  ypko.tr_numero        AS "tr-numero",
  ypko.tr_alkuosa       AS "tr-alkuosa",
  ypko.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypko.tr_loppuosa      AS "tr-loppuosa",
  ypko.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypko.tr_ajorata       AS "tr-ajorata",
  ypko.tr_kaista        AS "tr-kaista",
  toimenpide,
  sijainti
FROM yllapitokohdeosa ypko
  JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id
                            AND urakka = :urakka
                            AND sopimus = :sopimus
                            AND ypk.poistettu IS NOT TRUE
WHERE yllapitokohde = :yllapitokohde
      AND ypko.poistettu IS NOT TRUE;

-- name: luo-yllapitokohde<!
-- Luo uuden ylläpitokohteen
INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi,
                           tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
                           tr_ajorata, tr_kaista, keskimaarainen_vuorokausiliikenne,
                           yllapitoluokka, nykyinen_paallyste, sopimuksen_mukaiset_tyot,
                           arvonvahennykset, bitumi_indeksi, kaasuindeksi, yllapitokohdetyyppi,
                           yllapitokohdetyotyyppi, indeksin_kuvaus)
VALUES (:urakka,
  :sopimus,
  :kohdenumero,
  :nimi,
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_ajorata,
  :tr_kaista,
  :keskimaarainen_vuorokausiliikenne,
  :yllapitoluokka,
  :nykyinen_paallyste,
  :sopimuksen_mukaiset_tyot,
  :arvonvahennykset,
  :bitumi_indeksi,
  :kaasuindeksi,
  :yllapitokohdetyyppi :: yllapitokohdetyyppi,
  :yllapitokohdetyotyyppi :: yllapitokohdetyotyyppi,
  :indeksin_kuvaus);

-- name: paivita-yllapitokohde!
-- Päivittää ylläpitokohteen
UPDATE yllapitokohde
SET
  kohdenumero                       = :kohdenumero,
  nimi                              = :nimi,
  tr_numero                         = :tr_numero,
  tr_alkuosa                        = :tr_alkuosa,
  tr_alkuetaisyys                   = :tr_alkuetaisyys,
  tr_loppuosa                       = :tr_loppuosa,
  tr_loppuetaisyys                  = :tr_loppuetaisyys,
  tr_ajorata                        = :tr_ajorata,
  tr_kaista                         = :tr_kaista,
  keskimaarainen_vuorokausiliikenne = :keskimaarainen_vuorokausiliikenne,
  yllapitoluokka                    = :yllapitoluokka,
  nykyinen_paallyste                = :nykyinen_paallyste,
  sopimuksen_mukaiset_tyot          = :sopimuksen_mukaiset_tyot,
  arvonvahennykset                  = :arvonvanhennykset,
  bitumi_indeksi                    = :bitumi_indeksi,
  kaasuindeksi                      = :kaasuindeksi,
  indeksin_kuvaus                   = :indeksin_kuvaus
WHERE id = :id
      AND urakka = :urakka;

-- name: poista-yllapitokohde!
-- Poistaa ylläpitokohteen
UPDATE yllapitokohde
SET poistettu = TRUE
WHERE id = :id
      AND urakka = :urakka;

-- name: luo-yllapitokohdeosa<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tunnus, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                              tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, toimenpide, sijainti)
VALUES (:yllapitokohde,
  :nimi,
  :tunnus,
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_ajorata,
  :tr_kaista,
  :toimenpide,
        :sijainti);

-- name: paivita-yllapitokohdeosa<!
-- Päivittää yllapitokohdeosan
UPDATE yllapitokohdeosa
SET
  nimi             = :nimi,
  tunnus           = :tunnus,
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  tr_ajorata       = :tr_ajorata,
  tr_kaista        = :tr_kaista,
  toimenpide       = :toimenpide,
  sijainti         = :sijainti
WHERE id = :id
      AND yllapitokohde IN (SELECT id
                            FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: poista-yllapitokohdeosa!
-- Poistaa ylläpitokohdeosan
UPDATE yllapitokohdeosa
SET poistettu = TRUE
WHERE id = :id
      AND yllapitokohde IN (SELECT id
                            FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: hae-paallystysurakan-aikataulu
-- Hakee päällystysurakan kohteiden aikataulutiedot
SELECT
  id,
  kohdenumero,
  nimi,
  urakka,
  sopimus,
  aikataulu_paallystys_alku    AS "aikataulu-paallystys-alku",
  aikataulu_paallystys_loppu   AS "aikataulu-paallystys-loppu",
  aikataulu_tiemerkinta_alku   AS "aikataulu-tiemerkinta-alku",
  aikataulu_tiemerkinta_loppu  AS "aikataulu-tiemerkinta-loppu",
  aikataulu_kohde_valmis       AS "aikataulu-kohde-valmis",
  aikataulu_muokattu           AS "aikataulu-muokattu",
  aikataulu_muokkaaja          AS "aikataulu-muokkaaja",
  valmis_tiemerkintaan         AS "valmis-tiemerkintaan",
  tr_numero                    AS "tr-numero",
  tr_alkuosa                   AS "tr-alkuosa",
  tr_alkuetaisyys              AS "tr-alkuetaisyys",
  tr_loppuosa                  AS "tr-loppuosa",
  tr_loppuetaisyys             AS "tr-loppuetaisyys",
  tr_ajorata                   AS "tr-ajorata",
  tr_kaista                    AS "tr-kaista",
  yllapitoluokka,
  suorittava_tiemerkintaurakka AS "suorittava-tiemerkintaurakka"
FROM yllapitokohde
WHERE
  urakka = :urakka
  AND sopimus = :sopimus
  AND poistettu IS NOT TRUE;

-- name: hae-tiemerkintaurakan-aikataulu
-- Hakee tiemerkintäurakan kohteiden aikataulutiedot
SELECT
  id,
  kohdenumero,
  nimi,
  urakka,
  sopimus,
  aikataulu_paallystys_alku   AS "aikataulu-paallystys-alku",
  aikataulu_paallystys_loppu  AS "aikataulu-paallystys-loppu",
  aikataulu_tiemerkinta_alku  AS "aikataulu-tiemerkinta-alku",
  aikataulu_tiemerkinta_loppu AS "aikataulu-tiemerkinta-loppu",
  aikataulu_kohde_valmis      AS "aikataulu-kohde-valmis",
  aikataulu_muokattu          AS "aikataulu-muokattu",
  aikataulu_muokkaaja         AS "aikataulu-muokkaaja",
  valmis_tiemerkintaan        AS "valmis-tiemerkintaan",
  tr_numero                   AS "tr-numero",
  tr_alkuosa                  AS "tr-alkuosa",
  tr_alkuetaisyys             AS "tr-alkuetaisyys",
  tr_loppuosa                 AS "tr-loppuosa",
  tr_loppuetaisyys            AS "tr-loppuetaisyys",
  tr_ajorata                  AS "tr-ajorata",
  tr_kaista                   AS "tr-kaista",
  yllapitoluokka
FROM yllapitokohde
WHERE
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
  AND poistettu IS NOT TRUE;

-- name: hae-urakan-tyyppi
SELECT tyyppi
FROM urakka
WHERE id = :urakka;

-- name: hae-tiemerkinnan-suorittavat-urakat
SELECT
  id,
  nimi,
  hallintayksikko
FROM urakka
WHERE (loppupvm IS NULL OR loppupvm >= NOW())
      AND tyyppi = 'tiemerkinta' :: urakkatyyppi;

-- name: tallenna-paallystyskohteen-aikataulu!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohde
SET
  aikataulu_paallystys_alku    = :aikataulu_paallystys_alku,
  aikataulu_paallystys_loppu   = :aikataulu_paallystys_loppu,
  aikataulu_kohde_valmis       = :aikataulu_kohde_valmis,
  aikataulu_muokattu           = NOW(),
  aikataulu_muokkaaja          = :aikataulu_muokkaaja,
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
WHERE id = :id
      AND urakka = :urakka;

-- name: merkitse-kohde-valmiiksi-tiemerkintaan<!
UPDATE yllapitokohde
SET
  valmis_tiemerkintaan = :valmis_tiemerkintaan
WHERE id = :id
      AND urakka = :urakka;

-- name: tallenna-tiemerkintakohteen-aikataulu!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohde
SET
  aikataulu_tiemerkinta_alku  = :aikataulu_tiemerkinta_alku,
  aikataulu_tiemerkinta_loppu = :aikataulu_tiemerkinta_loppu,
  aikataulu_muokattu          = NOW(),
  aikataulu_muokkaaja         = :aikataulu_muokkaaja
WHERE id = :id
      AND suorittava_tiemerkintaurakka = :urakka;

-- name: yllapitokohteella-paallystysilmoitus
SELECT EXISTS(SELECT id
              FROM paallystysilmoitus
              WHERE paallystyskohde = :yllapitokohde) AS sisaltaa_paallystysilmoituksen;
-- name: yllapitokohteella-paikkausilmoitus
SELECT EXISTS(SELECT id
              FROM paikkausilmoitus
              WHERE paikkauskohde = :yllapitokohde) AS sisaltaa_paikkausilmoituksen;

-- name: hae-yllapitokohteen-urakka-id
SELECT urakka AS id
FROM yllapitokohde
WHERE id = :id;

-- name: hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
SELECT suorittava_tiemerkintaurakka AS id
FROM yllapitokohde
WHERE id = :id;

-- name: hae-yllapitokohde
SELECT
  id,
  sopimus,
  kohdenumero,
  nimi,
  sopimuksen_mukaiset_tyot,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
  poistettu,
  aikataulu_paallystys_alku,
  aikataulu_paallystys_loppu,
  aikataulu_tiemerkinta_alku,
  aikataulu_tiemerkinta_loppu,
  aikataulu_kohde_valmis,
  aikataulu_muokattu,
  aikataulu_muokkaaja,
  valmis_tiemerkintaan,
  tr_numero                  AS "tr-numero",
  tr_alkuosa                 AS "tr-alkuosa",
  tr_alkuetaisyys            AS "tr-alkuetaisyys",
  tr_loppuosa                AS "tr-loppuosa",
  tr_loppuetaisyys           AS "tr-loppuetaisyys",
  tr_ajorata                 AS "tr-ajorata",
  tr_kaista                  AS "tr-kaista",
  yllapitokohdetyotyyppi,
  yllapitokohdetyyppi,
  yhatunnus,
  yhaid,
  yllapitoluokka,
  lahetysaika,
  keskimaarainen_vuorokausiliikenne,
  nykyinen_paallyste,
  suorittava_tiemerkintaurakka,
  (SELECT viimeisin_paivitys
   FROM geometriapaivitys
   WHERE nimi = 'tieverkko') AS karttapvm
FROM yllapitokohde
WHERE id = :id;

-- name: hae-yllapitokohteen-kohdeosat
SELECT
  id,
  yllapitokohde,
  nimi,
  tunnus,
  tr_numero                  AS "tr-numero",
  tr_alkuosa                 AS "tr-alkuosa",
  tr_alkuetaisyys            AS "tr-alkuetaisyys",
  tr_loppuosa                AS "tr-loppuosa",
  tr_loppuetaisyys           AS "tr-loppuetaisyys",
  tr_ajorata                 AS "tr-ajorata",
  tr_kaista                  AS "tr-kaista",
  poistettu,
  sijainti,
  yhaid,
  toimenpide,
  (SELECT viimeisin_paivitys
   FROM geometriapaivitys
   WHERE nimi = 'tieverkko') AS karttapvm
FROM yllapitokohdeosa
WHERE yllapitokohde = :yllapitokohde AND
      poistettu IS NOT TRUE;

-- name: merkitse-kohteen-lahetystiedot!
UPDATE yllapitokohde
SET lahetetty = :lahetetty, lahetys_onnistunut = :onnistunut, lahetysvirhe = :lahetysvirhe
WHERE id = :kohdeid;

-- name: onko-olemassa-urakalla?
-- single?: true
SELECT exists(SELECT id
              FROM yllapitokohde
              WHERE urakka = :urakka AND id = :kohde);

-- name: paivita-yllapitokohteen-sijainti!
-- Päivittää ylläpitokohteen sijainnin
UPDATE yllapitokohde
SET
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys
WHERE id = :id;

-- name: paivita-yllapitokohteen-paallystysaikataulu!
-- Päivittää ylläpitokohteen aikataulutiedot
UPDATE yllapitokohde
SET
  aikataulu_paallystys_alku = :paallystys_alku,
  aikataulu_paallystys_loppu = :paallystys_loppu,
  aikataulu_kohde_valmis = :kohde_valmis,
  valmis_tiemerkintaan = :valmis_tiemerkintaan,
  aikataulu_muokattu = NOW(),
  aikataulu_muokkaaja = :muokkaaja
WHERE id = :id;

-- name: paivita-yllapitokohteen-tiemerkintaaikataulu!
-- Päivittää ylläpitokohteen aikataulutiedot
UPDATE yllapitokohde
SET
  aikataulu_tiemerkinta_alku = :tiemerkinta_alku,
  aikataulu_tiemerkinta_loppu = :tiemerkinta_loppu,
  aikataulu_muokattu = NOW(),
  aikataulu_muokkaaja = :muokkaaja
WHERE id = :id;

-- name: poista-yllapitokohteen-kohdeosat!
DELETE FROM yllapitokohdeosa
WHERE yllapitokohde = :id;