-- name: hae-urakan-paallystysilmoitukset
-- Hakee urakan päällystysilmoitukset joko päällystysilmoitukset tabille tai paikkauskohteisiin
-- Paikkauskohteet erotellaan mukaan tai pois paikkauskohteet parametrin kautta. Jos paikkauskohteet
-- parametri on annettu, niin haetaan vain ne ylläpitokohteet, jotka on linkitetty paikkauskohteisiin.
SELECT
  ypk.id                        AS "paallystyskohde-id",
  pi.id,
  ypk.tr_numero                 AS "tr-numero",
  pi.tila,
  pi.versio                     AS "versio",
  ypk.nimi,
  ypk.kohdenumero,
  ypk.yhaid,
  ypk.tunnus,
  pi.paatos_tekninen_osa        AS "paatos-tekninen-osa",
  ypkk.sopimuksen_mukaiset_tyot AS "sopimuksen-mukaiset-tyot",
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi           AS "bitumi-indeksi",
  ypkk.kaasuindeksi,
  ypk.lahetetty                 AS lahetetty,
  lahetys_onnistunut            AS "lahetys-onnistunut",
  lahetysvirhe,
  ypk.velho_lahetyksen_aika     AS "velho-lahetyksen-aika",
  ypk.velho_lahetyksen_tila     AS "velho-lahetyksen-tila",
  ypk.velho_lahetyksen_vastaus  AS "velho-lahetyksen-vastaus",
  pi.takuupvm                   AS takuupvm,
  pi.muokattu,
  ypk.yha_tr_osoite             AS "yha-tr-osoite",
  pktm.nimi                     AS "tyomenetelma",
  u.hallintayksikko             AS "ely",
  p.id                          AS "paikkauskohde-id"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                           AND pi.poistettu IS NOT TRUE
  LEFT JOIN yllapitokohteen_kustannukset ypkk ON ypkk.yllapitokohde = ypk.id

  LEFT JOIN paikkauskohde p ON p."yllapitokohde-id" = ypk.id
  LEFT JOIN paikkauskohde_tyomenetelma pktm ON p.tyomenetelma = pktm.id
  left join urakka u on u.id = ypk.urakka
WHERE ypk.urakka = :urakka
  AND ypk.sopimus = :sopimus
  AND ypk.yllapitokohdetyotyyppi = 'paallystys' :: YLLAPITOKOHDETYOTYYPPI
  AND (:vuosi :: INTEGER IS NULL OR (cardinality(vuodet) = 0
                                         OR vuodet @> ARRAY [:vuosi] :: INT []))
  AND ypk.poistettu IS NOT TRUE
  AND ((:paikkauskohteet ::TEXT IS NULL AND p."yllapitokohde-id" IS NULL)
   OR (:paikkauskohteet ::TEXT IS NOT NULL AND p."yllapitokohde-id" IS NOT NULL));

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
  pi.muokattu,
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
  ypkk.maaramuutokset,
  ypkk.arvonvahennykset,
  ypkk.bitumi_indeksi           AS "bitumi-indeksi",
  ypkk.maku_paallysteet         AS "maku-paallysteet",
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
  ypk.yha_tr_osoite             AS "yha-tr-osoite",
  -- Paikkauskohteen kääntäminen pot lomakkeeksi vaatii muutamia lisäkenttiä
  p.takuuaika                   AS takuuaika,
  p."toteutunut-hinta"          AS "paikkauskohde-toteutunut-hinta",
  ypka.paallystys_alku          AS "paallystys-alku",
  p.id                          AS "paikkauskohde-id",
  p.nimi                        AS "paikkauskohde-nimi",
  ypk.velho_lahetyksen_aika     AS "velho-lahetyksen-aika",
  ypk.velho_lahetyksen_vastaus  AS "velho-lahetyksen-vastaus",
  ypk.velho_lahetyksen_tila     AS "velho-lahetyksen-tila",
  ypk.lahetysaika,
  ypk.lahetetty,
  ypk.lahetys_onnistunut        AS "lahetys-onnistunut",
  ypk.lahetysvirhe,
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
  LEFT JOIN paikkauskohde p ON p."yllapitokohde-id" = ypk.id
WHERE ypk.id = :paallystyskohde
      AND ypk.poistettu IS NOT TRUE
GROUP BY pi.id, ypk.id, ypko.id, ypka.kohde_alku, ypka.kohde_valmis, ypka.paallystys_loppu,
  ypkk.sopimuksen_mukaiset_tyot, ypkk.maaramuutokset, ypkk.arvonvahennykset, ypkk.bitumi_indeksi, ypkk.kaasuindeksi, ypkk.maku_paallysteet,
  u.id, p.takuuaika, ypka.paallystys_alku, ypka.paallystys_loppu, p.id;

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
  pot2p.lisatieto,
  pot2p.jarjestysnro,
  pot2p.velho_lahetyksen_aika as "velho-lahetyksen-aika",
  pot2p.velho_lahetyksen_vastaus as "velho-lahetyksen-vastaus",
  pot2p.velho_rivi_lahetyksen_tila as "velho-rivi-lahetyksen-tila"
