-- name: hae-urakan-yllapitokohteet
-- Hakee urakan kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
SELECT
  ypk.id,
  pi.id    AS "paallystysilmoitus-id",
  pi.tila  AS "paallystysilmoitus-tila",
  pi.muutoshinta,
  pai.id   AS "paikkausilmoitus-id",
  pai.tila AS "paikkausilmoitus-tila",
  pai.toteutunut_hinta AS "toteutunut-hinta",
  ypk.kohdenumero,
  ypk.nimi,
  ypk.sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi AS "bitumi-indeksi",
  ypk.kaasuindeksi,
  ypk.nykyinen_paallyste AS "nykyinen-paallyste",
  ypk.keskimaarainen_vuorokausiliikenne AS "keskimaarainen-vuorokausiliikenne",
  yllapitoluokka,
  ypk.tr_numero AS "tr-numero",
  ypk.tr_alkuosa AS "tr-alkuosa",
  ypk.tr_alkuetaisyys AS "tr-alkuetaisyys",
  ypk.tr_loppuosa AS "tr-loppuosa",
  ypk.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypk.tr_ajorata AS "tr-ajorata",
  ypk.tr_kaista AS "tr-kaista",
  ypk.yhaid
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN paikkausilmoitus pai ON pai.paikkauskohde = ypk.id
                                    AND pai.poistettu IS NOT TRUE
WHERE
  urakka = :urakka
  AND sopimus = :sopimus
  AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakan-yllapitokohde
-- Hakee urakan yksittäisen ylläpitokohteen
SELECT
  id,
  kohdenumero,
  nimi,
  sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  arvonvahennykset,
  bitumi_indeksi AS "bitumi-indeksi",
  kaasuindeksi
FROM yllapitokohde
WHERE urakka = :urakka AND id = :id;

-- name: hae-urakan-yllapitokohteen-yllapitokohdeosat
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  ypko.id,
  ypko.nimi,
  ypko.tr_numero AS "tr-numero",
  ypko.tr_alkuosa AS "tr-alkuosa",
  ypko.tr_alkuetaisyys AS "tr-alkuetaisyys",
  ypko.tr_loppuosa AS "tr-loppuosa",
  ypko.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypko.tr_ajorata AS "tr-ajorata",
  ypko.tr_kaista AS "tr-kaista",
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
                           yllapitoluokka, nykyinen_paallyste,
                           sopimuksen_mukaiset_tyot,
                           arvonvahennykset, bitumi_indeksi, kaasuindeksi, tyyppi)
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
        :tyyppi::yllapitokohdetyyppi);

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
  kaasuindeksi                      = :kaasuindeksi
WHERE id = :id;

-- name: poista-yllapitokohde!
-- Poistaa ylläpitokohteen
UPDATE yllapitokohde
SET poistettu = TRUE
WHERE id = :id;

-- name: luo-yllapitokohdeosa<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
                              tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, toimenpide, sijainti)
VALUES (:yllapitokohde,
        :nimi,
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
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  tr_ajorata       = :tr_ajorata,
  tr_kaista        = :tr_kaista,
  toimenpide       = :toimenpide,
  sijainti         = :sijainti
WHERE id = :id;

-- name: poista-yllapitokohdeosa!
-- Poistaa ylläpitokohdeosan
UPDATE yllapitokohdeosa
SET poistettu = TRUE
WHERE id = :id;

-- name: hae-paallystysurakan-aikataulu
-- Hakee päällystysurakan kohteiden aikataulutiedot
SELECT
  id,
  kohdenumero,
  nimi,
  urakka,
  sopimus,
  aikataulu_paallystys_alku AS "aikataulu-paallystys-alku",
  aikataulu_paallystys_loppu  AS "aikataulu-paallystys-loppu",
  aikataulu_tiemerkinta_alku  AS "aikataulu-tiemerkinta-alku",
  aikataulu_tiemerkinta_loppu  AS "aikataulu-tiemerkinta-loppu",
  aikataulu_kohde_valmis  AS "aikataulu-kohde-valmis",
  aikataulu_muokattu  AS "aikataulu-muokattu",
  aikataulu_muokkaaja  AS "aikataulu-muokkaaja",
  valmis_tiemerkintaan  AS "valmis-tiemerkintaan",
  tr_numero AS "tr-numero",
  tr_alkuosa AS "tr-alkuosa",
  tr_alkuetaisyys AS "tr-alkuetaisyys",
  tr_loppuosa AS "tr-loppuosa",
  tr_loppuetaisyys AS "tr-loppuetaisyys",
  tr_ajorata AS "tr-ajorata",
  tr_kaista AS "tr-kaista",
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
  aikataulu_paallystys_alku AS "aikataulu-paallystys-alku",
  aikataulu_paallystys_loppu  AS "aikataulu-paallystys-loppu",
  aikataulu_tiemerkinta_alku  AS "aikataulu-tiemerkinta-alku",
  aikataulu_tiemerkinta_loppu  AS "aikataulu-tiemerkinta-loppu",
  aikataulu_kohde_valmis  AS "aikataulu-kohde-valmis",
  aikataulu_muokattu  AS "aikataulu-muokattu",
  aikataulu_muokkaaja  AS "aikataulu-muokkaaja",
  valmis_tiemerkintaan  AS "valmis-tiemerkintaan",
  tr_numero AS "tr-numero",
  tr_alkuosa AS "tr-alkuosa",
  tr_alkuetaisyys AS "tr-alkuetaisyys",
  tr_loppuosa AS "tr-loppuosa",
  tr_loppuetaisyys AS "tr-loppuetaisyys",
  tr_ajorata AS "tr-ajorata",
  tr_kaista AS "tr-kaista"
FROM yllapitokohde
WHERE
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
  AND poistettu IS NOT TRUE;

-- name: hae-urakan-tyyppi
SELECT tyyppi FROM urakka WHERE id = :urakkaid;

-- name: hae-tiemerkinnan-suorittavat-urakat
-- FIXME Käytä urakka-id:tä sorttaamaan lähimpänä olevat ylimmäs
SELECT
  id,
  nimi,
  hallintayksikko
FROM urakka
WHERE (loppupvm IS NULL or loppupvm >= NOW())
AND tyyppi = 'tiemerkinta'::urakkatyyppi;

-- name: tallenna-yllapitokohteen-aikataulu!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohde
SET
  aikataulu_paallystys_alku    = :aikataulu_paallystys_alku,
  aikataulu_paallystys_loppu   = :aikataulu_paallystys_loppu,
  aikataulu_tiemerkinta_alku   = :aikataulu_tiemerkinta_alku,
  aikataulu_tiemerkinta_loppu  = :aikataulu_tiemerkinta_loppu,
  aikataulu_kohde_valmis       = :aikataulu_kohde_valmis,
  aikataulu_muokattu           = NOW(),
  aikataulu_muokkaaja          = :aikataulu_muokkaaja,
  suorittava_tiemerkintaurakka = :suorittava_tiemerkintaurakka
WHERE id = :id;

-- name: yllapitokohteella-paallystysilmoitus
SELECT EXISTS(SELECT id
              FROM paallystysilmoitus
              WHERE paallystyskohde = :yllapitokohde) AS sisaltaa_paallystysilmoituksen;
-- name: yllapitokohteella-paikkausilmoitus
SELECT EXISTS(SELECT id
              FROM paikkausilmoitus
              WHERE paikkauskohde = :yllapitokohde) AS sisaltaa_paikkausilmoituksen;