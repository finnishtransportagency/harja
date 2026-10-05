-- name: hae-tiemerkinta-kustannuskirjaukset
SELECT
    ukk.id,
    ukk.urakka,
    ukk.kustannusvuosi,
    ukk.kustannus,
    ukk.pk1,
    ukk.pk2,
    ukk.pk3
    FROM tiemerkinta_korjauskustannus ukk
WHERE ukk.urakka = :urakka
ORDER BY ukk.kustannusvuosi ASC;

-- name: hae-tiemerkinta-kustannuskirjaus-kustannusvuodella
SELECT
    ukk.id,
    ukk.urakka,
    ukk.kustannusvuosi
    FROM tiemerkinta_korjauskustannus ukk
WHERE ukk.urakka = :urakka AND ukk.kustannusvuosi = :kustannusvuosi
ORDER BY ukk.kustannusvuosi ASC;

--name: lisaa-tiemerkinta-kustannuskirjaus!
INSERT INTO tiemerkinta_korjauskustannus (urakka, luoja, muokattu, muokkaaja, kustannusvuosi, kustannus, pk1, pk2, pk3)
VALUES (
           :urakka,
           :luoja,
           :muokattu,
           :muokkaaja,
           :kustannusvuosi,
           :kustannus,
           :pk1,
           :pk2,
           :pk3);

--name: paivita-tiemerkinta-kustannuskirjaus!
UPDATE tiemerkinta_korjauskustannus
SET
    muokattu = :muokattu,
    muokkaaja = :muokkaaja,
    kustannus = :kustannus,
    pk1 = :pk1,
    pk2 = :pk2,
    pk3 = :pk3
WHERE urakka = :urakka AND kustannusvuosi = :kustannusvuosi;

-- name: hae-tiemerkinta-kustannustyypit
SELECT unnest(enum_range(NULL::yllapito_muu_toteuma_tyyppi)) AS tyyppi;


-- name: hae-urakan-yllapitokohteiden-kustannukset
SELECT
  ypk.id,
  COALESCE(CAST(ypk.kohdenumero AS TEXT), '-')           AS "kohdenumero",
  ypk.yhaid                                              AS "yha-id",
  ypk.nimi,
  ypk.urakka,
  ypk.tr_numero                                          AS tie,
  ypk.tr_alkuosa                                         AS alkuosa,
  ypk.tr_alkuetaisyys                                    AS alkuetaisyys,
  ypk.tr_loppuosa                                        AS loppuosa,
  ypk.tr_loppuetaisyys                                   AS loppuetaisyys,
  COALESCE(CAST(ypk.pkluokka AS TEXT), 'Ei tiedossa')    AS "pk-luokka",
  COALESCE(tyk.linjamerkinnat, 0)                        AS "linjamerkinnat",
  COALESCE(tyk.pienmerkinnat, 0)                         AS "pienmerkinnat",
  COALESCE(tyk.jyrsinnat, 0)                             AS "jyrsinnat",
  COALESCE(tyk.muut_kustannukset, 0)                     AS "muut-kustannukset"
FROM yllapitokohde ypk
LEFT JOIN tiemerkinta_yllapitokohteen_kustannus tyk ON ypk.id = tyk.yllapitokohde
WHERE
  ypk.suorittava_tiemerkintaurakka = :urakka
  AND ypk.yllapitokohdetyotyyppi = :yllapitokohdetyotyyppi :: YLLAPITOKOHDETYOTYYPPI
  -- Jos passataan 0, hae kaikki vuodet
  AND (:vuosi = 0 OR ypk.vuodet @> ARRAY[:vuosi]::INTEGER[])
  AND ypk.poistettu IS FALSE
ORDER BY coalesce(ypk.muokattu,  ypk.luotu) DESC;


-- name: hae-urakan-paikkauskohteiden-kustannukset
SELECT
  pk.id,
  pk."ulkoinen-id"                         AS kohdenumero,
  pk.nimi,
  (pk.tierekisteriosoite_laajennettu).tie  AS tie,
  (pk.tierekisteriosoite_laajennettu).aosa AS alkuosa,
  (pk.tierekisteriosoite_laajennettu).aet  AS alkuetaisyys,
  (pk.tierekisteriosoite_laajennettu).losa AS loppuosa,
  (pk.tierekisteriosoite_laajennettu).let  AS loppuetaisyys,
  pk.pkluokka                              AS "pk-luokka",
  COALESCE(tpk.linjamerkinnat, 0)          AS "linjamerkinnat",
  COALESCE(tpk.pienmerkinnat, 0)           AS "pienmerkinnat",
  COALESCE(tpk.jyrsinnat, 0)               AS "jyrsinnat",
  COALESCE(tpk.muut_kustannukset, 0)       AS "muut-kustannukset"
