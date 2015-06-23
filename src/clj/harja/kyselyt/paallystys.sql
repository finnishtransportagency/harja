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
  tila,
  pk.nimi,
  pk.kohdenumero
FROM paallystysilmoitus
JOIN paallystyskohde pk ON pk.id = paallystysilmoitus.paallystyskohde
AND pk.urakka = :urakka
AND pk.sopimus = :sopimus;

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