-- name: hae-kohteen-pot2-tiedot
select ypk.tr_numero                 AS "tr-numero",
       ypk.tr_alkuosa                AS "tr-alkuosa",
       ypk.tr_alkuetaisyys           AS "tr-alkuetaisyys",
       ypk.tr_loppuosa               AS "tr-loppuosa",
       ypk.tr_loppuetaisyys          AS "tr-loppuetaisyys",
       ypk.id                        AS "yllapitokohde-id",
       ypk.nimi                      AS kohdenimi,
       ypk.tunnus,
       ypk.kohdenumero,
       ypka.kohde_alku               AS "aloituspvm",
       ypka.kohde_valmis             AS "valmispvm-kohde",
       ypka.paallystys_loppu         AS "valmispvm-paallystys",
       pot2.takuupvm,
       pot2.tila
from yllapitokohde ypk
         left join pot2 pot2 ON (pot2.yllapitokohde = ypk.id AND pot2.poistettu IS FALSE)
         left join yllapitokohteen_aikataulu ypka ON pot2.yllapitokohde = ypka.yllapitokohde
 WHERE ypk.id = :kohde_id;