FROM paikkauskohde pk
    LEFT JOIN tiemerkinta_paikkauskohteen_kustannus tpk ON pk.id = tpk.paikkauskohde
WHERE pk.suorittava_tiemerkintaurakka = :urakka-id
-- Jos passataan 0, hae kaikki vuodet
AND (:vuosi = 0 OR EXTRACT(YEAR FROM pk.alkupvm) = :vuosi)
AND pk.poistettu = false
-- Näytetään paikkauskohde vasta kun tiemerkintä on merkattu valmiiksi
AND pk."tiemerkinnan-tila" = 'valmis'
ORDER BY coalesce(pk.muokattu,  pk.luotu) DESC;

-- name: hae-yllapitokustannus
SELECT
    ypk.id
FROM tiemerkinta_yllapitokohteen_kustannus ypk   
WHERE ypk.yllapitokohde = :yllapitokohde;

--name: lisaa-tiemerkinta-yllapitokohde-kustannuskirjaus!
INSERT INTO tiemerkinta_yllapitokohteen_kustannus (yllapitokohde, linjamerkinnat, pienmerkinnat, jyrsinnat, muut_kustannukset, muokattu, muokkaaja, luoja)
VALUES (:id,
       :linjamerkinnat,
       :pienmerkinnat,
       :jyrsinnat,
       :muut-kustannukset,
       :muokattu,
       :muokkaaja,
       :luoja)
RETURNING id;

--name: paivita-tiemerkinta-yllapitokohde-kustannuskirjaus!
UPDATE tiemerkinta_yllapitokohteen_kustannus
SET
    muokattu = :muokattu,
    muokkaaja = :muokkaaja,
    linjamerkinnat = :linjamerkinnat,
    pienmerkinnat = :pienmerkinnat,
    jyrsinnat = :jyrsinnat,
    muut_kustannukset = :muut-kustannukset
WHERE yllapitokohde = :id
RETURNING id;

-- name: hae-paikkauskustannus
SELECT
    tpk.id
FROM tiemerkinta_paikkauskohteen_kustannus tpk   
WHERE tpk.paikkauskohde = :paikkauskohde;

--name: lisaa-tiemerkinta-paikkauskohde-kustannuskirjaus!
INSERT INTO tiemerkinta_paikkauskohteen_kustannus (paikkauskohde, linjamerkinnat, pienmerkinnat, jyrsinnat, muut_kustannukset, muokattu, muokkaaja, luoja)
VALUES (:id,
       :linjamerkinnat,
       :pienmerkinnat,
       :jyrsinnat,
       :muut-kustannukset,
       :muokattu,
       :muokkaaja,
       :luoja)
RETURNING id;

--name: paivita-tiemerkinta-paikkauskohde-kustannuskirjaus!
UPDATE tiemerkinta_paikkauskohteen_kustannus
SET
    muokattu = :muokattu,
    muokkaaja = :muokkaaja,
    linjamerkinnat = :linjamerkinnat,
    pienmerkinnat = :pienmerkinnat,
    jyrsinnat = :jyrsinnat,
    muut_kustannukset = :muut-kustannukset
WHERE paikkauskohde = :id
RETURNING id;

-- name: analytiikalle-tiemerkintaurakat-kannasta
-- Haetaan annetulle aikavälille kaikki tiemerkintäurakat, joilla on kustannuksia
SELECT DISTINCT u.id as urakkaid, u.urakkanro
  FROM urakka u
        JOIN tiemerkinta_korjauskustannus ukk ON ukk.urakka = u.id
 WHERE ukk.luotu BETWEEN :alkupvm AND :loppupvm
   OR ukk.muokattu BETWEEN :alkupvm AND :loppupvm
UNION
SELECT DISTINCT u.id as urakkaid, u.urakkanro
FROM urakka u
    JOIN yllapitokohde ypk ON ypk.urakka = u.id
    JOIN tiemerkinta_yllapitokohteen_kustannus tyk ON ypk.id = tyk.yllapitokohde