FROM pot2_paallystekerros pot2p
WHERE pot2_id = :pot2_id AND kohdeosa_id = :kohdeosa_id;

-- name: hae-pot2-paallystekerrokset
SELECT
    pot2p.id as "pot2p_id",
    pot2p.pot2_id as "pot-id",
    pot2p.kohdeosa_id as "kohdeosa-id",
    pot2p.toimenpide as "pot2-tyomenetelma",
    pot2p.leveys,
    pot2p.massamenekki,
    pot2p.pinta_ala as "pinta-ala",
    pot2p.kokonaismassamaara,
    pot2p.piennar,
    pot2p.lisatieto,
    pot2p.jarjestysnro,
    pot2p.velho_lahetyksen_aika as "velho-lahetyksen-aika",
    pot2p.velho_rivi_lahetyksen_tila as "velho-rivi-lahetyksen-tila",
    pot2p.velho_lahetyksen_vastaus as "velho-lahetyksen-vastaus",
    pot.luotu as "alkaen", -- velhon "alkaen"
    ypko.tr_ajorata as "tr-ajorata",
    ypko.tr_kaista as "tr-kaista",
    NULL as "karttapaivamaara",
    ypko.tr_numero as "tr-numero",
    ypko.tr_alkuosa as "tr-alkuosa",
    ypko.tr_alkuetaisyys as "tr-alkuetaisyys",
    ypko.tr_loppuosa as "tr-loppuosa",
    ypko.tr_loppuetaisyys as "tr-loppuetaisyys",
    ypko.yllapitokohde as "kohde-id",
    mt.*
FROM pot2_paallystekerros pot2p
         LEFT JOIN pot2_massan_tiedot mt ON pot2p.materiaali = mt.id
         JOIN yllapitokohdeosa ypko ON pot2p.kohdeosa_id = ypko.id
         JOIN paallystysilmoitus pot ON pot.id = pot2p.pot2_id
WHERE pot2p.pot2_id = :pot2_id;


-- name: hae-pot2-alustarivit
SELECT
    pot2a.id as "pot2a_id",
    pot2a.pot2_id as "pot-id",
    pot2a.tr_numero AS "tr-numero",
    pot2a.tr_alkuosa AS "tr-alkuosa",
    pot2a.tr_alkuetaisyys AS "tr-alkuetaisyys",
    pot2a.tr_loppuosa AS "tr-loppuosa",
    pot2a.tr_loppuetaisyys AS "tr-loppuetaisyys",
    pot2a.tr_ajorata AS "tr-ajorata",
    pot2a.tr_kaista AS "tr-kaista",
    pot2a.toimenpide,
    pot2a.velho_lahetyksen_aika as "velho-lahetyksen-aika",
    pot2a.velho_lahetyksen_vastaus as "velho-lahetyksen-vastaus",
    pot2a.velho_rivi_lahetyksen_tila as "velho-rivi-lahetyksen-tila",

    -- toimenpidespesifiset kentät
    pot2a.massa,
    pot2a.murske,
    pot2a.verkon_tyyppi AS "verkon-tyyppi",
    pot2a.verkon_tarkoitus AS "verkon-tarkoitus",
    pot2a.verkon_sijainti AS "verkon-sijainti",
    pot2a.kasittelysyvyys,
    pot2a.lisatty_paksuus AS "lisatty-paksuus",
    pot2a.massamenekki,
    pot2a.leveys,
    pot2a.pinta_ala AS "pinta-ala",
    pot2a.kokonaismassamaara,
    pot2a.sideaine,
    pot2a.sideainepitoisuus,
    pot2a.sideaine2,
    pot.luotu as "alkaen",
    pot.paallystyskohde,
    um.tyyppi as "murske-tyyppi",
    um.rakeisuus,
    um.iskunkestavyys,
    p2mt.*
  FROM pot2_alusta pot2a
  JOIN paallystysilmoitus pot ON pot.id = pot2a.pot2_id AND pot.poistettu IS FALSE
  LEFT JOIN pot2_mk_urakan_murske um ON um.id = pot2a.murske
  LEFT JOIN pot2_massan_tiedot p2mt on p2mt.id = pot2a.massa
 WHERE pot2a.pot2_id = :pot2_id
   AND pot2a.poistettu IS FALSE;

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

-- name: paivita-paallystysilmoituksen-virhe!
UPDATE paallystysilmoitus
   SET virhe = :virhe::TEXT,
       virhe_aikaleima = :aikaleima::TIMESTAMP
 WHERE paallystyskohde = :id;

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

