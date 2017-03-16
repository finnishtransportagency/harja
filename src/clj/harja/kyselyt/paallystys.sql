-- name: hae-urakan-paallystysilmoitukset
-- Hakee urakan kaikki päällystysilmoitukset
SELECT
  ypk.id                   AS "paallystyskohde-id",
  ypk.tr_numero            AS "tr-numero",
  pi.tila,
  nimi,
  kohdenumero,
  pi.paatos_tekninen_osa   AS "paatos-tekninen-osa",
  sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  arvonvahennykset,
  bitumi_indeksi           AS "bitumi-indeksi",
  kaasuindeksi,
  lahetetty,
  lahetys_onnistunut       AS "lahetys-onnistunut",
  lahetysvirhe
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND yllapitokohdetyotyyppi = 'paallystys' :: yllapitokohdetyotyyppi
      AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
                                         OR vuodet @> ARRAY [:vuosi] :: INT []))
      AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakan-paallystysilmoituksen-id-paallystyskohteella
SELECT id
FROM paallystysilmoitus
WHERE paallystyskohde = :paallystyskohde;

-- name: hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
-- Hakee urakan päällystysilmoituksen päällystyskohteen id:llä
SELECT
  pi.id,
  tila,
  ypk.aikataulu_kohde_alku,
  ypk.aikataulu_kohde_valmis     AS "valmispvm-kohde",
  ypk.aikataulu_paallystys_loppu AS "valmispvm-paallystys",
  takuupvm,
  ypk.id                         AS "yllapitokohde-id",
  ypk.nimi                       AS kohdenimi,
  ypk.kohdenumero,
  ypk.sopimuksen_mukaiset_tyot   AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi             AS "bitumi-indeksi",
  ypk.kaasuindeksi,
  ypk.yllapitokohdetyyppi,
  ilmoitustiedot,
  paatos_tekninen_osa            AS "tekninen-osa_paatos",
  perustelu_tekninen_osa         AS "tekninen-osa_perustelu",
  kasittelyaika_tekninen_osa     AS "tekninen-osa_kasittelyaika",
  asiatarkastus_pvm              AS "asiatarkastus_tarkastusaika",
  asiatarkastus_tarkastaja       AS "asiatarkastus_tarkastaja",
  asiatarkastus_hyvaksytty       AS "asiatarkastus_hyvaksytty",
  asiatarkastus_lisatiedot       AS "asiatarkastus_lisatiedot",
  ypko.id                        AS kohdeosa_id,
  ypko.nimi                      AS kohdeosa_nimi,
  ypko.tunnus                    AS kohdeosa_tunnus,
  ypko.tr_numero                 AS "kohdeosa_tr-numero",
  ypko.tr_alkuosa                AS "kohdeosa_tr-alkuosa",
  ypko.tr_alkuetaisyys           AS "kohdeosa_tr-alkuetaisyys",
  ypko.tr_loppuosa               AS "kohdeosa_tr-loppuosa",
  ypko.tr_loppuetaisyys          AS "kohdeosa_tr-loppuetaisyys",
  ypko.tr_ajorata                AS "kohdeosa_tr-ajorata",
  ypko.tr_kaista                 AS "kohdeosa_tr-kaista",
  ypko.toimenpide                AS "kohdeosa_toimenpide",
  ypk.tr_numero                  AS "tr-numero",
  ypk.tr_alkuosa                 AS "tr-alkuosa",
  ypk.tr_alkuetaisyys            AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                AS "tr-loppuosa",
  ypk.tr_loppuetaisyys           AS "tr-loppuetaisyys",
  u.id                           AS "urakka-id"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = :paallystyskohde
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohdeosa ypko ON ypko.yllapitokohde = :paallystyskohde
                                     AND ypko.poistettu IS NOT TRUE
  LEFT JOIN urakka u ON u.id = ypk.urakka
WHERE ypk.id = :paallystyskohde
      AND ypk.poistettu IS NOT TRUE;

-- name: hae-paallystysilmoitus-paallystyskohteella
SELECT
  id,
  tila,
  ilmoitustiedot,
  paatos_tekninen_osa        AS "tekninen-osa_paatos",
  perustelu_tekninen_osa     AS "tekninen-osa_perustelu",
  kasittelyaika_tekninen_osa AS "tekninen-osa_kasittelyaika",
  asiatarkastus_pvm          AS "asiatarkastus_tarkastusaika",
  asiatarkastus_tarkastaja   AS "asiatarkastus_tarkastaja",
  asiatarkastus_hyvaksytty   AS "asiatarkastus_hyvaksytty",
  asiatarkastus_lisatiedot   AS "asiatarkastus_lisatiedot"
FROM paallystysilmoitus pi
WHERE paallystyskohde = :paallystyskohde;

-- name: paivita-paallystysilmoitus<!
-- Päivittää päällystysilmoituksen tiedot (ei käsittelyä tai asiatarkastusta, päivitetään erikseen)
UPDATE paallystysilmoitus
SET
  tila           = :tila :: paallystystila,
  ilmoitustiedot = :ilmoitustiedot :: JSONB,
  takuupvm       = :takuupvm,
  muokattu       = NOW(),
  muokkaaja      = :muokkaaja,
  poistettu      = FALSE
