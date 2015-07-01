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
  pk.sopimuksen_mukaiset_tyot
FROM paallystysilmoitus
  RIGHT JOIN paallystyskohde pk ON pk.id = paallystysilmoitus.paallystyskohde
                                   AND pk.urakka = :urakka
                                   AND pk.sopimus = :sopimus
WHERE poistettu IS NOT TRUE;

-- name: hae-urakan-paallystysilmoitus-paallystyskohteella
-- Hakee urakan päällystysilmoituksen päällystyskohteen id:llä
SELECT
  tila,
  aloituspvm,
  valmistumispvm,
  takuupvm,
  pk.nimi,
  pk.kohdenumero,
  muutoshinta,
  ilmoitustiedot
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
  muokattu       = NOW(),
  muokkaaja      = :muokkaaja,
  poistettu      = FALSE
  WHERE paallystyskohde = :id;

-- name: luo-paallystysilmoitus<!
-- Luo uuden päällystysilmoituksen
INSERT INTO paallystysilmoitus (paallystyskohde, tila, ilmoitustiedot, aloituspvm, valmistumispvm, takuupvm, muutoshinta, luotu, luoja, poistettu)
VALUES (:paallystyskohde, :tila :: paallystystila, :ilmoitustiedot :: JSONB, :aloituspvm, :valmistumispvm, :takuupvm, :muutoshinta,
        NOW(), :kayttaja, FALSE);