-- name: merkitse-paallystekerros-lahetystiedot-velhoon!
UPDATE pot2_paallystekerros
SET velho_lahetyksen_aika = :aikaleima,
    velho_rivi_lahetyksen_tila = :tila :: velho_rivi_lahetyksen_tila_tyyppi,
    velho_lahetyksen_vastaus = :lahetysvastaus
WHERE jarjestysnro = 1 and kohdeosa_id = :id;

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
       massamenekki = :massamenekki,
       murske = :murske,
       kasittelysyvyys = :kasittelysyvyys,
       leveys = :leveys,
       pinta_ala = :pinta-ala,
       kokonaismassamaara = :kokonaismassamaara,
       massa = :massa,
       sideaine = :sideaine,
       sideainepitoisuus = :sideainepitoisuus,
       sideaine2 = :sideaine2,
       verkon_tyyppi = :verkon-tyyppi,
       verkon_sijainti = :verkon-sijainti,
       verkon_tarkoitus = :verkon-tarkoitus,
       pot2_id = :pot2_id
 WHERE id = :pot2a_id;

-- name: merkitse-alusta-lahetystiedot-velhoon!
UPDATE pot2_alusta
SET velho_lahetyksen_aika = :aikaleima,
    velho_rivi_lahetyksen_tila = :tila :: velho_rivi_lahetyksen_tila_tyyppi,
    velho_lahetyksen_vastaus = :lahetysvastaus
WHERE id = :id;

-- name: luo-pot2-alusta<!
INSERT INTO pot2_alusta (tr_numero, tr_alkuetaisyys, tr_alkuosa, tr_loppuetaisyys,
                         tr_loppuosa, tr_ajorata, tr_kaista, toimenpide,
                         lisatty_paksuus, massamenekki, murske,
                         kasittelysyvyys, leveys, pinta_ala,
                         kokonaismassamaara, massa, sideaine,
                         sideainepitoisuus, sideaine2,
                         verkon_tyyppi, verkon_sijainti, verkon_tarkoitus,
                         pot2_id)
VALUES (:tr-numero, :tr-alkuetaisyys, :tr-alkuosa, :tr-loppuetaisyys,
        :tr-loppuosa, :tr-ajorata, :tr-kaista, :toimenpide,
        :lisatty-paksuus, :massamenekki, :murske,
        :kasittelysyvyys, :leveys, :pinta-ala,
        :kokonaismassamaara, :massa, :sideaine,
        :sideainepitoisuus, :sideaine2,
        :verkon-tyyppi, :verkon-sijainti, :verkon-tarkoitus,
        :pot2_id);

-- name: poista-pot2-alustarivit!
UPDATE pot2_alusta
   SET poistettu = TRUE
 WHERE id IN (:pot2a_idt);

-- name: massan-kayttotiedot
SELECT ypk.nimi, ypk.kohdenumero, (SELECT string_agg(pot.tila::TEXT, ',')) AS tila,
       count(*) AS "kohteiden-lkm", 'paallyste' AS rivityyppi
  FROM pot2_paallystekerros pk
           JOIN pot2_mk_urakan_massa um ON um.id = pk.materiaali
           JOIN yllapitokohdeosa ypko ON pk.kohdeosa_id = ypko.id AND ypko.poistettu IS NOT TRUE
           LEFT JOIN paallystysilmoitus pot ON pk.pot2_id = pot.id
           LEFT JOIN yllapitokohde ypk ON ypk.id = pot.paallystyskohde AND ypk.poistettu IS NOT TRUE
           LEFT JOIN urakka u ON ypk.urakka = u.id
 WHERE pk.materiaali = :id
 group by ypk.nimi, ypk.kohdenumero
 UNION
SELECT ypk.nimi, ypk.kohdenumero, (SELECT string_agg(pot.tila::TEXT, ',')) AS tila,
       count(*) AS "kohteiden-lkm", 'alusta' AS rivityyppi
  FROM pot2_alusta a
           JOIN pot2_mk_urakan_massa um ON um.id = a.massa
           LEFT JOIN paallystysilmoitus pot ON a.pot2_id = pot.id
           LEFT JOIN yllapitokohde ypk ON ypk.id = pot.paallystyskohde
           LEFT JOIN urakka u ON ypk.urakka = u.id
 WHERE a.massa = :id AND a.poistettu IS NOT TRUE
 group by ypk.nimi, ypk.kohdenumero;

-- name: murskeen-kayttotiedot
SELECT ypk.nimi, ypk.kohdenumero, (SELECT string_agg(pot.tila::TEXT, ',')) AS tila, count(*) AS "kohteiden-lkm"
  FROM pot2_alusta a
           JOIN pot2_mk_urakan_murske um ON um.id = a.murske
           LEFT JOIN paallystysilmoitus pot ON a.pot2_id = pot.id
           LEFT JOIN yllapitokohde ypk ON ypk.id = pot.paallystyskohde AND ypk.poistettu IS NOT TRUE
           LEFT JOIN urakka u ON ypk.urakka = u.id
 WHERE a.murske = :id AND a.poistettu IS NOT TRUE
 group by ypk.nimi, ypk.kohdenumero;

