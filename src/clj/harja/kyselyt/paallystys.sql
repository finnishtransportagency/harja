-- name: hae-urakan-paallystysilmoitukset
-- Hakee urakan kaikki päällystysilmoitukset
SELECT
  yllapitokohde.id AS "paallystyskohde-id",
  pi.tila,
  nimi,
  kohdenumero,
  pi.paatos_tekninen_osa AS "paatos-tekninen-osa",
  pi.paatos_taloudellinen_osa  AS "paatos-taloudellinen-osa",
  sopimuksen_mukaiset_tyot  AS "sopimuksen-mukaiset-tyot",
  arvonvahennykset,
  bitumi_indeksi AS "bitumi-indeksi",
  kaasuindeksi
FROM yllapitokohde
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = yllapitokohde.id
  AND pi.poistettu IS NOT TRUE
WHERE urakka = :urakka
AND sopimus = :sopimus
AND tyyppi = 'paallystys'::yllapitokohdetyyppi
AND yllapitokohde.poistettu IS NOT TRUE;

-- name: hae-urakan-paallystysilmoituksen-id-paallystyskohteella
SELECT id FROM paallystysilmoitus WHERE paallystyskohde = :paallystyskohde;

-- name: hae-urakan-paallystysilmoitus-paallystyskohteella
-- Hakee urakan päällystysilmoituksen päällystyskohteen id:llä
SELECT
  pi.id,
  pi.muutoshinta,
  tila,
  aloituspvm,
  valmispvm_kohde                 AS "valmispvm-kohde",
  valmispvm_paallystys            AS "valmispvm-paallystys",
  takuupvm,
  ypk.nimi                        AS kohdenimi,
  ypk.kohdenumero,
  ypk.sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  ypk.arvonvahennykset,
  ypk.bitumi_indeksi AS "bitumi-indeksi",
  ypk.kaasuindeksi,
  ilmoitustiedot,
  paatos_tekninen_osa             AS "paatos-tekninen-osa",
  paatos_taloudellinen_osa        AS "paatos-taloudellinen-osa",
  perustelu_tekninen_osa          AS "perustelu-tekninen-osa",
  perustelu_taloudellinen_osa     AS "perustelu-taloudellinen-osa",
  kasittelyaika_tekninen_osa      AS "kasittelyaika-tekninen-osa",
  kasittelyaika_taloudellinen_osa AS "kasittelyaika-taloudellinen-osa",
  ypko.id                         AS kohdeosa_id,
  ypko.nimi                       AS kohdeosa_nimi,
  ypko.tr_numero                  AS kohdeosa_tie,
  ypko.tr_alkuosa                 AS kohdeosa_aosa,
  ypko.tr_alkuetaisyys            AS kohdeosa_aet,
  ypko.tr_loppuosa                AS kohdeosa_losa,
  ypko.tr_loppuetaisyys           AS kohdeosa_let,
  ypko.tr_ajorata                 AS "kohdeosa_ajorata",
  ypko.tr_kaista                  AS "kohdeosa_kaista"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = :paallystyskohde
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohdeosa ypko ON ypko.yllapitokohde = :paallystyskohde
                                     AND ypko.poistettu IS NOT TRUE
WHERE ypk.id = :paallystyskohde
      AND ypk.poistettu IS NOT TRUE;

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