-- name: hae-urakan-paallystyskohteet
-- Hakee urakan kaikki paallystyskohteet
SELECT
  paallystyskohde.id,
  pi.id as paallystysilmoitus_id,
  kohdenumero,
  paallystyskohde.nimi,
  sopimuksen_mukaiset_tyot,
  lisatyo,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
  muutoshinta
FROM paallystyskohde
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = paallystyskohde.id
  AND pi.poistettu IS NOT TRUE
WHERE
  urakka = :urakka
  AND sopimus = :sopimus
  AND paallystyskohde.poistettu IS NOT TRUE;

-- name: hae-urakan-paallystystoteumat
-- Hakee urakan kaikki paallystystoteumat
SELECT
  pk.id AS paallystyskohde_id,
  tila,
  pk.nimi,
  pk.kohdenumero,
  paatos_tekninen_osa,
  paatos_taloudellinen_osa,
  pk.sopimuksen_mukaiset_tyot,
  pk.arvonvahennykset,
  pk.bitumi_indeksi,
  pk.kaasuindeksi
FROM paallystysilmoitus
  RIGHT JOIN paallystyskohde pk ON pk.id = paallystysilmoitus.paallystyskohde
                                   AND pk.urakka = :urakka
                                   AND pk.sopimus = :sopimus
                                   AND pk.poistettu IS NOT TRUE
WHERE paallystysilmoitus.poistettu IS NOT TRUE;

-- name: hae-urakan-paallystysilmoitus-paallystyskohteella
-- Hakee urakan päällystysilmoituksen päällystyskohteen id:llä
SELECT
  paallystysilmoitus.id,
  tila,
  aloituspvm,
  valmispvm_kohde,
  valmispvm_paallystys,
  takuupvm,
  pk.nimi as kohdenimi,
  pk.kohdenumero,
  muutoshinta,
  ilmoitustiedot,
  paatos_tekninen_osa,
  paatos_taloudellinen_osa,
  perustelu_tekninen_osa,
  perustelu_taloudellinen_osa,
  kasittelyaika_tekninen_osa,
  kasittelyaika_taloudellinen_osa
FROM paallystysilmoitus
  JOIN paallystyskohde pk ON pk.id = paallystysilmoitus.paallystyskohde
                             AND pk.urakka = :urakka
                             AND pk.sopimus = :sopimus
                             AND pk.poistettu IS NOT TRUE
WHERE paallystyskohde = :paallystyskohde
      AND paallystysilmoitus.poistettu IS NOT TRUE;

-- name: hae-urakan-paallystyskohteen-paallystyskohdeosat
-- Hakee urakan päällystyskohdeosat päällystyskohteen id:llä.
SELECT
  paallystyskohdeosa.id,
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
                          AND paallystyskohde.poistettu IS NOT TRUE
WHERE paallystyskohde = :paallystyskohde
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
poistettu                           = FALSE
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

-- name: luo-paallystyskohde<!
-- Luo uuden päällystykohteen
INSERT INTO paallystyskohde (urakka, sopimus, kohdenumero, nimi, sopimuksen_mukaiset_tyot, lisatyo, arvonvahennykset, bitumi_indeksi, kaasuindeksi)
VALUES (:urakka,
        :sopimus,
        :kohdenumero,
        :nimi,
        :sopimuksen_mukaiset_tyot,
        :lisatyo,
        :arvonvahennykset,
        :bitumi_indeksi,
        :kaasuindeksi);

-- name: paivita-paallystyskohde!
-- Päivittää päällystyskohteen
UPDATE paallystyskohde
SET
  kohdenumero                 = :kohdenumero,
  nimi                        = :nimi,
  sopimuksen_mukaiset_tyot    = :sopimuksen_mukaiset_tyot,
  lisatyo                     = :lisatyo,
  arvonvahennykset            = :arvonvanhennykset,
  bitumi_indeksi              = :bitumi_indeksi,
  kaasuindeksi                = :kaasuindeksi
WHERE id = :id;

-- name: poista-paallystyskohde!
-- Poistaa päällystyskohteen
UPDATE paallystyskohde
SET poistettu = true
WHERE id = :id;

-- name: luo-paallystyskohdeosa<!
-- Luo uuden päällystykohdeosan
INSERT INTO paallystyskohdeosa (paallystyskohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, kvl, nykyinen_paallyste, toimenpide)
VALUES (:paallystyskohde,
        :nimi,
        :tr_numero,
        :tr_alkuosa,
        :tr_alkuetaisyys,
        :tr_loppuosa,
        :tr_loppuetaisyys,
        :kvl,
        :nykyinen_paallyste,
        :toimenpide);

-- name: paivita-paallystyskohdeosa!
-- Päivittää päällystyskohdeosan
UPDATE paallystyskohdeosa
SET
  nimi                  = :nimi,
  tr_numero             = :tr_numero,
  tr_alkuosa            = :tr_alkuosa,
  tr_alkuetaisyys       = :tr_alkuetaisyys,
  tr_loppuosa           = :tr_loppuosa,
  tr_loppuetaisyys      = :tr_loppuetaisyys,
  kvl                   = :kvl,
  nykyinen_paallyste    = :nykyinen_paallyste,
  toimenpide            = :toimenpide
WHERE id = :id;

-- name: poista-paallystyskohdeosa!
-- Poistaa päällystyskohdeosan
UPDATE paallystyskohdeosa
SET poistettu = true
WHERE id = :id;