-- name: hae-urakan-paallystysilmoitukset
-- Hakee urakan kaikki päällystysilmoitukset
SELECT
  ypk.id                        AS "paallystyskohde-id",
  pi.id,
  ypk.tr_numero                 AS "tr-numero",
  pi.tila,
  pi.versio                     AS "versio",
  nimi,
  kohdenumero,
  yhaid,
  tunnus,
  pi.paatos_tekninen_osa        AS "paatos-tekninen-osa",
  ypkk.sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi           AS "bitumi-indeksi",
  ypkk.kaasuindeksi,
  lahetetty,
  lahetys_onnistunut            AS "lahetys-onnistunut",
  takuupvm,
  pi.muokattu
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id

WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND yllapitokohdetyotyyppi = 'paallystys' :: YLLAPITOKOHDETYOTYYPPI
      AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
                                         OR vuodet @> ARRAY [:vuosi] :: INT []))
      AND ypk.poistettu IS NOT TRUE;

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

-- name: hae-urakan-paallystysilmoituksen-id-paallystyskohteella
SELECT id
FROM paallystysilmoitus
WHERE paallystyskohde = :paallystyskohde;

-- name: hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
-- Hakee urakan päällystysilmoituksen päällystyskohteen id:llä
SELECT
  pi.id,
  pi.versio                     AS "versio",
  pi.lisatiedot,
  tila,
  ypka.kohde_alku               AS "aloituspvm",
  ypka.kohde_valmis             AS "valmispvm-kohde",
  ypka.paallystys_loppu         AS "valmispvm-paallystys",
  takuupvm,
  ypk.id                        AS "yllapitokohde-id",
  ypk.nimi                      AS kohdenimi,
  ypk.tunnus,
  ypk.kohdenumero,
  ypk.vuodet,
  ypkk.sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi           AS "bitumi-indeksi",
  ypkk.kaasuindeksi,
  sum(-s.maara)                 AS "sakot-ja-bonukset", -- käännetään toisin päin jotta summaus toimii oikein
  ypk.yllapitokohdetyyppi,
  ilmoitustiedot,
  paatos_tekninen_osa           AS "tekninen-osa_paatos",
  perustelu_tekninen_osa        AS "tekninen-osa_perustelu",
  kasittelyaika_tekninen_osa    AS "tekninen-osa_kasittelyaika",
  asiatarkastus_pvm             AS "asiatarkastus_tarkastusaika",
  asiatarkastus_tarkastaja      AS "asiatarkastus_tarkastaja",
  asiatarkastus_hyvaksytty      AS "asiatarkastus_hyvaksytty",
  asiatarkastus_lisatiedot      AS "asiatarkastus_lisatiedot",
  ypko.id                       AS kohdeosa_id,
  ypko.nimi                     AS kohdeosa_nimi,
  ypko.tr_numero                AS "kohdeosa_tr-numero",
  ypko.tr_alkuosa               AS "kohdeosa_tr-alkuosa",
  ypko.tr_alkuetaisyys          AS "kohdeosa_tr-alkuetaisyys",
  ypko.tr_loppuosa              AS "kohdeosa_tr-loppuosa",
  ypko.tr_loppuetaisyys         AS "kohdeosa_tr-loppuetaisyys",
  ypko.tr_ajorata               AS "kohdeosa_tr-ajorata",
  ypko.tr_kaista                AS "kohdeosa_tr-kaista",
  ypko.paallystetyyppi          AS "kohdeosa_paallystetyyppi",
  ypko.raekoko                  AS "kohdeosa_raekoko",
  ypko.tyomenetelma             AS "kohdeosa_tyomenetelma",
  ypko.massamaara               AS "kohdeosa_massamaara",
  ypko.toimenpide               AS "kohdeosa_toimenpide",
  ypk.tr_numero                 AS "tr-numero",
  ypk.tr_alkuosa                AS "tr-alkuosa",
  ypk.tr_alkuetaisyys           AS "tr-alkuetaisyys",
  ypk.tr_loppuosa               AS "tr-loppuosa",
  ypk.tr_loppuetaisyys          AS "tr-loppuetaisyys",
  ypk.tr_ajorata                AS "tr-ajorata",
  ypk.tr_kaista                 AS "tr-kaista",
  u.id                          AS "urakka-id"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = :paallystyskohde
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohdeosa ypko ON ypko.yllapitokohde = :paallystyskohde
                                     AND ypko.poistettu IS NOT TRUE
  LEFT JOIN urakka u ON u.id = ypk.urakka
  LEFT JOIN laatupoikkeama lp ON (lp.yllapitokohde = ypk.id AND lp.urakka = ypk.urakka AND lp.poistettu IS NOT TRUE)
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id
WHERE ypk.id = :paallystyskohde
      AND ypk.poistettu IS NOT TRUE
