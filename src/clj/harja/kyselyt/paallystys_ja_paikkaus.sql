-- name: hae-urakan-yllapitokohteet
-- Hakee urakan kaikki yllapitokohteet ja niihin liittyvät ilmoitukset
SELECT
  yllapitokohde.id,
  pi.id as paallystysilmoitus_id,
  pi.tila as paallystysilmoitus_tila,
  pai.id as paikkausilmoitus_id,
  pai.tila as paikkausilmoitus_tila,
  kohdenumero,
  paallystyskohde.nimi,
  sopimuksen_mukaiset_tyot,
  muu_tyo,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
  muutoshinta,
  pa.toteutunut_hinta
FROM yllapitokohde
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = yllapitokohde.id
  AND pi.poistettu IS NOT TRUE
  LEFT JOIN paikkausilmoitus pa ON pa.paikkauskohde = yllapitokohde.id
  AND pa.poistettu IS NOT TRUE
LEFT JOIN paikkausilmoitus pai ON pai.paikkauskohde = yllapitokohde.id
  AND pai.poistettu IS NOT TRUE
WHERE
  urakka = :urakka
  AND sopimus = :sopimus
  AND yllapitokohde.poistettu IS NOT TRUE;

-- name: hae-urakan-yllapitokohde
-- Hakee urakan yksittäisen ylläpitokohteen
SELECT id, kohdenumero, nimi, sopimuksen_mukaiset_tyot, muu_tyo, arvonvahennykset,
       bitumi_indeksi, kaasuindeksi
  FROM yllapitokohde
 WHERE urakka = :urakka AND id = :id;

-- name: hae-urakan-paallystystoteumat
-- Hakee urakan kaikki paallystystoteumat
SELECT
  yllapitokohde.id AS paallystyskohde_id,
  pi.tila,
  nimi,
  kohdenumero,
  pi.paatos_tekninen_osa,
  pi.paatos_taloudellinen_osa,
  sopimuksen_mukaiset_tyot,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi
FROM yllapitokohde
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = yllapitokohde.id
  AND pi.poistettu IS NOT TRUE
WHERE urakka = :urakka
AND sopimus = :sopimus
AND yllapitokohde.poistettu IS NOT TRUE;

-- name: hae-urakan-paallystysilmoitus-paallystyskohteella
-- Hakee urakan päällystysilmoituksen päällystyskohteen id:llä
SELECT
  paallystysilmoitus.id,
  tila,
  aloituspvm,
  valmispvm_kohde,
  valmispvm_paallystys,
  takuupvm,
  ypk.nimi as kohdenimi,
  ypk.kohdenumero,
  muutoshinta,
  ilmoitustiedot,
  paatos_tekninen_osa,
  paatos_taloudellinen_osa,
  perustelu_tekninen_osa,
  perustelu_taloudellinen_osa,
  kasittelyaika_tekninen_osa,
  kasittelyaika_taloudellinen_osa
FROM paallystysilmoitus
  JOIN yllapitokohde ypk ON ypk.id = paallystysilmoitus.paallystyskohde
                             AND ypk.urakka = :urakka
                             AND ypk.sopimus = :sopimus
                             AND ypk.poistettu IS NOT TRUE
WHERE paallystyskohde = :paallystyskohde
      AND paallystysilmoitus.poistettu IS NOT TRUE;

-- name: hae-urakan-yllapitokohteen-yllapitokohdeosat
-- Hakee urakan ylläpitokohdeosat ylläpitokohteen id:llä.
SELECT
  yllapitokohdeosa.id,
  yllapitokohdeosa.nimi,
  tr_numero,
  tr_alkuosa,
  tr_alkuetaisyys,
  tr_loppuosa,
  tr_loppuetaisyys,
  sijainti,
  kvl,
  nykyinen_paallyste,
  toimenpide
