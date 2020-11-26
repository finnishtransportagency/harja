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
 WHERE ypk.id = :paallystyskohde;

-- name: hae-urakan-pot2-paallystysilmoitukset
-- Hakee urakan kaikki päällystysilmoitukset vuodelta 2021 ja siitä eteenpäin (POT2)
SELECT
    ypk.id                        AS "paallystyskohde-id",
    pot2.id,
    ypk.tr_numero                 AS "tr-numero",
    pot2.tila,
    nimi,
    kohdenumero,
    yhaid,
    tunnus,
    pot2.paatos_tekninen_osa        AS "paatos-tekninen-osa",
    ypkk.sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
    ypkk.arvonvahennykset,
    ypkk.bitumi_indeksi           AS "bitumi-indeksi",
    ypkk.kaasuindeksi,
    lahetetty,
    lahetys_onnistunut            AS "lahetys-onnistunut",
    takuupvm,
    pot2.muokattu
  FROM yllapitokohde ypk
           LEFT JOIN pot2 pot2 ON pot2.yllapitokohde = ypk.id
      AND pot2.poistettu IS NOT TRUE
           LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id

 WHERE urakka = :urakka
   AND sopimus = :sopimus
   AND yllapitokohdetyotyyppi = 'paallystys' :: YLLAPITOKOHDETYOTYYPPI
   AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
     OR vuodet @> ARRAY [:vuosi] :: INT []))
   AND ypk.poistettu IS NOT TRUE;

-- name: hae-pot2-paallystyskohteella
SELECT
    id,
    tila,
    paatos_tekninen_osa        AS "tekninen-osa_paatos",
    perustelu_tekninen_osa     AS "tekninen-osa_perustelu",
    kasittelyaika_tekninen_osa AS "tekninen-osa_kasittelyaika",
    asiatarkastus_pvm          AS "asiatarkastus_tarkastusaika",
    asiatarkastus_tarkastaja   AS "asiatarkastus_tarkastaja",
    asiatarkastus_hyvaksytty   AS "asiatarkastus_hyvaksytty",
    asiatarkastus_lisatiedot   AS "asiatarkastus_lisatiedot"
  FROM pot2 pot2
 WHERE yllapitokohde = :paallystyskohde;



-- name: luo-paallystysilmoitus<!
-- Luo uuden päällystysilmoituksen
INSERT INTO pot2 (yllapitokohde, tila, takuupvm, luotu, luoja, poistettu)
VALUES (:paallystyskohde,
        :tila :: PAALLYSTYSTILA,
        :takuupvm,
        NOW(),
        :kayttaja, FALSE);


-- name: paivita-paallystysilmoitus<!
-- Päivittää päällystysilmoituksen tiedot (ei käsittelyä tai asiatarkastusta, päivitetään erikseen)
UPDATE pot2
   SET
       tila           = :tila :: PAALLYSTYSTILA,
       takuupvm       = :takuupvm,
       muokattu       = NOW(),
       muokkaaja      = :muokkaaja,
       poistettu      = FALSE
 WHERE yllapitokohde = :id
   AND yllapitokohde IN (SELECT id
                             FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: paivita-paallystysilmoituksen-kasittelytiedot<!
-- Päivittää päällystysilmoituksen käsittelytiedot
UPDATE pot2
   SET
       paatos_tekninen_osa        = :tekninen-osa_paatos :: PAALLYSTYSILMOITUKSEN_PAATOSTYYPPI,
       perustelu_tekninen_osa     = :tekninen-osa_perustelu,
       kasittelyaika_tekninen_osa = :tekninen-osa_kasittelyaika,
       muokattu                   = NOW(),
       muokkaaja                  = :muokkaaja
 WHERE yllapitokohde = :id
   AND yllapitokohde IN (SELECT id
                             FROM yllapitokohde
                            WHERE urakka = :urakka);

-- name: paivita-paallystysilmoituksen-asiatarkastus<!
-- Päivittää päällystysilmoituksen asiatarkastuksen
UPDATE pot2
   SET
       asiatarkastus_pvm        = :asiatarkastus_pvm,
       asiatarkastus_tarkastaja = :asiatarkastus_tarkastaja,
       asiatarkastus_hyvaksytty = :asiatarkastus_hyvaksytty,
       asiatarkastus_lisatiedot = :asiatarkastus_lisatiedot,
       muokattu                 = NOW(),
       muokkaaja                = :muokkaaja
 WHERE yllapitokohde = :id
   AND yllapitokohde IN (SELECT id
                             FROM yllapitokohde
                            WHERE urakka = :urakka);