GROUP BY pi.id, ypk.id, ypko.id, ypka.kohde_alku, ypka.kohde_valmis, ypka.paallystys_loppu,
  ypkk.sopimuksen_mukaiset_tyot, ypkk.arvonvahennykset, ypkk.bitumi_indeksi, ypkk.kaasuindeksi,
  u.id;

-- name: hae-kohdeosan-pot2-paallystekerrokset
SELECT
  pot2p.id as "pot2p_id",
  pot2p.kohdeosa_id as "kohdeosa-id",
  pot2p.toimenpide,
  pot2p.materiaali,
  pot2p.leveys,
  pot2p.massamenekki,
  pot2p.pinta_ala,
  pot2p.kokonaismassamaara,
  pot2p.piennar,
  pot2p.lisatieto
FROM pot2_paallystekerros pot2p
WHERE pot2_id = :pot2_id AND kohdeosa_id = :kohdeosa_id;

-- name: hae-pot2-alustarivit
SELECT
    pot2a.id as "pot2a_id",
    pot2a.tr_numero AS "tr-numero",
    pot2a.tr_alkuosa AS "tr-alkuosa",
    pot2a.tr_alkuetaisyys AS "tr-alkuetaisyys",
    pot2a.tr_loppuosa AS "tr-loppuosa",
    pot2a.tr_loppuetaisyys AS "tr-loppuetaisyys",
    pot2a.tr_ajorata AS "tr-ajorata",
    pot2a.tr_kaista AS "tr-kaista",
    pot2a.toimenpide,
    pot2a.murske,
    pot2a.massa,
    pot2a.verkon_tyyppi AS "verkon-tyyppi",
    pot2a.verkon_tarkoitus AS "verkon-tarkoitus",
    pot2a.verkon_sijainti AS "verkon-sijainti"
  FROM pot2_alusta pot2a
 WHERE pot2_id = :pot2_id AND poistettu IS FALSE;

-- name: hae-paallystysilmoitus-paallystyskohteella
SELECT
  id,
  versio                     AS "versio",
  tila,
  ilmoitustiedot,
  paatos_tekninen_osa        AS "tekninen-osa_paatos",
  perustelu_tekninen_osa     AS "tekninen-osa_perustelu",
  kasittelyaika_tekninen_osa AS "tekninen-osa_kasittelyaika",
  asiatarkastus_pvm          AS "asiatarkastus_tarkastusaika",
  asiatarkastus_tarkastaja   AS "asiatarkastus_tarkastaja",
  asiatarkastus_hyvaksytty   AS "asiatarkastus_hyvaksytty",
  asiatarkastus_lisatiedot   AS "asiatarkastus_lisatiedot"
FROM paallystysilmoitus pi
WHERE paallystyskohde = :paallystyskohde
  AND NOT poistettu;

-- name: paivita-paallystysilmoitus<!
-- Päivittää päällystysilmoituksen tiedot (ei käsittelyä tai asiatarkastusta, päivitetään erikseen)
UPDATE paallystysilmoitus
SET
  tila           = :tila :: PAALLYSTYSTILA,
  ilmoitustiedot = :ilmoitustiedot :: JSONB,
  takuupvm       = :takuupvm,
  muokattu       = NOW(),
  muokkaaja      = :muokkaaja,
  poistettu      = FALSE,
  lisatiedot     = :lisatiedot
WHERE paallystyskohde = :id
      AND paallystyskohde IN (SELECT id
                              FROM yllapitokohde
                              WHERE urakka = :urakka);

-- name: paivita-paallystysilmoituksen-kasittelytiedot<!
-- Päivittää päällystysilmoituksen käsittelytiedot
UPDATE paallystysilmoitus
SET
  paatos_tekninen_osa        = :tekninen-osa_paatos :: PAALLYSTYSILMOITUKSEN_PAATOSTYYPPI,
  perustelu_tekninen_osa     = :tekninen-osa_perustelu,
  kasittelyaika_tekninen_osa = :tekninen-osa_kasittelyaika,
  muokattu                   = NOW(),
  muokkaaja                  = :muokkaaja