-- name: muut-urakat-joissa-materiaaleja
SELECT id, nimi FROM urakka u
        WHERE (u.id IN (:urakat) OR
               u.urakoitsija IN (:organisaatiot) OR
               :jarjestelmavastaava IS TRUE)
          -- ei sallita missään tilanteessa eri urakoitsijan urakoihin näkyvyyttä
          AND u.urakoitsija = (SELECT urakoitsija FROM urakka WHERE id = :valittu_urakka)
          AND u.id != :valittu_urakka
          AND
            (exists(SELECT id FROM pot2_mk_urakan_massa ma WHERE ma.urakka_id = u.id AND ma.poistettu IS FALSE) OR
             exists(SELECT id FROM pot2_mk_urakan_murske mu WHERE mu.urakka_id = u.id AND mu.poistettu IS FALSE))
ORDER BY u.nimi;

-- name: monista-massa<!
INSERT INTO pot2_mk_urakan_massa (urakka_id,
                                  tyyppi,
                                  nimen_tarkenne,
                                  max_raekoko,
                                  kuulamyllyluokka,
                                  dop_nro,
                                  poistettu,
                                  muokkaaja,
                                  muokattu,
                                  luoja,
                                  luotu,
                                  litteyslukuluokka)
SELECT :urakka_id,
        tyyppi,
        nimen_tarkenne,
        max_raekoko,
        kuulamyllyluokka::kuulamyllyluokka,
        dop_nro,
        FALSE,
        NULL,
        NULL,
        :kayttaja,
        NOW(),
        litteyslukuluokka::litteyslukuluokka
  FROM pot2_mk_urakan_massa
 WHERE id = :id;

-- name: monista-massan-runkoaineet<!
INSERT INTO pot2_mk_massan_runkoaine (pot2_massa_id,
                                      tyyppi,
                                      esiintyma,
                                      fillerityyppi,
                                      kuvaus,
                                      kuulamyllyarvo,
                                      litteysluku,
                                      massaprosentti)
SELECT :uusi_pot2_massa_id,
       tyyppi,
       esiintyma,
       fillerityyppi,
       kuvaus,
       kuulamyllyarvo,
       litteysluku,
       massaprosentti
  FROM pot2_mk_massan_runkoaine WHERE pot2_massa_id = :vanha_pot2_massa_id;

-- name: monista-massan-sideaineet<!
INSERT INTO pot2_mk_massan_sideaine (pot2_massa_id,
                                     "lopputuote?",
                                     tyyppi,
                                     pitoisuus)
SELECT :uusi_pot2_massa_id,
       "lopputuote?",
       tyyppi,
       pitoisuus
  FROM pot2_mk_massan_sideaine WHERE pot2_massa_id = :vanha_pot2_massa_id;

-- name: monista-massan-lisaaineet<!
INSERT INTO pot2_mk_massan_lisaaine (pot2_massa_id,
                                     tyyppi,
                                     pitoisuus)
SELECT :uusi_pot2_massa_id,
       tyyppi,
       pitoisuus
  FROM pot2_mk_massan_lisaaine WHERE pot2_massa_id = :vanha_pot2_massa_id;

-- name: monista-murske<!
INSERT INTO pot2_mk_urakan_murske (urakka_id,
                                   nimen_tarkenne,
                                   tyyppi,
                                   esiintyma,
                                   rakeisuus,
                                   iskunkestavyys,
                                   dop_nro,
                                   poistettu,
                                   muokkaaja,
                                   muokattu,
                                   luoja,
                                   luotu,
                                   rakeisuus_tarkenne,
                                   tyyppi_tarkenne,
                                   lahde)
SELECT :urakka_id,
       nimen_tarkenne,
       tyyppi,
       esiintyma,
       rakeisuus,
       iskunkestavyys,
       dop_nro,
       FALSE,
       NULL,
       NULL,
       :kayttaja,
       NOW(),
       rakeisuus_tarkenne,
       tyyppi_tarkenne,
       lahde FROM pot2_mk_urakan_murske WHERE id = :id;

-- name: hae-massan-urakka-id
-- single?: true
SELECT urakka_id
  FROM pot2_mk_urakan_massa
 WHERE id = :id;

-- name: hae-murskeen-urakka-id
-- single?: true
SELECT urakka_id
  FROM pot2_mk_urakan_murske
 WHERE id = :id;

-- name: poista-urakan-massa<!
UPDATE pot2_mk_urakan_massa
   SET poistettu = true
 WHERE id = :id and urakka_id = :urakka_id;

-- name: poista-urakan-murske<!
UPDATE pot2_mk_urakan_murske
   SET poistettu = true
 WHERE id = :id and urakka_id = :urakka_id;

