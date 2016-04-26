-- name: hae-urakan-yllapitokohteet
-- Hakee urakan kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
SELECT
  ypk.id,
  pi.id as paallystysilmoitus_id,
  pi.tila as paallystysilmoitus_tila,
  pai.id as paikkausilmoitus_id,
  pai.tila as paikkausilmoitus_tila,
  pai.toteutunut_hinta
  kohdenumero,
  ypk.nimi,
  sopimuksen_mukaiset_tyot,
  muu_tyo,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
  muutoshinta
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
SELECT id, kohdenumero, nimi, sopimuksen_mukaiset_tyot, muu_tyo, arvonvahennykset,
  bitumi_indeksi, kaasuindeksi
FROM yllapitokohde
WHERE urakka = :urakka AND id = :id;

-- name: hae-urakan-yllapitokohteen-yllapitokohdeosat
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  ypko.id,
  ypko.nimi,
  tr_numero,
  tr_alkuosa,
  tr_alkuetaisyys,
  tr_loppuosa,
  tr_loppuetaisyys,
  sijainti,
  kvl,
  nykyinen_paallyste,
  toimenpide
FROM yllapitokohdeosa ypko
  JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id
                            AND urakka = :urakka
                            AND sopimus = :sopimus
                            AND ypk.poistettu IS NOT TRUE
WHERE yllapitokohde = :yllapitokohde
      AND ypko.poistettu IS NOT TRUE;

-- name: luo-yllapitokohde<!
-- Luo uuden ylläpitokohteen
INSERT INTO yllapitokohde (urakka, sopimus, kohdenumero, nimi, sopimuksen_mukaiset_tyot, muu_tyo, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES (:urakka,
        :sopimus,
        :kohdenumero,
        :nimi,
        :sopimuksen_mukaiset_tyot,
        :muu_tyo,
        :arvonvahennykset,
        :bitumi_indeksi,
        :kaasuindeksi);

-- name: paivita-yllapitokohde!
-- Päivittää ylläpitokohteen
UPDATE yllapitokohde
SET
  kohdenumero                 = :kohdenumero,
  nimi                        = :nimi,
  sopimuksen_mukaiset_tyot    = :sopimuksen_mukaiset_tyot,
  muu_tyo                     = :muu_tyo,
  arvonvahennykset            = :arvonvanhennykset,
  bitumi_indeksi              = :bitumi_indeksi,
  kaasuindeksi                = :kaasuindeksi
WHERE id = :id;

-- name: poista-yllapitokohde!
-- Poistaa ylläpitokohteen
UPDATE yllapitokohde
SET poistettu = true
WHERE id = :id;

-- name: luo-yllapitokohdeosa<!
-- Luo uuden yllapitokohdeosan
INSERT INTO yllapitokohdeosa (yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, sijainti, kvl, nykyinen_paallyste, toimenpide)
VALUES (:yllapitokohde,
  :nimi,
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :sijainti,
  :kvl,
  :nykyinen_paallyste,
  :toimenpide);

-- name: paivita-yllapitokohdeosa!
-- Päivittää yllapitokohdeosan
UPDATE yllapitokohdeosa
SET
  nimi                  = :nimi,
  tr_numero             = :tr_numero,
  tr_alkuosa            = :tr_alkuosa,
  tr_alkuetaisyys       = :tr_alkuetaisyys,
  tr_loppuosa           = :tr_loppuosa,
  tr_loppuetaisyys      = :tr_loppuetaisyys,
  sijainti              = :sijainti,
  kvl                   = :kvl,
  nykyinen_paallyste    = :nykyinen_paallyste,
  toimenpide            = :toimenpide
WHERE id = :id;

-- name: poista-yllapitokohdeosa!
-- Poistaa ylläpitokohdeosan
UPDATE yllapitokohdeosa
SET poistettu = true
WHERE id = :id;

-- name: paivita-paallystys-tai-paikkausurakan-geometria
SELECT paivita_paallystys_tai_paikkausurakan_geometria(:urakka::INTEGER);

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
  aikataulu_paallystys_alku = :aikataulu_paallystys_alku,
  aikataulu_paallystys_loppu = :aikataulu_paallystys_loppu,
  aikataulu_tiemerkinta_alku = :aikataulu_tiemerkinta_alku,
  aikataulu_tiemerkinta_loppu = :aikataulu_tiemerkinta_loppu,
  aikataulu_kohde_valmis = :aikataulu_kohde_valmis,
  aikataulu_muokattu = NOW(),
  aikataulu_muokkaaja = :aikataulu_muokattu
WHERE id = :id;

-- name: yllapitokohteella-paallystysilmoitus
SELECT EXISTS(SELECT id FROM paallystysilmoitus WHERE paallystyskohde = :yllapitokohde) as sisaltaa_paallystysilmoituksen;
-- name: yllapitokohteella-paikkausilmoitus
SELECT EXISTS(SELECT id FROM paikkausilmoitus WHERE paikkauskohde = :yllapitokohde) as sisaltaa_paikkausilmoituksen;