WHERE paallystyskohde = :id
      AND paallystyskohde IN (SELECT id
                              FROM yllapitokohde
                              WHERE urakka = :urakka);

-- name: paivita-paallystysilmoituksen-asiatarkastus<!
-- Päivittää päällystysilmoituksen asiatarkastuksen
UPDATE paallystysilmoitus
SET
  asiatarkastus_pvm        = :asiatarkastus_pvm,
  asiatarkastus_tarkastaja = :asiatarkastus_tarkastaja,
  asiatarkastus_hyvaksytty = :asiatarkastus_hyvaksytty,
  asiatarkastus_lisatiedot = :asiatarkastus_lisatiedot,
  muokattu                 = NOW(),
  muokkaaja                = :muokkaaja
WHERE paallystyskohde = :id
      AND paallystyskohde IN (SELECT id
                              FROM yllapitokohde
                              WHERE urakka = :urakka);

-- name: luo-paallystysilmoitus<!
-- Luo uuden päällystysilmoituksen
INSERT INTO paallystysilmoitus (paallystyskohde, tila, ilmoitustiedot, takuupvm, luotu, luoja, poistettu, versio, lisatiedot)
VALUES (:paallystyskohde,
        :tila :: PAALLYSTYSTILA,
        :ilmoitustiedot :: JSONB,
        :takuupvm,
        NOW(),
        :kayttaja, FALSE, :versio, :lisatiedot);

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

-- name: hae-maaramuutoksen-urakka
SELECT u.id AS urakka
FROM yllapitokohteen_maaramuutos ym
  JOIN yllapitokohde ypk ON ym.yllapitokohde = ypk.id
  JOIN urakka u ON ypk.urakka = u.id
WHERE ym.id = :id;

-- name: maaramuutos-jarjestelman-luoma
SELECT k.jarjestelma AS "jarjestelman-luoma"
FROM yllapitokohteen_maaramuutos ym
  JOIN kayttaja k ON ym.luoja = k.id
WHERE ym.id = :id;

-- name: hae-yllapitokohteen-maaramuutokset
SELECT
  ym.id,
  yllapitokohde    AS "yllapitokohde-id",
  tyon_tyyppi      AS "tyyppi",
  tyo,
  yksikko,
  tilattu_maara    AS "tilattu-maara",
  ennustettu_maara AS "ennustettu-maara",
  toteutunut_maara AS "toteutunut-maara",
  yksikkohinta,
  k.jarjestelma    AS "jarjestelman-lisaama"
FROM yllapitokohteen_maaramuutos ym
  LEFT JOIN kayttaja k ON ym.luoja = k.id
WHERE yllapitokohde = :id
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = :id) = :urakka
      AND ym.poistettu IS NOT TRUE;

-- name: hae-yllapitokohteiden-maaramuutokset
SELECT
  ym.id,
  yllapitokohde    AS "yllapitokohde-id",
  tyon_tyyppi      AS "tyyppi",
  tyo,
  yksikko,
  tilattu_maara    AS "tilattu-maara",
  ennustettu_maara AS "ennustettu-maara",
  toteutunut_maara AS "toteutunut-maara",
  yksikkohinta,
  k.jarjestelma    AS "jarjestelman-lisaama"
FROM yllapitokohteen_maaramuutos ym
  LEFT JOIN kayttaja k ON ym.luoja = k.id
WHERE yllapitokohde IN (:idt)
  AND ym.poistettu IS NOT TRUE;

-- name: luo-yllapitokohteen-maaramuutos<!
INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo, yksikko, tilattu_maara,
                                         ennustettu_maara, toteutunut_maara,
                                         yksikkohinta, luoja, ulkoinen_id, jarjestelma)
VALUES (:yllapitokohde, :tyon_tyyppi :: MAARAMUUTOS_TYON_TYYPPI, :tyo, :yksikko, :tilattu_maara,
                        :ennustettu_maara, :toteutunut_maara,
                        :yksikkohinta, :luoja, :ulkoinen_id, :jarjestelma);