-- name: hae-samannimiset-massat-urakasta
SELECT *
  FROM pot2_mk_urakan_massa
 WHERE tyyppi = :tyyppi AND
         max_raekoko = :max_raekoko AND
         (nimen_tarkenne = :nimen_tarkenne OR nimen_tarkenne IS NULL) AND
         dop_nro = :dop_nro AND
         urakka_id = :urakka_id AND
         id != :id;

-- name: hae-samannimisten-massojen-viimeisin-tarkenne
-- single?: true
SELECT nimen_tarkenne
  FROM pot2_mk_urakan_massa
 WHERE tyyppi = :tyyppi AND
         max_raekoko = :max_raekoko AND
         dop_nro = :dop_nro AND
         urakka_id = :urakka_id AND
         id != :id AND nimen_tarkenne IS NOT NULL
ORDER BY nimen_tarkenne DESC;

-- name: hae-samannimiset-murskeet-urakasta
SELECT *
  FROM pot2_mk_urakan_murske
 WHERE tyyppi = :tyyppi AND
     (nimen_tarkenne = :nimen_tarkenne OR nimen_tarkenne IS NULL) AND
     (dop_nro = :dop_nro OR dop_nro IS NULL) AND
         urakka_id = :urakka_id AND
         id != :id;

-- name: hae-samannimisten-murskeiden-viimeisin-tarkenne
-- single?: true
SELECT nimen_tarkenne
  FROM pot2_mk_urakan_murske
 WHERE tyyppi = :tyyppi AND
     (dop_nro = :dop_nro OR dop_nro IS NULL) AND
         urakka_id = :urakka_id AND
         id != :id AND
     nimen_tarkenne IS NOT NULL
 ORDER BY nimen_tarkenne DESC;

-- name: paivita-massan-nimen-tarkennetta<!
UPDATE pot2_mk_urakan_massa
   SET nimen_tarkenne = :nimen_tarkenne
 WHERE id = :id AND urakka_id = :urakka_id;

-- name: paivita-murskeen-nimen-tarkennetta<!
UPDATE pot2_mk_urakan_murske
   SET nimen_tarkenne = :nimen_tarkenne
 WHERE id = :id AND urakka_id = :urakka_id;

-- name: hae-paikkauskohde-yllapitokohde-idlla
select p.id FROM paikkauskohde p WHERE p."yllapitokohde-id" = :yllapitokohde-id;

-- name: hae-paallystyskohteet-analytiikalle
SELECT ypk.id,
       ypk.yhaid,
       ypk.poistettu,
       ypk.urakka,
       ypk.yha_kohdenumero        AS kohdenumero,
       ypk.yllapitokohdetyotyyppi AS kohdetyyppi,
       ypk.nimi,
       ypk.tunnus,
       ypk.yotyo,
       ypk.tr_numero              AS "tr-numero",
       ypk.tr_alkuosa             AS "tr-alkuosa",
       ypk.tr_alkuetaisyys        AS "tr-alkuetaisyys",
       ypk.tr_loppuosa            AS "tr-loppuosa",
       ypk.tr_loppuetaisyys       AS "tr-loppuetaisyys",
       ypk.karttapaivamaara,
       (k.sopimuksen_mukaiset_tyot +
        k.maaramuutokset +
        k.bitumi_indeksi +
        k.kaasuindeksi +
        k.maku_paallysteet)       AS kokonaishinta
FROM yllapitokohde ypk
         LEFT JOIN yllapitokohteen_kustannukset k ON ypk.id = k.yllapitokohde
WHERE ypk.luotu BETWEEN :alku AND :loppu
   OR ypk.muokattu BETWEEN :alku AND :loppu
   OR k.muokattu BETWEEN :alku AND :loppu
   OR EXISTS(SELECT *
             FROM yllapitokohdeosa osa
             WHERE osa.yllapitokohde = ypk.id
               AND (osa.muokattu BETWEEN :alku AND :loppu
                 OR osa.luotu BETWEEN :alku AND :loppu));

-- name: hae-paallystyksen-alikohteet-analytiikalle
SELECT osa.yllapitokohde,
       osa.yhaid,
       osa.id,
       ST_ASTEXT(st_simplify(osa.sijainti, 1)) AS geometria,
       osa.tr_numero           AS "tr-numero",
       osa.tr_alkuosa          AS "tr-alkuosa",
       osa.tr_alkuetaisyys     AS "tr-alkuetaisyys",
       osa.tr_loppuosa         AS "tr-loppuosa",
       osa.tr_loppuetaisyys    AS "tr-loppuetaisyys",
       osa.tr_kaista           AS "tr-kaista",
       osa.tr_ajorata          AS "tr-ajorata",
       osa.karttapaivamaara,
       osa.paallystetyyppi     AS uusi_paallyste,
       osa.raekoko,
       osa.massamenekki,
       osa.massamaara,
       osa.tyomenetelma