FROM yllapitokohdeosa
  JOIN yllapitokohde ON paallystyskohde.id = paallystyskohdeosa.paallystyskohde
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND paallystyskohde.poistettu IS NOT TRUE
WHERE yllapitokohde = :yllapitokohde
AND paallystyskohdeosa.poistettu IS NOT TRUE;

-- name: paivita-paallystysilmoitus!
-- Päivittää päällystysilmoituksen
UPDATE paallystysilmoitus
SET
  tila                              = :tila::paallystystila,
  ilmoitustiedot                    = :ilmoitustiedot :: JSONB,
  aloituspvm                        = :aloituspvm,
  valmispvm_kohde                   = :valmispvm_kohde,
  valmispvm_paallystys              = :valmispvm_paallystys,
  takuupvm                          = :takuupvm,
  muutoshinta                       = :muutoshinta,
  paatos_tekninen_osa               = :paatos_tekninen_osa::paallystysilmoituksen_paatostyyppi,
  paatos_taloudellinen_osa          = :paatos_taloudellinen_osa::paallystysilmoituksen_paatostyyppi,
  perustelu_tekninen_osa            = :perustelu_tekninen_osa,
  perustelu_taloudellinen_osa       = :perustelu_taloudellinen_osa,
  kasittelyaika_tekninen_osa        = :kasittelyaika_tekninen_osa,
  kasittelyaika_taloudellinen_osa   = :kasittelyaika_taloudellinen_osa,
  muokattu                          = NOW(),
  muokkaaja                         = :muokkaaja,
  poistettu                         = FALSE
WHERE paallystyskohde = :id;

-- name: luo-paallystysilmoitus<!
-- Luo uuden päällystysilmoituksen
INSERT INTO paallystysilmoitus (paallystyskohde, tila, ilmoitustiedot, aloituspvm, valmispvm_kohde, valmispvm_paallystys, takuupvm, muutoshinta, luotu, luoja, poistettu)
VALUES (:paallystyskohde,
        :tila::paallystystila,
        :ilmoitustiedot::JSONB,
        :aloituspvm,
        :valmispvm_kohde,
        :valmispvm_paallystys,
        :takuupvm,
        :muutoshinta,
        NOW(),
        :kayttaja, FALSE);

-- name: hae-paallystysilmoituksen-kommentit
-- Hakee annetun päällystysilmoituksen kaikki kommentit (joita ei ole poistettu) sekä
-- kommentin mahdollisen liitteen tiedot. Kommentteja on vaikea hakea
-- array aggregoimalla itse havainnon hakukyselyssä.
SELECT
  k.id,
  k.tekija,
  k.kommentti,
  k.luoja,
  k.luotu                              AS aika,
  CONCAT(ka.etunimi, ' ', ka.sukunimi) AS tekijanimi,
  l.id                                 AS liite_id,
  l.tyyppi                             AS liite_tyyppi,
  l.koko                               AS liite_koko,
  l.nimi                               AS liite_nimi,
  l.liite_oid                          AS liite_oid
FROM kommentti k
  JOIN kayttaja ka ON k.luoja = ka.id
  LEFT JOIN liite l ON l.id = k.liite
WHERE k.poistettu = FALSE
      AND k.id IN (SELECT pk.kommentti
                   FROM paallystysilmoitus_kommentti pk
                   WHERE pk.paallystysilmoitus = :id)
ORDER BY k.luotu ASC;

-- name: liita-kommentti<!
-- Liittää päällystysilmoitukseen uuden kommentin
INSERT INTO paallystysilmoitus_kommentti (paallystysilmoitus, kommentti) VALUES (:paallystysilmoitus, :kommentti);

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
UPDATE paallystyskohde
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

-- name: hae-urakan-paikkaustoteumat
-- Hakee urakan kaikki paikkaustoteumat
SELECT
  yllapitokohde.id AS paikkauskohde_id,
  pi.id,
  pi.tila,
  nimi,
  kohdenumero,
  pi.paatos
