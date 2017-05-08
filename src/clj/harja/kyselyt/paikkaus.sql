-- name: hae-urakan-paikkausilmoitukset
-- Hakee urakan kaikki paikkausilmoitukset
SELECT
  ypk.id AS "paikkauskohde-id",
  pi.id,
  pi.tila,
  nimi,
  kohdenumero,
  pi.paatos,
  ypka.kohde_alku AS "kohde-alkupvm",
  ypka.paallystys_alku AS "paallystys-alkupvm",
  ypka.paallystys_loppu AS "paallystys-loppupvm",
  ypka.tiemerkinta_alku AS "tiemerkinta-alkupvm",
  ypka.tiemerkinta_loppu AS "tiemerkinta-loppupvm",
  ypka.kohde_valmis AS "kohde-valmispvm"
FROM yllapitokohde ypk
  LEFT JOIN paikkausilmoitus pi ON pi.paikkauskohde = ypk.id
                                   AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND yllapitokohdetyotyyppi = 'paikkaus'::yllapitokohdetyotyyppi
      AND (:vuosi::INTEGER IS NULL OR (cardinality(vuodet) = 0
           OR vuodet @> ARRAY[:vuosi]::int[]))
      AND ypk.poistettu IS NOT TRUE;

-- name: hae-urakan-paikkausilmoitus-paikkauskohteella
-- Hakee urakan paikkausilmoituksen paikkauskohteen id:llä
SELECT
  paikkausilmoitus.id,
  tila,
  aloituspvm,
  valmispvm_kohde AS "valmispvm-kohde",
  valmispvm_paikkaus AS "valmispvm-paikkaus",
  ypk.nimi as kohdenimi,
  ypk.kohdenumero,
  ypk.toteutunut_hinta AS "toteutunut-hinta",
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
INSERT INTO paikkausilmoitus (paikkauskohde, tila, ilmoitustiedot,
                              aloituspvm, valmispvm_kohde, valmispvm_paikkaus, luotu, luoja, poistettu)
VALUES (:paikkauskohde,
        :tila::paikkausilmoituksen_tila,
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
INSERT INTO paikkausilmoitus_kommentti (ilmoitus, kommentti) VALUES (:paikkausilmoitus, :kommentti);

-- name: hae-urakan-yllapitokohde
-- Hakee urakan yksittäisen ylläpitokohteen
SELECT
  id,
  kohdenumero,
  nimi,
  sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  arvonvahennykset,
  bitumi_indeksi           AS "bitumi-indeksi",
  kaasuindeksi
FROM yllapitokohde
WHERE urakka = :urakka AND id = :id;

-- name: yllapitokohteella-paikkausilmoitus
SELECT EXISTS(SELECT id
              FROM paikkausilmoitus
              WHERE paikkauskohde = :yllapitokohde);

-- name: paivita-paikkauskohteen-toteutunut-hinta!
-- Päivittää paikkauskohteen toteutuneen hinnan yllapitokohde tauluun
UPDATE yllapitokohde
   SET toteutunut_hinta = :toteutunut_hinta
 WHERE id = :id;