WHERE paallystyskohde = :id
      AND paallystyskohde IN (SELECT id
                              FROM yllapitokohde
                              WHERE urakka = :urakka);

-- name: paivita-paallystysilmoituksen-kasittelytiedot<!
-- Päivittää päällystysilmoituksen käsittelytiedot
UPDATE paallystysilmoitus
SET
  paatos_tekninen_osa        = :paatos_tekninen_osa :: paallystysilmoituksen_paatostyyppi,
  perustelu_tekninen_osa     = :perustelu_tekninen_osa,
  kasittelyaika_tekninen_osa = :kasittelyaika_tekninen_osa,
  muokattu                   = NOW(),
  muokkaaja                  = :muokkaaja
WHERE paallystyskohde = :id
      AND paallystyskohde IN (SELECT id
                              FROM yllapitokohde
                              WHERE urakka = :urakka);

-- name: paivita-paallystysilmoituksen-asiatarkastus<!
-- Päivittää päällystysilmoituksen asiatarkastuksen
UPDATE paallystysilmoitus
SET
  asiatarkastus_pvm          = :asiatarkastus_pvm,
  asiatarkastus_tarkastaja   = :asiatarkastus_tarkastaja,
  asiatarkastus_hyvaksytty   = :asiatarkastus_hyvaksytty,
  asiatarkastus_lisatiedot   = :asiatarkastus_lisatiedot,
  muokattu                   = NOW(),
  muokkaaja                  = :muokkaaja
WHERE paallystyskohde = :id
      AND paallystyskohde IN (SELECT id
                              FROM yllapitokohde
                              WHERE urakka = :urakka);

-- name: luo-paallystysilmoitus<!
-- Luo uuden päällystysilmoituksen
INSERT INTO paallystysilmoitus (paallystyskohde, tila, ilmoitustiedot, takuupvm, luotu, luoja, poistettu)
VALUES (:paallystyskohde,
        :tila :: paallystystila,
        :ilmoitustiedot :: JSONB,
        :takuupvm,
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

-- name: hae-maaramuutoksen-urakka
SELECT u.id AS urakka
FROM yllapitokohteen_maaramuutos ym
  JOIN yllapitokohde ypk ON ym.yllapitokohde = ypk.id
  JOIN urakka u ON ypk.urakka = u.id
WHERE ym.id = :id;

-- name: maaramuutos-jarjestelman-luoma
SELECT k.jarjestelma AS "jarjestelman-luoma"
FROM yllapitokohteen_maaramuutos ym
  JOIN kayttaja k ON ym.luoja = k.id
WHERE ym.id = :id;

-- name: hae-yllapitokohteen-maaramuutokset
SELECT
  ym.id,
  yllapitokohde,
  tyon_tyyppi               AS "tyyppi",
  tyo,
  yksikko,
  tilattu_maara             AS "tilattu-maara",
  ennustettu_maara          AS "ennustettu-maara",
  toteutunut_maara          AS "toteutunut-maara",
  yksikkohinta,
  k.jarjestelma             AS "jarjestelman-lisaama"
FROM yllapitokohteen_maaramuutos ym
  LEFT JOIN kayttaja k ON ym.luoja = k.id
WHERE yllapitokohde = :id
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = :id) = :urakka
      AND ym.poistettu IS NOT TRUE;

-- name: luo-yllapitokohteen-maaramuutos<!
INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo, yksikko, tilattu_maara,
                                         ennustettu_maara, toteutunut_maara,
                                         yksikkohinta, luoja, ulkoinen_id, jarjestelma)
VALUES (:yllapitokohde, :tyon_tyyppi :: maaramuutos_tyon_tyyppi, :tyo, :yksikko, :tilattu_maara,
        :ennustettu_maara, :toteutunut_maara,
        :yksikkohinta, :luoja, :ulkoinen_id, :jarjestelma);

-- name: paivita-yllapitokohteen-maaramuutos<!
UPDATE yllapitokohteen_maaramuutos
SET
  tyon_tyyppi      = :tyon_tyyppi :: maaramuutos_tyon_tyyppi,
  tyo              = :tyo,
  yksikko          = :yksikko,
  tilattu_maara    = :tilattu_maara,
  ennustettu_maara = :ennustettu_maara,
  toteutunut_maara = :toteutunut_maara,
  yksikkohinta     = :yksikkohinta,
  muokattu         = NOW(),
  muokkaaja        = :kayttaja,
  poistettu        = :poistettu
WHERE id = :id
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = :id) = :urakka;

-- name: yllapitokohteella-paallystysilmoitus
SELECT EXISTS(SELECT id
              FROM paallystysilmoitus
              WHERE paallystyskohde = :yllapitokohde);

-- name: poista-yllapitokohteen-jarjestelman-kirjaamat-maaramuutokset!
DELETE FROM yllapitokohteen_maaramuutos
WHERE yllapitokohde = :yllapitokohdeid AND
      jarjestelma = :jarjestelma

-- name: avaa-paallystysilmoituksen-lukko!
UPDATE paallystysilmoitus
SET tila = 'valmis'::paallystystila
WHERE paallystyskohde = :yllapitokohde_id

-- name: lukitse-paallystysilmoitus!
UPDATE paallystysilmoitus
SET tila = 'lukittu'::paallystystila
WHERE paallystyskohde = :yllapitokohde_id