FROM yllapitokohde
  LEFT JOIN paikkausilmoitus pi ON pi.paikkauskohde = yllapitokohde.id
                                   AND pi.poistettu IS NOT TRUE
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND paallystyskohde.poistettu IS NOT TRUE;

-- name: hae-urakan-paikkausilmoitus-paikkauskohteella
-- Hakee urakan paikkausilmoituksen paikkauskohteen id:llä
SELECT
  paikkausilmoitus.id,
  tila,
  aloituspvm,
  valmispvm_kohde,
  valmispvm_paikkaus,
  ypk.nimi as kohdenimi,
  ypk.kohdenumero,
  ilmoitustiedot,
  paatos,
  perustelu,
  kasittelyaika
FROM paikkausilmoitus
  JOIN yllapitokohde ypk ON ypk.id = paikkausilmoitus.paikkauskohde
                           AND ypk.urakka = :urakka
                           AND ypk.sopimus = :sopimus
                           AND ypk.poistettu IS NOT TRUE
WHERE paikkauskohde = :paikkauskohde
      AND paikkausilmoitus.poistettu IS NOT TRUE;

-- name: paivita-paikkausilmoitus!
-- Päivittää paikkausilmoituksen
UPDATE paikkausilmoitus
SET
  tila                              = :tila::paikkausilmoituksen_tila,
  ilmoitustiedot                    = :ilmoitustiedot :: JSONB,
  toteutunut_hinta                  = :toteutunut_hinta,
  aloituspvm                        = :aloituspvm,
  valmispvm_kohde                   = :valmispvm_kohde,
  valmispvm_paikkaus                = :valmispvm_paikkaus,
  paatos                            = :paatos::paikkausilmoituksen_paatostyyppi,
  perustelu                         = :perustelu,
  kasittelyaika                     = :kasittelyaika,
  muokattu                          = NOW(),
  muokkaaja                         = :muokkaaja,
  poistettu                         = FALSE
WHERE paikkauskohde = :id;

-- name: luo-paikkausilmoitus<!
-- Luo uuden paikkausilmoituksen
INSERT INTO paikkausilmoitus (paikkauskohde, tila, ilmoitustiedot, toteutunut_hinta, aloituspvm, valmispvm_kohde, valmispvm_paikkaus, luotu, luoja, poistettu)
VALUES (:paikkauskohde,
        :tila::paikkausilmoituksen_tila,
        :ilmoitustiedot::JSONB,
        :toteutunut_hinta,
        :aloituspvm,
        :valmispvm_kohde,
        :valmispvm_paikkaus,
        NOW(),
        :kayttaja, FALSE);

-- name: hae-paikkausilmoituksen-kommentit
-- Hakee annetun paikkausilmoituksen kaikki kommentit (joita ei ole poistettu) sekä
-- kommentin mahdollisen liitteen tiedot. Kommentteja on vaikea hakea
-- array aggregoimalla itse havainnon hakukyselyssä.
SELECT
  k.id,
  k.tekija,
  k.kommentti,
  k.luoja,
  k.luotu                              AS aika,
  CONCAT(ka.etunimi, ' ', ka.sukunimi) AS tekijanimi,
  l.id                                 AS liite_id,
  l.tyyppi                             AS liite_tyyppi,
  l.koko                               AS liite_koko,
  l.nimi                               AS liite_nimi,
  l.liite_oid                          AS liite_oid
FROM kommentti k
  JOIN kayttaja ka ON k.luoja = ka.id
  LEFT JOIN liite l ON l.id = k.liite
WHERE k.poistettu = FALSE
      AND k.id IN (SELECT pk.kommentti
                   FROM paikkausilmoitus_kommentti pk
                   WHERE pk.ilmoitus = :id)
ORDER BY k.luotu ASC;

-- name: liita-kommentti<!
-- Liittää paikkausilmoitukseen uuden kommentin
INSERT INTO paikkausilmoitus_kommentti (ilmoitus, kommentti) VALUES (:paikkausilmoitus, :kommentti);