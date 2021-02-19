-- name: paivita-paikkauskohteen-ilmoitettu-virhe!
-- Päivittää paikkauskohteen ilmoitetun virheen
UPDATE paikkauskohde
    SET
    "ilmoitettu-virhe"              = :ilmoitettu-virhe
WHERE id = :id;

-- name: merkitse-paikkauskohde-tarkistetuksi!
-- Päivittää paikkauskohteen tarkistaja-idn ja aikaleiman
UPDATE paikkauskohde
    SET
    tarkistettu                 = NOW(),
    "tarkistaja-id"             = :tarkistaja-id
WHERE id = :id;

-- name: hae-paikkauskohteen-mahdolliset-kustannukset
SELECT pk.id              AS "paikkauskohde_id",
       pk.nimi            AS "paikkauskohde_nimi",
       pt.tyomenetelma,
       pt.valmistumispvm,
       (pt.tierekisteriosoite).tie AS tie,
       (pt.tierekisteriosoite).aosa AS aosa,
       (pt.tierekisteriosoite).aet AS aet,
       (pt.tierekisteriosoite).losa AS losa,
       (pt.tierekisteriosoite).let AS let,
       pt.kirjattu,
       pt.hinta,
       pt.id              AS "paikkaustoteuma-id",
       pt.poistettu       AS "paikkaustoteuma-poistettu"
FROM paikkaustoteuma pt
     JOIN paikkauskohde pk ON (pt."paikkauskohde-id"=pk.id AND
                                   pt.tyyppi = :tyyppi::paikkaustoteumatyyppi AND
                                   pt.poistettu IS NOT TRUE)
WHERE pt."urakka-id"=:urakka-id AND
      (:alkuaika :: TIMESTAMP IS NULL OR pt.valmistumispvm >= :alkuaika) AND
      (:loppuaika :: TIMESTAMP IS NULL OR pt.valmistumispvm <= :loppuaika) AND
      (:numero :: INTEGER IS NULL OR (pt.tierekisteriosoite).tie = :numero) AND
      (:alkuosa :: INTEGER IS NULL OR (pt.tierekisteriosoite).aosa >= :alkuosa) AND
      (:alkuetaisyys :: INTEGER IS NULL OR
       ((pt.tierekisteriosoite).aet >= :alkuetaisyys AND
        ((pt.tierekisteriosoite).aosa = :alkuosa OR
         :alkuosa :: INTEGER IS NULL)) OR
       (pt.tierekisteriosoite).aosa > :alkuosa) AND
      (:loppuosa :: INTEGER IS NULL OR (pt.tierekisteriosoite).losa <= :loppuosa) AND
      (:loppuetaisyys :: INTEGER IS NULL OR
       ((pt.tierekisteriosoite).let <= :loppuetaisyys AND
        ((pt.tierekisteriosoite).losa = :loppuosa OR
         :loppuosa :: INTEGER IS NULL)) OR
       (pt.tierekisteriosoite).losa < :loppuosa) AND
      (:paikkaus-idt :: INTEGER [] IS NULL OR pk.id = ANY (:paikkaus-idt :: INTEGER [])) AND
      (:tyomenetelmat :: VARCHAR [] IS NULL OR pt.tyomenetelma = ANY (:tyomenetelmat :: VARCHAR []));

--name: paivita-paikkaustoteuma!
UPDATE paikkaustoteuma
   SET hinta = :hinta,
       poistettu = :poistettu,
       "poistaja-id" = :poistaja,
       "muokkaaja-id" = :muokkaaja,
       muokattu = NOW(),
       tierekisteriosoite = ROW(:tie, :aosa, :aet, :losa, :let, NULL)::tr_osoite,
       valmistumispvm = :valmistumispvm
 WHERE id = :paikkaustoteuma-id;

--name: luo-paikkaustoteuma!
INSERT INTO paikkaustoteuma("urakka-id", "paikkauskohde-id", "luoja-id", luotu,
                            tyyppi, hinta, kirjattu,
                            tyomenetelma, valmistumispvm, tierekisteriosoite)
 VALUES(:urakka, :paikkauskohde, :luoja, NOW(),
        :tyyppi::paikkaustoteumatyyppi, :hinta, NOW(),
        :tyomenetelma, :valmistumispvm, ROW(:tie, :aosa, :aet, :losa, :let, NULL)::tr_osoite);

--name: hae-paikkauskohteen-tierekisteriosoite
  WITH tr_alku AS (
      SELECT id, tierekisteriosoite as tr1
        FROM paikkaus p1
       WHERE "paikkauskohde-id" = :kohde
       ORDER BY (p1.tierekisteriosoite).aosa,
                (p1.tierekisteriosoite).aet limit 1),
   tr_loppu AS (
      SELECT id, tierekisteriosoite as tr2
        FROM paikkaus p2
       WHERE "paikkauskohde-id" = :kohde
       ORDER BY (p2.tierekisteriosoite).losa DESC,
                (p2.tierekisteriosoite).let DESC limit 1)