-- name: paivita-yllapitokohteen-maaramuutos<!
UPDATE yllapitokohteen_maaramuutos ym
SET
  tyon_tyyppi      = :tyon_tyyppi :: MAARAMUUTOS_TYON_TYYPPI,
  tyo              = :tyo,
  yksikko          = :yksikko,
  tilattu_maara    = :tilattu_maara,
  ennustettu_maara = :ennustettu_maara,
  toteutunut_maara = :toteutunut_maara,
  yksikkohinta     = :yksikkohinta,
  muokattu         = NOW(),
  muokkaaja        = :kayttaja,
  poistettu        = :poistettu
WHERE id = :id
      AND (SELECT urakka
           FROM yllapitokohde
           WHERE id = ym.yllapitokohde) = :urakka;

-- name: yllapitokohteella-paallystysilmoitus
SELECT EXISTS(SELECT id
              FROM paallystysilmoitus
              WHERE paallystyskohde = :yllapitokohde);

-- name: poista-yllapitokohteen-jarjestelman-kirjaamat-maaramuutokset!
DELETE FROM yllapitokohteen_maaramuutos
WHERE yllapitokohde = :yllapitokohdeid AND
      jarjestelma = :jarjestelma;

-- name: avaa-paallystysilmoituksen-lukko!
UPDATE paallystysilmoitus
SET tila = 'valmis' :: PAALLYSTYSTILA
WHERE paallystyskohde = :yllapitokohde_id;

-- name: lukitse-paallystysilmoitus!
UPDATE paallystysilmoitus
SET tila = 'lukittu' :: PAALLYSTYSTILA
WHERE paallystyskohde = :yllapitokohde_id;

-- name: hae-urakan-maksuerat
SELECT
  ypk.id,
  ypk.kohdenumero,
  ypk.nimi,
  ypk.tunnus,
  ypk.tr_numero                 AS "tr-numero",
  ym.id                         AS "maksuera_id",
  ym.sisalto                    AS "maksuera_sisalto",
  ym.maksueranumero             AS "maksuera_maksueranumero",
  ymt.maksueratunnus,
  ypkk.sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi           AS "bitumi-indeksi",
  ypkk.kaasuindeksi,
  sum(-s.maara)                 AS "sakot-ja-bonukset" -- Käännetään, jotta laskenta toimii suoraan oikein
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_maksuera ym ON ym.yllapitokohde = ypk.id
  LEFT JOIN yllapitokohteen_maksueratunnus ymt ON ymt.yllapitokohde = ypk.id
  LEFT JOIN laatupoikkeama lp ON (lp.yllapitokohde = ypk.id AND lp.urakka = ypk.urakka AND lp.poistettu IS NOT TRUE)
  LEFT JOIN sanktio s ON s.laatupoikkeama = lp.id AND s.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id
WHERE ypk.urakka = :urakka
      AND ypk.sopimus = :sopimus
      AND ypk.poistettu IS NOT TRUE
      AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
                                         OR vuodet @> ARRAY [:vuosi] :: INT []))
GROUP BY ypk.id, ym.id, ymt.maksueratunnus, ypkk.sopimuksen_mukaiset_tyot, ypkk.arvonvahennykset, ypkk.bitumi_indeksi, ypkk.kaasuindeksi;

-- name: hae-yllapitokohteen-maksuera
SELECT
  ym.sisalto,
  ym.maksueranumero,
  ypk.id,
  ypk.kohdenumero,
  ypk.nimi
FROM yllapitokohde ypk
  LEFT JOIN yllapitokohteen_maksuera ym ON ym.yllapitokohde = ypk.id
WHERE yllapitokohde = :yllapitokohde
      AND maksueranumero = :maksueranumero
      AND ypk.poistettu IS NOT TRUE;

-- name: luo-maksuera<!
INSERT INTO yllapitokohteen_maksuera (yllapitokohde, maksueranumero, sisalto)
VALUES (:yllapitokohde, :maksueranumero, :sisalto);

-- name: paivita-maksuera<!
UPDATE yllapitokohteen_maksuera
SET
  sisalto        = :sisalto,
  maksueranumero = :maksueranumero
WHERE yllapitokohde = :yllapitokohde
      AND maksueranumero = :maksueranumero;

-- name: hae-yllapitokohteen-maksueratunnus
SELECT maksueratunnus
FROM yllapitokohteen_maksueratunnus
WHERE yllapitokohde = :yllapitokohde;

-- name: luo-maksueratunnus<!
INSERT INTO yllapitokohteen_maksueratunnus (yllapitokohde, maksueratunnus)
VALUES (:yllapitokohde, :maksueratunnus);

