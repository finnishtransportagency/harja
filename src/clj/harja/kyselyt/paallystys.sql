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