FROM yllapitokohdeosa osa
         LEFT JOIN yllapitokohde kohde ON osa.yllapitokohde = kohde.id
WHERE osa.muokattu BETWEEN :alku AND :loppu
   OR osa.luotu BETWEEN :alku AND :loppu
   OR kohde.muokattu BETWEEN :alku AND :loppu
   OR kohde.luotu BETWEEN :alku AND :loppu;

-- name: hae-paallystyskohteiden-aikataulut-analytiikalle
SELECT yllapitokohde,
       kohde_alku,
       paallystys_alku,
       paallystys_loppu,
       valmis_tiemerkintaan,
       tiemerkinta_takaraja,
       tiemerkinta_alku,
       tiemerkinta_loppu,
       kohde_valmis
FROM yllapitokohteen_aikataulu
WHERE luotu BETWEEN :alku AND :loppu
   OR muokattu BETWEEN :alku AND :loppu;

-- name: hae-paallystysilmoitukset-analytiikalle
SELECT paallystyskohde,
       pi.id,
       ypk.lahetetty,
       ypk.lahetys_onnistunut AS "lahetys-onnistunut",
       takuupvm               AS takuupaivamaara,
       k.toteutunut_hinta     AS "toteutunut-hinta"
FROM paallystysilmoitus pi
         LEFT JOIN yllapitokohde ypk ON pi.paallystyskohde = ypk.id
         LEFT JOIN yllapitokohteen_kustannukset k ON ypk.id = k.yllapitokohde
WHERE pi.luotu BETWEEN :alku AND :loppu
   OR pi.muokattu BETWEEN :alku AND :loppu;

-- name: hae-paallystysilmoitusten-kulutuskerroksen-toimenpiteet-analytiikalle
SELECT pi.id                       AS paallystysilmoitus,
       pk_osa.id                   AS alikohde,
       pk_osa.poistettu            AS poistettu,
       pk_osa.tr_numero            AS "tierekisteriosoitevali_tienumero",
       pk_osa.tr_alkuosa           AS "tierekisteriosoitevali_aosa",
       pk_osa.tr_alkuetaisyys      AS "tierekisteriosoitevali_aet",
       pk_osa.tr_loppuosa          AS "tierekisteriosoitevali_losa",
       pk_osa.tr_loppuetaisyys     AS "tierekisteriosoitevali_let",
       pk_osa.tr_kaista            AS "tierekisteriosoitevali_kaista",
       pk_osa.tr_ajorata           AS "tierekisteriosoitevali_ajorata",
       (SELECT laske_tr_osoitteen_pituus(pk_osa.tr_numero,
                                         pk_osa.tr_alkuosa,
                                         pk_osa.tr_alkuetaisyys,
                                         pk_osa.tr_loppuosa,
                                         pk_osa.tr_loppuetaisyys))
                                   AS pituus,
       pot2pk.leveys               AS leveys,
       pot2pk.pinta_ala            AS "pinta-ala",
       kktp.nimi                   AS paallystetyomenetelma,

       pot2pk.massamenekki         AS massamenekki,
       pot2pk.kokonaismassamaara   AS kokonaismassamaara,
       kk_massa.id                 AS massa_id,
       kk_massatyyppi.nimi         AS massa_massatyyppi,
       kk_massa.kuulamyllyluokka   AS massa_kuulamyllyluokka,
       kk_massa.litteyslukuluokka  AS massa_litteyslukuluokka,
       kk_runkoaine.id             AS massa_runkoaine_id,
       kk_runkoainetyyppi.nimi     AS massa_runkoaine_runkoainetyyppi,
       kk_runkoaine.kuulamyllyarvo AS massa_runkoaine_kuulamyllyarvo,
       kk_runkoaine.litteysluku    AS massa_runkoaine_litteysluku,
       kk_runkoaine.massaprosentti AS massa_runkoaine_massaprosentti,
       kk_runkoaine.fillerityyppi  AS massa_runkoaine_fillerityyppi,
       kk_runkoaine.kuvaus         AS massa_runkoaine_kuvaus,
       kk_sideaine.id              AS massa_sideaine_id,
       kk_sideainetyyppi.nimi      AS massa_sideaine_tyyppi,
       kk_sideaine.pitoisuus       AS massa_sideaine_pitoisuus,
       kk_lisaaine.id              AS massa_lisaaine_id,
       kk_lisaainetyyppi.nimi      AS massa_lisaaine_tyyppi,
       kk_lisaaine.pitoisuus       AS massa_lisaaine_pitoisuus