-- name: paivita-maksueratunnus<!
UPDATE yllapitokohteen_maksueratunnus
SET
  maksueratunnus = :maksueratunnus
WHERE yllapitokohde = :yllapitokohde;

-- name: paivita-paallystysilmoituksen-takuupvm!
UPDATE paallystysilmoitus
SET
  takuupvm = :takuupvm
WHERE id = :id;

-- name: paivita-yllapitokohde!
-- Päivittää ylläpitokohteen
UPDATE yllapitokohde
SET
  tr_alkuosa                        = :tr-alkuosa,
  tr_alkuetaisyys                   = :tr-alkuetaisyys,
  tr_loppuosa                       = :tr-loppuosa,
  tr_loppuetaisyys                  = :tr-loppuetaisyys,
  muokattu                          = now(),
  muokkaaja                         = :muokkaaja
WHERE id = :id
      AND urakka = :urakka;

-- name: virheen-tiedot
SELECT nimi, tunnus, kohdenumero, yhaid
FROM yllapitokohde
WHERE yhaid IN (:ulkoiset-idt);

-- name: paivita-pot2-paallystekerros<!
UPDATE pot2_paallystekerros
   SET kohdeosa_id = :kohdeosa_id,
       toimenpide = :toimenpide,
       materiaali = :materiaali,
       leveys = :leveys,
       massamenekki = :massamenekki,
       pinta_ala = :pinta_ala,
       kokonaismassamaara = :kokonaismassamaara,
       piennar = :piennar,
       lisatieto = :lisatieto,
       pot2_id = :pot2_id
 WHERE id = :pot2p_id;

-- name: luo-pot2-paallystekerros<!
INSERT INTO pot2_paallystekerros
    (kohdeosa_id, toimenpide, materiaali, leveys, massamenekki,
     pinta_ala, kokonaismassamaara, piennar, lisatieto, pot2_id)
     VALUES (:kohdeosa_id, :toimenpide, :materiaali, :leveys, :massamenekki,
             :pinta_ala, :kokonaismassamaara, :piennar, :lisatieto, :pot2_id);

-- name: paivita-pot2-alusta<!
UPDATE pot2_alusta
   SET tr_numero = :tr-numero,
       tr_alkuetaisyys = :tr-alkuetaisyys,
       tr_alkuosa = :tr-alkuosa,
       tr_loppuetaisyys = :tr-loppuetaisyys,
       tr_loppuosa = :tr-loppuosa,
       tr_ajorata = :tr-ajorata,
       tr_kaista = :tr-kaista,
       toimenpide = :toimenpide,
       lisatty_paksuus = :lisatty-paksuus,
       massamaara = :massamaara,
       murske = :murske,
       kasittelysyvyys = :kasittelysyvyys,
       leveys = :leveys,
       pinta_ala = :pinta-ala,
       kokonaismassamaara = :kokonaismassamaara,
       massa = :massa,
       sideaine = :sideaine,
       sideainepitoisuus = :sideainepitoisuus,
       seosaine = :seosaine,
       verkon_tyyppi = :verkon-tyyppi,
       verkon_sijainti = :verkon-sijainti,
       verkon_tarkoitus = :verkon-tarkoitus,
       pot2_id = :pot2_id
 WHERE id = :pot2a_id;

-- name: luo-pot2-alusta<!
INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys,
                         tr_loppuosa, tr_ajorata, tr_kaista, toimenpide,
                         lisatty_paksuus, massamaara, murske,
                         kasittelysyvyys, leveys, pinta_ala,
                         kokonaismassamaara, massa, sideaine,
                         sideainepitoisuus, seosaine,
                         verkon_tyyppi, verkon_sijainti, verkon_tarkoitus,
                         pot2_id)
VALUES (:tr-numero, :tr-alkuetaisyys, :tr-alkuosa, :tr-loppuetaisyys,
        :tr-loppuosa, :tr-ajorata, :tr-kaista, :toimenpide,
        :lisatty-paksuus, :massamaara, :murske,
        :kasittelysyvyys, :leveys, :pinta-ala,
        :kokonaismassamaara, :massa, :sideaine,
        :sideainepitoisuus, :seosaine,
        :verkon-tyyppi, :verkon-sijainti, :verkon-tarkoitus,
        :pot2_id);

-- name: poista-pot2-alustarivit!
UPDATE pot2_alusta
   SET poistettu = TRUE
 WHERE id IN (:pot2a_idt);