SELECT (tr1).tie as tie,
       (tr1).aosa,
       (tr1).aet,
       (tr2).losa,
       (tr2).let from tr_alku, tr_loppu
WHERE (tr1).tie = (tr2).tie;

--name: hae-paikkauskohteen-harja-id
--single?: true
SELECT id
  FROM paikkauskohde
 WHERE "ulkoinen-id" = :ulkoinen-id;

--name: paikkauskohteet-urakalle
-- Haetaan urakan paikkauskohteet ja mahdollisesti jotain tarkentavaa dataa
SELECT pk.id                        AS id,
       pk.nimi                      AS nimi,
       pk.nro                       AS nro,
       pk.luotu                     AS luotu,
       pk.muokattu                  AS muokattu,
       pk."urakka-id"               AS "urakka-id",
       pk.tyomenetelma              AS tyomenetelma,
       pk.tyomenetelma_kuvaus       AS "tyomenetelma-kuvaus",
       pk.alkupvm                   AS alkupvm,
       pk.loppupvm                  AS loppupvm,
       pk."paikkauskohteen-tila"    AS "paikkauskohteen-tila",
       pk."suunniteltu-hinta"       AS "suunniteltu-hinta",
       pk."suunniteltu-maara"       AS "suunniteltu-maara",
       pk.yksikko                   AS yksikko,
       pk.lisatiedot              AS lisatiedot,
       (pk.tierekisteriosoite).tie  AS tie,
       (pk.tierekisteriosoite).aosa AS aosa,
       (pk.tierekisteriosoite).aet  AS aet,
       (pk.tierekisteriosoite).losa AS losa,
       (pk.tierekisteriosoite).let  AS let,
       CASE
           WHEN (pk.tierekisteriosoite).tie IS NOT NULL THEN
               (SELECT *
                FROM tierekisteriosoitteelle_viiva(
                        CAST((pk.tierekisteriosoite).tie AS INTEGER),
                        CAST((pk.tierekisteriosoite).aosa AS INTEGER), CAST((pk.tierekisteriosoite).aet AS INTEGER),
                        CAST((pk.tierekisteriosoite).losa AS INTEGER), CAST((pk.tierekisteriosoite).let AS INTEGER)))
           ELSE NULL
           END                   AS geometria
FROM paikkauskohde pk
 WHERE pk."urakka-id" = :urakka-id
   AND pk.poistettu = false
   -- paikkauskohteen-tila kentällä määritellään, näkyykö paikkauskohde paikkauskohdelistassa
   AND pk."paikkauskohteen-tila" IS NOT NULL;

--name:paivita-paikkauskohde!
UPDATE paikkauskohde
SET "ulkoinen-id"          = :ulkoinen-id,
    nimi                   = :nimi,
    poistettu              = :poistettu,
    "muokkaaja-id"         = :muokkaaja-id,
    muokattu               = :muokattu,
    "yhalahetyksen-tila"   = :yhalahetyksen-tila,
    virhe                  = :virhe,
    tarkistettu            = :tarkistettu,
    "tarkistaja-id"        = :tarkistaja-id,
    "ilmoitettu-virhe"     = :ilmoitettu-virhe,
    nro                    = :nro,
    alkupvm               = :alkupvm::TIMESTAMP,
    loppupvm              = :loppupvm::TIMESTAMP,
    tyomenetelma           = :tyomenetelma,
    tyomenetelma_kuvaus    = :tyomenetelma-kuvaus,
    tierekisteriosoite = ROW(:tie, :aosa, :aet, :losa, :let, NULL)::tr_osoite,
    "paikkauskohteen-tila" = :paikkauskohteen-tila::paikkauskohteen_tila,
    "suunniteltu-hinta" = :suunniteltu-hinta,
    "suunniteltu-maara" = :suunniteltu-maara,
    yksikko = :yksikko,
    lisatiedot = :lisatiedot
WHERE id = :id RETURNING id;

--name: luo-uusi-paikkauskohde!
INSERT INTO paikkauskohde ("luoja-id", "ulkoinen-id", nimi, poistettu, luotu,
                           "yhalahetyksen-tila", virhe, nro, alkupvm, loppupvm, tyomenetelma,
                           "tyomenetelma_kuvaus", tierekisteriosoite, "paikkauskohteen-tila", "urakka-id",
                           "suunniteltu-hinta", "suunniteltu-maara", yksikko, lisatiedot)
VALUES (:luoja-id,
        :ulkoinen-id,
        :nimi,
        false,
        :luotu,
        :yhalahetyksen-tila,
        :virhe,
        :nro,
        :alkupvm::TIMESTAMP,
        :loppupvm::TIMESTAMP,
        :tyomenetelma,
        :tyomenetelma-kuvaus,
        ROW(:tie, :aosa, :aet, :losa, :let, NULL)::tr_osoite,
        :paikkauskohteen-tila::paikkauskohteen_tila,
        :urakka-id,
        :suunniteltu-hinta,
        :suunniteltu-maara,
        :yksikko,
        :lisatiedot)
        RETURNING id;