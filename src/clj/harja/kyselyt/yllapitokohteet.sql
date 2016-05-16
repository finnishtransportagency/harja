-- name: hae-urakan-yllapitokohteet
-- Hakee urakan kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
SELECT
  ypk.id,
  pi.id    AS paallystysilmoitus_id,
  pi.tila  AS paallystysilmoitus_tila,
  pi.muutoshinta,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.sopimuksen_mukaiset_tyot,
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi,
  ypk.kaasuindeksi,
  ypk.nykyinen_paallyste,
  ypk.keskimaarainen_vuorokausiliikenne,
  yllapitoluokka,
  ypk.tr_numero,
  ypk.tr_alkuosa,
  ypk.tr_alkuetaisyys,
  ypk.tr_loppuosa,
  ypk.tr_loppuetaisyys,
  ypk.yhaid,
  pai.id   AS paikkausilmoitus_id,
  pai.tila AS paikkausilmoitus_tila,
  pai.toteutunut_hinta
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
  sopimuksen_mukaiset_tyot,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi
FROM yllapitokohde
WHERE urakka = :urakka AND id = :id;

-- name: hae-urakan-yllapitokohteen-yllapitokohdeosat
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  ypko.id,
  ypko.nimi,
  ypko.tr_numero,
  ypko.tr_alkuosa,
  ypko.tr_alkuetaisyys,
  ypko.tr_loppuosa,
  ypko.tr_loppuetaisyys,
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
INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, sopimuksen_mukaiset_tyot,
                           arvonvahennykset, bitumi_indeksi, kaasuindeksi,
                           keskimaarainen_vuorokausiliikenne, nykyinen_paallyste)
VALUES (:urakka,
        :sopimus,
        :kohdenumero,
        :nimi,
        :sopimuksen_mukaiset_tyot,
        :arvonvahennykset,
        :bitumi_indeksi,
        :kaasuindeksi,
        :keskimaarainen_vuorokausiliikenne,
        :nykyinen_paallyste);

-- name: paivita-yllapitokohde!
-- Päivittää ylläpitokohteen
UPDATE yllapitokohde
SET
  kohdenumero                       = :kohdenumero,
  nimi                              = :nimi,
  sopimuksen_mukaiset_tyot          = :sopimuksen_mukaiset_tyot,
  arvonvahennykset                  = :arvonvanhennykset,
  bitumi_indeksi                    = :bitumi_indeksi,
  kaasuindeksi                      = :kaasuindeksi,
  keskimaarainen_vuorokausiliikenne = :keskimaarainen_vuorokausiliikenne,
  nykyinen_paallyste                = :nykyinen_paallyste
WHERE id = :id;

-- name: poista-yllapitokohde!
-- Poistaa ylläpitokohteen
UPDATE yllapitokohde
SET poistettu = TRUE
WHERE id = :id;

-- name: luo-yllapitokohdeosa<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti)
VALUES (:yllapitokohde,
        :nimi,
        :tr_numero,
        :tr_alkuosa,
        :tr_alkuetaisyys,
        :tr_loppuosa,
        :tr_loppuetaisyys,
        :sijainti;

-- name: paivita-yllapitokohdeosa!
-- Päivittää yllapitokohdeosan
UPDATE yllapitokohdeosa
SET
  nimi             = :nimi,
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  sijainti         = :sijainti
WHERE id = :id;

-- name: poista-yllapitokohdeosa!
-- Poistaa ylläpitokohdeosan
UPDATE yllapitokohdeosa
SET poistettu = TRUE
WHERE id = :id;

-- name: hae-urakan-aikataulu
-- Hakee urakan kohteiden aikataulutiedot
SELECT
  id,
  kohdenumero,
  nimi,
  urakka,
  sopimus,
  aikataulu_paallystys_alku,
  aikataulu_paallystys_loppu,
  aikataulu_tiemerkinta_alku,
  aikataulu_tiemerkinta_loppu,
  aikataulu_kohde_valmis,
  aikataulu_muokattu,
  aikataulu_muokkaaja,
  valmis_tiemerkintaan
FROM yllapitokohde
WHERE
  urakka = :urakka
  AND sopimus = :sopimus
  AND yllapitokohde.poistettu IS NOT TRUE;

-- name: tallenna-yllapitokohteen-aikataulu!
-- Tallentaa ylläpitokohteen aikataulun
UPDATE yllapitokohde
SET
  aikataulu_paallystys_alku   = :aikataulu_paallystys_alku,
  aikataulu_paallystys_loppu  = :aikataulu_paallystys_loppu,
  aikataulu_tiemerkinta_alku  = :aikataulu_tiemerkinta_alku,
  aikataulu_tiemerkinta_loppu = :aikataulu_tiemerkinta_loppu,
  aikataulu_kohde_valmis      = :aikataulu_kohde_valmis,
  aikataulu_muokattu          = NOW(),
  aikataulu_muokkaaja         = :aikataulu_muokattu
WHERE id = :id;

-- name: yllapitokohteella-paallystysilmoitus
SELECT EXISTS(SELECT id
              FROM paallystysilmoitus
              WHERE paallystyskohde = :yllapitokohde) AS sisaltaa_paallystysilmoituksen;
-- name: yllapitokohteella-paikkausilmoitus
SELECT EXISTS(SELECT id
              FROM paikkausilmoitus
              WHERE paikkauskohde = :yllapitokohde) AS sisaltaa_paikkausilmoituksen;