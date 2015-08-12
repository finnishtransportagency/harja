-- name: hae-urakan-paikkaustoteumat
-- Hakee urakan kaikki paikkaustoteumat
SELECT
  paallystyskohde.id AS paikkauskohde_id,
  pi.id,
  pi.tila,
  nimi,
  kohdenumero,
  pi.paatos
FROM paallystyskohde
  LEFT JOIN paikkausilmoitus pi ON pi.paikkauskohde = paallystyskohde.id
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
  pk.nimi as kohdenimi,
  pk.kohdenumero,
  ilmoitustiedot,
  paatos,
  perustelu,
  kasittelyaika
FROM paikkausilmoitus
  JOIN paallystyskohde pk ON pk.id = paikkausilmoitus.paikkauskohde
                             AND pk.urakka = :urakka
                             AND pk.sopimus = :sopimus
                             AND pk.poistettu IS NOT TRUE
WHERE paikkauskohde = :paikkauskohde
      AND paikkausilmoitus.poistettu IS NOT TRUE;

-- name: paivita-paikkausilmoitus!
-- Päivittää paikkausilmoituksen
UPDATE paikkausilmoitus
SET
  tila                              = :tila::paikkausilmoituksen_tila,
  ilmoitustiedot                    = :ilmoitustiedot :: JSONB,
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
INSERT INTO paikkausilmoitus (paikkauskohde, tila, ilmoitustiedot, aloituspvm, valmispvm_kohde, valmispvm_paikkaus, luotu, luoja, poistettu)
VALUES (:paikkauskohde,
        :tila::paikkaustila,
        :ilmoitustiedot::JSONB,
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
INSERT INTO paikkausilmoitus_kommentti (paikkausilmoitus, kommentti) VALUES (:paikkausilmoitus, :kommentti);