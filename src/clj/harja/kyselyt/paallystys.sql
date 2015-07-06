-- name: hae-urakan-paallystyskohteet
-- Hakee urakan kaikki paallystyskohteet
SELECT
  id,
  kohdenumero,
  paallystyskohde.nimi,
  sopimuksen_mukaiset_tyot,
  lisatyot,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
  muutoshinta
FROM paallystyskohde
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = paallystyskohde.id
WHERE
  urakka = :urakka
  AND sopimus = :sopimus;

-- name: hae-urakan-paallystystoteumat
-- Hakee urakan kaikki paallystystoteumat
SELECT
  pk.id AS paallystyskohde_id,
  tila,
  pk.nimi,
  pk.kohdenumero,
  paatos,
  pk.sopimuksen_mukaiset_tyot
FROM paallystysilmoitus
  RIGHT JOIN paallystyskohde pk ON pk.id = paallystysilmoitus.paallystyskohde
                                   AND pk.urakka = :urakka
                                   AND pk.sopimus = :sopimus
WHERE poistettu IS NOT TRUE;

-- name: hae-urakan-paallystysilmoitus-paallystyskohteella
-- Hakee urakan päällystysilmoituksen päällystyskohteen id:llä
SELECT
  paallystysilmoitus.id,
  tila,
  aloituspvm,
  valmistumispvm,
  takuupvm,
  pk.nimi as kohdenimi,
  pk.kohdenumero,
  muutoshinta,
  ilmoitustiedot,
  paatos,
  perustelu,
  kasittelyaika
FROM paallystysilmoitus
  JOIN paallystyskohde pk ON pk.id = paallystysilmoitus.paallystyskohde
                             AND pk.urakka = :urakka
                             AND pk.sopimus = :sopimus
WHERE paallystyskohde = :paallystyskohde
      AND poistettu IS NOT TRUE;

-- name: hae-urakan-paallystyskohteen-paallystyskohdeosat
-- Hakee urakan päällystyskohdeosat päällystyskohteen id:llä.
SELECT
  paallystyskohdeosa.nimi,
  tr_numero,
  tr_alkuosa,
  tr_alkuetaisyys,
  tr_loppuosa,
  tr_loppuetaisyys,
  kvl,
  nykyinen_paallyste,
  toimenpide
FROM paallystyskohdeosa
  JOIN paallystyskohde ON paallystyskohde.id = paallystyskohdeosa.paallystyskohde
                          AND urakka = :urakka
                          AND sopimus = :sopimus
WHERE paallystyskohde = :paallystyskohde;

-- name: paivita-paallystysilmoitus!
-- Päivittää päällystysilmoituksen
UPDATE paallystysilmoitus
SET
  tila           = :tila::paallystystila,
  ilmoitustiedot = :ilmoitustiedot :: JSONB,
  aloituspvm     = :aloituspvm,
  valmistumispvm = :valmistumispvm,
  takuupvm       = :takuupvm,
  muutoshinta    = :muutoshinta,
  paatos         = :paatos::paallystysilmoituksen_paatostyyppi,
  perustelu      = :perustelu,
  kasittelyaika  = :kasittelyaika,
  muokattu       = NOW(),
  muokkaaja      = :muokkaaja,
  poistettu      = FALSE
WHERE paallystyskohde = :id;

-- name: luo-paallystysilmoitus<!
-- Luo uuden päällystysilmoituksen
INSERT INTO paallystysilmoitus (paallystyskohde, tila, ilmoitustiedot, aloituspvm, valmistumispvm, takuupvm, muutoshinta, luotu, luoja, poistettu)
VALUES (:paallystyskohde,
        :tila::paallystystila,
        :ilmoitustiedot::JSONB,
        :aloituspvm,
        :valmistumispvm,
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