FROM paallystysilmoitus pi
         LEFT JOIN yllapitokohde ypk ON pi.paallystyskohde = ypk.id
         LEFT JOIN yllapitokohteen_kustannukset k ON ypk.id = k.yllapitokohde
         LEFT JOIN pot2_paallystekerros pot2pk ON pi.id = pot2pk.pot2_id

         LEFT JOIN yllapitokohdeosa pk_osa ON pot2pk.kohdeosa_id = pk_osa.id
         LEFT JOIN pot2_mk_paallystekerros_toimenpide kktp ON pot2pk.toimenpide = kktp.koodi
         LEFT JOIN pot2_mk_urakan_massa kk_massa ON pot2pk.materiaali = kk_massa.id
         LEFT JOIN pot2_mk_massatyyppi kk_massatyyppi ON kk_massa.tyyppi = kk_massatyyppi.koodi
         LEFT JOIN pot2_mk_massan_runkoaine kk_runkoaine ON kk_massa.id = kk_runkoaine.pot2_massa_id
         LEFT JOIN pot2_mk_runkoainetyyppi kk_runkoainetyyppi ON kk_runkoaine.tyyppi = kk_runkoainetyyppi.koodi
         LEFT JOIN pot2_mk_massan_sideaine kk_sideaine ON kk_massa.id = kk_sideaine.pot2_massa_id
         LEFT JOIN pot2_mk_sideainetyyppi kk_sideainetyyppi ON kk_sideaine.tyyppi = kk_sideainetyyppi.koodi
         LEFT JOIN pot2_mk_massan_lisaaine kk_lisaaine ON kk_massa.id = kk_lisaaine.pot2_massa_id
         LEFT JOIN pot2_mk_lisaainetyyppi kk_lisaainetyyppi ON kk_lisaaine.tyyppi = kk_lisaainetyyppi.koodi
WHERE pi.luotu BETWEEN :alku AND :loppu
   OR pi.muokattu BETWEEN :alku AND :loppu;

-- name: hae-paallystysilmoitusten-alustan-toimenpiteet-analytiikalle
SELECT pi.id                           AS paallystysilmoitus,
       alusta.id,
       alusta.poistettu                AS poistettu,
       alusta.tr_numero                AS "tierekisteriosoitevali_tienumero",
       alusta.tr_alkuosa               AS "tierekisteriosoitevali_aosa",
       alusta.tr_alkuetaisyys          AS "tierekisteriosoitevali_aet",
       alusta.tr_loppuosa              AS "tierekisteriosoitevali_losa",
       alusta.tr_loppuetaisyys         AS "tierekisteriosoitevali_let",
       alusta.tr_kaista                AS "tierekisteriosoitevali_kaista",
       alusta.tr_ajorata               AS "tierekisteriosoitevali_ajorata",
       (SELECT laske_tr_osoitteen_pituus(alusta.tr_numero,
                                         alusta.tr_alkuosa,
                                         alusta.tr_alkuetaisyys,
                                         alusta.tr_loppuosa,
                                         alusta.tr_loppuetaisyys))
                                       AS pituus,
       a_toimenpide.nimi               AS kasittelymenetelma,
       alusta.lisatty_paksuus          AS "lisatty-paksuus",
       alusta.kasittelysyvyys          AS kasittelysyvyys,
       verkon_tyyppi.nimi              AS "verkon-tyyppi",
       verkon_tarkoitus.nimi           AS "verkon-tarkoitus",
       verkon_sijainti.nimi            AS "verkon-sijainti",
       alusta.massamenekki             AS massamenekki,
       alusta.kokonaismassamaara       AS kokonaismassamaara,

       alusta_massa.id                 AS massa_id,
       alusta_massatyyppi.nimi         AS massa_massatyyppi,
       alusta_massa.kuulamyllyluokka   AS massa_kuulamyllyluokka,
       alusta_massa.litteyslukuluokka  AS massa_litteyslukuluokka,
       alusta_runkoaine.id             AS massa_runkoaine_id,
       alusta_runkoainetyyppi.nimi     AS massa_runkoaine_runkoainetyyppi,
       alusta_runkoaine.kuulamyllyarvo AS massa_runkoaine_kuulamyllyarvo,
       alusta_runkoaine.litteysluku    AS massa_runkoaine_litteysluku,
       alusta_runkoaine.massaprosentti AS massa_runkoaine_massaprosentti,
       alusta_runkoaine.fillerityyppi  AS massa_runkoaine_fillerityyppi,
       alusta_runkoaine.kuvaus         AS massa_runkoaine_kuvaus,
       alusta_sideaine.id              AS massa_sideaine_id,
       alusta_sideainetyyppi.nimi      AS massa_sideaine_tyyppi,
       alusta_sideaine.pitoisuus       AS massa_sideaine_pitoisuus,
       alusta_lisaaine.id              AS massa_lisaaine_id,
       alusta_lisaainetyyppi.nimi      AS massa_lisaaine_tyyppi,
       alusta_lisaaine.pitoisuus       AS massa_lisaaine_pitoisuus,

       mursketyyppi.nimi               AS murske_tyyppi,
       murske.rakeisuus                AS murske_rakeisuus,
       murske.iskunkestavyys           AS murske_iskunkestavyys
