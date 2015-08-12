-- name: hae-urakan-paikkaustoteumat
-- Hakee urakan kaikki paikkaustoteumat
SELECT
  paallystyskohde.id AS paallystyskohde_id,
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
  paikkauskohde.id,
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