WHERE ypk.luotu BETWEEN :alkupvm AND :loppupvm
   OR ypk.muokattu BETWEEN :alkupvm AND :loppupvm
UNION
SELECT DISTINCT u.id as urakkaid, u.urakkanro
FROM urakka u
    JOIN paikkauskohde pk ON pk."urakka-id" = u.id
     JOIN tiemerkinta_paikkauskohteen_kustannus tpk ON pk.id = tpk.paikkauskohde
WHERE tpk.luotu BETWEEN :alkupvm AND :loppupvm
   OR tpk.muokattu BETWEEN :alkupvm AND :loppupvm;

-- name: hae-analytiikalle-tiemerkinta-korjauskustannukset
-- Hakee tiemerkintäurakan korjauskustannukset aikaväliltä
SELECT ukk.kustannusvuosi         AS sopimusvuosi,
       ukk.id                     AS "korjauskustannus-id",
       COALESCE(ukk.kustannus, 0) AS "kustannus",
       COALESCE(ukk.pk1, 0)       AS "pk1%",
       COALESCE(ukk.pk2, 0)       AS "pk2%",
       COALESCE(ukk.pk3, 0)       AS "pk3%",
       ukk.luotu                  as "korjauskustannus-luotu",
       ukk.muokattu               as "korjauskustannus-muokattu"
FROM tiemerkinta_korjauskustannus ukk
WHERE (COALESCE(ukk.muokattu, ukk.luotu) BETWEEN :alkupvm AND :loppupvm)
  AND ukk.urakka = :urakkaid
ORDER BY sopimusvuosi ASC;

-- name: hae-analytiikalle-tiemerkinta-yllapitokohde-kustannukset
-- Hakee ylläpitokohteiden tiemerkintäkustannukset aikaväliltä
SELECT ypk.vuodet                         AS sopimusvuosi,
       ypk.id                             AS "paallystyskohde-id",
       ypk.kohdenumero,
       ypk.nimi,
       COALESCE(tyk.linjamerkinnat, 0)    AS "linjamerkinnat",
       COALESCE(tyk.pienmerkinnat, 0)     AS "pienmerkinnat",
       COALESCE(tyk.jyrsinnat, 0)         AS "jyrsinnat",
       COALESCE(tyk.muut_kustannukset, 0) AS "muut-kustannukset",
       ypk.poistettu                      AS "paallystyskohde-poistettu",
       tyk.luotu                          as "paallystyskohde-kustannus-luotu",
       tyk.muokattu                       as "paallystyskohde-kustannus-muokattu"
FROM yllapitokohde ypk
         JOIN tiemerkinta_yllapitokohteen_kustannus tyk ON ypk.id = tyk.yllapitokohde
WHERE (COALESCE(ypk.muokattu, ypk.luotu) BETWEEN :alkupvm AND :loppupvm)
  AND ypk.urakka = :urakkaid
ORDER BY sopimusvuosi ASC;

-- name: hae-analytiikalle-tiemerkinta-paikkauskohde-kustannukset
-- Hakee paikkauskohteiden tiemerkintäkustannukset aikaväliltä
SELECT extract(YEAR FROM pk.alkupvm)            AS sopimusvuosi,
       pk.id                                    AS "paikkauskohde-id",
       pk."ulkoinen-id"::TEXT                   AS "ulkoinen-id",
       pk.nimi,
       COALESCE(tpk.linjamerkinnat, 0)          AS "linjamerkinnat",
       COALESCE(tpk.pienmerkinnat, 0)           AS "pienmerkinnat",
       COALESCE(tpk.jyrsinnat, 0)               AS "jyrsinnat",
       COALESCE(tpk.muut_kustannukset, 0)       AS "muut-kustannukset",
       pk.poistettu                             AS "paikkauskohde-poistettu",
       tpk.luotu                                as "paikkauskohde-kustannus-luotu",
       tpk.muokattu                             as "paikkauskohde-kustannus-muokattu"
FROM paikkauskohde pk
     JOIN tiemerkinta_paikkauskohteen_kustannus tpk ON pk.id = tpk.paikkauskohde
WHERE (COALESCE(tpk.muokattu, tpk.luotu) BETWEEN :alkupvm AND :loppupvm)
  AND pk."urakka-id" = :urakkaid
ORDER BY sopimusvuosi ASC;