FROM paallystysilmoitus pi
         LEFT JOIN yllapitokohde ypk ON pi.paallystyskohde = ypk.id
         LEFT JOIN yllapitokohteen_kustannukset k ON ypk.id = k.yllapitokohde

         LEFT JOIN pot2_alusta alusta ON pi.id = alusta.pot2_id
         LEFT JOIN pot2_mk_alusta_toimenpide a_toimenpide ON alusta.toimenpide = a_toimenpide.koodi
         LEFT JOIN pot2_verkon_tyyppi verkon_tyyppi ON alusta.verkon_tyyppi = verkon_tyyppi.koodi
         LEFT JOIN pot2_verkon_tarkoitus verkon_tarkoitus ON alusta.verkon_tarkoitus = verkon_tarkoitus.koodi
         LEFT JOIN pot2_verkon_sijainti verkon_sijainti ON alusta.verkon_sijainti = verkon_sijainti.koodi
         LEFT JOIN pot2_mk_urakan_massa alusta_massa ON alusta.massa = alusta_massa.id
         LEFT JOIN pot2_mk_massatyyppi alusta_massatyyppi ON alusta_massa.tyyppi = alusta_massatyyppi.koodi
         LEFT JOIN pot2_mk_massan_runkoaine alusta_runkoaine ON alusta_massa.id = alusta_runkoaine.pot2_massa_id
         LEFT JOIN pot2_mk_runkoainetyyppi alusta_runkoainetyyppi
                   ON alusta_runkoaine.tyyppi = alusta_runkoainetyyppi.koodi
         LEFT JOIN pot2_mk_massan_sideaine alusta_sideaine ON alusta_massa.id = alusta_sideaine.pot2_massa_id
         LEFT JOIN pot2_mk_sideainetyyppi alusta_sideainetyyppi ON alusta_sideaine.tyyppi = alusta_sideainetyyppi.koodi
         LEFT JOIN pot2_mk_massan_lisaaine alusta_lisaaine ON alusta_massa.id = alusta_lisaaine.pot2_massa_id
         LEFT JOIN pot2_mk_lisaainetyyppi alusta_lisaainetyyppi ON alusta_lisaaine.tyyppi = alusta_lisaainetyyppi.koodi
         LEFT JOIN pot2_mk_urakan_murske murske ON alusta.murske = murske.id
         LEFT JOIN pot2_mk_mursketyyppi mursketyyppi ON murske.tyyppi = mursketyyppi.koodi
WHERE pi.luotu BETWEEN :alku AND :loppu
   OR pi.muokattu BETWEEN :alku AND :loppu;

-- name: hae-hoidon-paallystyksen-kulut-analytiikalle
SELECT k.id,
       k.urakka,
       k.poistettu,
       k.erapaiva      AS paivamaara,
       k.kokonaissumma AS summa,
       tr.nimi         AS tehtavaryhma
FROM kulu k
         LEFT JOIN kulu_kohdistus kk ON k.id = kk.kulu
         LEFT JOIN tehtavaryhma tr ON kk.tehtavaryhma = tr.id
         LEFT JOIN tehtavaryhmaotsikko tro ON tr.tehtavaryhmaotsikko_id = tro.id
WHERE tro.otsikko = '4 PÄÄLLYSTEIDEN PAIKKAUS'
  AND (k.luotu BETWEEN :alku AND :loppu OR
       k.muokattu BETWEEN :alku AND :loppu);

-- name: hae-hoidon-paallystyksen-toimenpiteet-analytiikalle
SELECT t.id,
       t.urakka,
       t.poistettu,
       t.alkanut          AS paivamaara,
       tt.maara,
       yksikko,
       tr.nimi            AS tehtavaryhma,
       te.nimi            AS tehtava,
       t.tr_numero        AS tierekisteriosoitevali_tienumero,
       t.tr_alkuosa       AS tierekisteriosoitevali_aosa,
       t.tr_alkuetaisyys  AS tierekisteriosoitevali_aet,
       t.tr_loppuosa      AS tierekisteriosoitevali_losa,
       t.tr_loppuetaisyys AS tierekisteriosoitevali_let
FROM toteuma t
         JOIN toteuma_tehtava tt ON t.id = tt.toteuma
         JOIN tehtava te ON tt.toimenpidekoodi = te.id
         JOIN tehtavaryhma tr ON te.tehtavaryhma = tr.id
         JOIN tehtavaryhmaotsikko tro ON tr.tehtavaryhmaotsikko_id = tro.id
WHERE tro.otsikko = '4 PÄÄLLYSTEIDEN PAIKKAUS'
  AND (t.luotu BETWEEN :alku AND :loppu OR
       t.muokattu BETWEEN :alku AND :loppu);
