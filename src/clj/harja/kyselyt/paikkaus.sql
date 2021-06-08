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
      (:tyomenetelmat :: VARCHAR [] IS NULL OR pt.tyomenetelma::VARCHAR = ANY (:tyomenetelmat :: VARCHAR []));

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
SELECT pk.id                                       AS id,
       pk.nimi                                     AS nimi,
       pk."ulkoinen-id"                            AS "ulkoinen-id",
       pk.luotu                                    AS luotu,
       pk.muokattu                                 AS muokattu,
       pk."urakka-id"                              AS "urakka-id",
       pk.tyomenetelma                             AS tyomenetelma,
       pk.alkupvm                                  AS alkupvm,
       pk.loppupvm                                 AS loppupvm,
       pk.tilattupvm                               AS tilattupvm,
       pk."paikkauskohteen-tila"                   AS "paikkauskohteen-tila",
       pk."suunniteltu-hinta"                      AS "suunniteltu-hinta",
       pk."suunniteltu-maara"                      AS "suunniteltu-maara",
       pk."toteutunut-hinta"                       AS "toteutunut-hinta",
       pk.valmistumispvm                           AS valmistumispvm,
       pk.yksikko                                  AS yksikko,
       pk.lisatiedot                               AS lisatiedot,
       pk."pot?"                                   AS "pot?",
       pk.takuuaika                                AS takuuaika,
       pk."tiemerkintaa-tuhoutunut?"               AS "tiemerkintaa-tuhoutunut?",
       o.nimi                                      AS urakoitsija,
       (pk.tierekisteriosoite_laajennettu).tie     AS tie,
       (pk.tierekisteriosoite_laajennettu).aosa    AS aosa,
       (pk.tierekisteriosoite_laajennettu).aet     AS aet,
       (pk.tierekisteriosoite_laajennettu).losa    AS losa,
       (pk.tierekisteriosoite_laajennettu).let     AS let,
       (pk.tierekisteriosoite_laajennettu).ajorata AS ajorata,
       CASE
           WHEN (pk.tierekisteriosoite_laajennettu).tie IS NOT NULL THEN
               (SELECT *
                FROM tierekisteriosoitteelle_viiva(
                        CAST((pk.tierekisteriosoite_laajennettu).tie AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aosa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aet AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).losa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).let AS INTEGER)))
           ELSE NULL
           END                                     AS geometria,
       MIN(p.alkuaika)                             AS "toteutus-alkuaika",
       MAX(p.loppuaika)                            AS "toteutus-loppuaika",
       SUM(p.massamenekki)                         AS "toteutunut-maara", -- TODO: Katsotaan oikea kenttä tähän myöhemmin
       COUNT(p.id)                                 AS "toteumien-maara",
       pk.tiemerkintapvm                           AS tiemerkintapvm
FROM paikkauskohde pk
     LEFT JOIN paikkaus p ON p."paikkauskohde-id" = pk.id AND p.poistettu = false,
     urakka u,
     organisaatio o
WHERE pk."urakka-id" = :urakka-id
  AND pk.poistettu = false
  -- paikkauskohteen-tila kentällä määritellään, näkyykö paikkauskohde paikkauskohdelistassa
  AND pk."paikkauskohteen-tila" IS NOT NULL
  AND ((:tilat)::TEXT IS NULL OR pk."paikkauskohteen-tila"::TEXT IN (:tilat))
  AND ((:alkupvm :: DATE IS NULL AND :loppupvm :: DATE IS NULL)
    OR pk.alkupvm BETWEEN :alkupvm AND :loppupvm)
  AND ((:tyomenetelmat)::TEXT IS NULL OR pk.tyomenetelma IN (:tyomenetelmat))
  AND u.id = pk."urakka-id"
  AND o.id = u.urakoitsija
  -- Valittujen elykeskusten perusteella tehtävä geometriarajaus
  AND ((:elyt)::TEXT IS NULL OR (st_intersects(ST_UNION(ARRAY(select e.alue FROM organisaatio e WHERE e.id in (:elyt))),
                                               (SELECT *
                                                FROM tierekisteriosoitteelle_viiva(
                                                        CAST((pk.tierekisteriosoite_laajennettu).tie AS INTEGER),
                                                        CAST((pk.tierekisteriosoite_laajennettu).aosa AS INTEGER),
                                                        CAST((pk.tierekisteriosoite_laajennettu).aet AS INTEGER),
                                                        CAST((pk.tierekisteriosoite_laajennettu).losa AS INTEGER),
                                                        CAST((pk.tierekisteriosoite_laajennettu).let AS INTEGER)))))
    )
GROUP BY pk.id, o.nimi
ORDER BY coalesce(pk.muokattu, pk.luotu) DESC;

--name: paikkauskohteet-urakan-alueella
-- Haetaan alueurakan (hoito,teiden-hoito) alueella olevat paikkauskohteet
WITH alueurakka AS (
    select id, alue FROM urakka WHERE id = :urakka-id
)
SELECT pk.id                                       AS id,
       pk.nimi                                     AS nimi,
       pk."ulkoinen-id"                            AS "ulkoinen-id",
       pk.luotu                                    AS luotu,
       pk.muokattu                                 AS muokattu,
       pk."urakka-id"                              AS "urakka-id",
       pk.tyomenetelma                             AS tyomenetelma,
       pk.alkupvm                                  AS alkupvm,
       pk.loppupvm                                 AS loppupvm,
       pk.tilattupvm                               AS tilattupvm,
       pk."paikkauskohteen-tila"                   AS "paikkauskohteen-tila",
       pk."suunniteltu-hinta"                      AS "suunniteltu-hinta",
       pk."suunniteltu-maara"                      AS "suunniteltu-maara",
       pk."toteutunut-hinta"                       AS "toteutunut-hinta",
       pk.valmistumispvm                           AS valmistumispvm,
       pk.yksikko                                  AS yksikko,
       pk.lisatiedot                               AS lisatiedot,
       pk."pot?"                                   AS "pot?",
       o.nimi                                      AS urakoitsija,
       (pk.tierekisteriosoite_laajennettu).tie     AS tie,
       (pk.tierekisteriosoite_laajennettu).aosa    AS aosa,
       (pk.tierekisteriosoite_laajennettu).aet     AS aet,
       (pk.tierekisteriosoite_laajennettu).losa    AS losa,
       (pk.tierekisteriosoite_laajennettu).let     AS let,
       (pk.tierekisteriosoite_laajennettu).ajorata AS ajorata,
       CASE
           WHEN (pk.tierekisteriosoite_laajennettu).tie IS NOT NULL THEN
               (SELECT *
                FROM tierekisteriosoitteelle_viiva(
                        CAST((pk.tierekisteriosoite_laajennettu).tie AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aosa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aet AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).losa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).let AS INTEGER)))
           ELSE NULL
           END                                     AS geometria
FROM paikkauskohde pk,
     urakka u,
     organisaatio o,
     alueurakka a
WHERE st_intersects(a.alue, (SELECT *
                             FROM tierekisteriosoitteelle_viiva(
                                     CAST((pk.tierekisteriosoite_laajennettu).tie AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).aosa AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).aet AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).losa AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).let AS INTEGER))))
  AND pk.poistettu = false
  -- paikkauskohteen-tila kentällä määritellään, näkyykö paikkauskohde paikkauskohdelistassa
  AND pk."paikkauskohteen-tila" IS NOT NULL
  AND ((:tilat)::TEXT IS NULL OR pk."paikkauskohteen-tila"::TEXT IN (:tilat))
  AND ((:alkupvm :: DATE IS NULL AND :loppupvm :: DATE IS NULL)
    OR pk.alkupvm BETWEEN :alkupvm AND :loppupvm)
  AND ((:tyomenetelmat)::TEXT IS NULL OR pk.tyomenetelma IN (:tyomenetelmat))
  AND u.id = pk."urakka-id"
  AND o.id = u.urakoitsija
ORDER BY coalesce(pk.muokattu,  pk.luotu) DESC;


--name: paikkauskohteet-elyn-alueella
-- Haetaan elyn alueen (geometrian) sisältämät paikkauskohteet
SELECT pk.id                                       AS id,
       pk.nimi                                     AS nimi,
       pk."ulkoinen-id"                            AS "ulkoinen-id",
       pk.luotu                                    AS luotu,
       pk.muokattu                                 AS muokattu,
       pk."urakka-id"                              AS "urakka-id",
       pk.tyomenetelma                             AS tyomenetelma,
       pk.alkupvm                                  AS alkupvm,
       pk.loppupvm                                 AS loppupvm,
       pk.tilattupvm                               AS tilattupvm,
       pk."paikkauskohteen-tila"                   AS "paikkauskohteen-tila",
       pk."suunniteltu-hinta"                      AS "suunniteltu-hinta",
       pk."suunniteltu-maara"                      AS "suunniteltu-maara",
       pk."toteutunut-hinta"                       AS "toteutunut-hinta",
       pk.valmistumispvm                           AS valmistumispvm,
       pk.yksikko                                  AS yksikko,
       pk.lisatiedot                               AS lisatiedot,
       pk."pot?"                                   AS "pot?",
       urakoitsija.nimi                            AS urakoitsija,
       (pk.tierekisteriosoite_laajennettu).tie     AS tie,
       (pk.tierekisteriosoite_laajennettu).aosa    AS aosa,
       (pk.tierekisteriosoite_laajennettu).aet     AS aet,
       (pk.tierekisteriosoite_laajennettu).losa    AS losa,
       (pk.tierekisteriosoite_laajennettu).let     AS let,
       (pk.tierekisteriosoite_laajennettu).ajorata AS ajorata,
       CASE
           WHEN (pk.tierekisteriosoite_laajennettu).tie IS NOT NULL THEN
               (SELECT *
                FROM tierekisteriosoitteelle_viiva(
                        CAST((pk.tierekisteriosoite_laajennettu).tie AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aosa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aet AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).losa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).let AS INTEGER)))
           ELSE NULL
           END                                     AS geometria
FROM paikkauskohde pk,
     urakka u,
     organisaatio o,
     organisaatio urakoitsija,
     urakka pk_urakka
WHERE st_intersects(o.alue, (SELECT *
                             FROM tierekisteriosoitteelle_viiva(
                                     CAST((pk.tierekisteriosoite_laajennettu).tie AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).aosa AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).aet AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).losa AS INTEGER),
                                     CAST((pk.tierekisteriosoite_laajennettu).let AS INTEGER))))
  AND u.id = :urakka-id
  AND pk.poistettu = false
  -- paikkauskohteen-tila kentällä määritellään, näkyykö paikkauskohde paikkauskohdelistassa
  AND pk."paikkauskohteen-tila" IS NOT NULL
  AND ((:tilat)::TEXT IS NULL OR pk."paikkauskohteen-tila"::TEXT IN (:tilat))
  AND ((:alkupvm :: DATE IS NULL AND :loppupvm :: DATE IS NULL)
    OR pk.alkupvm BETWEEN :alkupvm AND :loppupvm)
  AND ((:tyomenetelmat)::TEXT IS NULL OR pk.tyomenetelma IN (:tyomenetelmat))
  AND o.id = u.hallintayksikko -- Haetaan tiemerkintäurakan hallintoyksikön alueen perusteella.
  AND pk_urakka.id = pk."urakka-id" -- Lisäksi organisaatioista tarvitaan urakoitsija, joten haetaan ensin urakka
  AND pk_urakka.urakoitsija = urakoitsija.id
ORDER BY coalesce(pk.muokattu,  pk.luotu) DESC;

--name:paivita-paikkauskohde!
UPDATE paikkauskohde
SET "ulkoinen-id"                  = :ulkoinen-id,
    nimi                           = :nimi,
    poistettu                      = :poistettu,
    "muokkaaja-id"                 = :muokkaaja-id,
    muokattu                       = :muokattu,
    "yhalahetyksen-tila"           = :yhalahetyksen-tila,
    virhe                          = :virhe,
    tarkistettu                    = :tarkistettu,
    "tarkistaja-id"                = :tarkistaja-id,
    "ilmoitettu-virhe"             = :ilmoitettu-virhe,
    "ulkoinen-id"                  = :ulkoinen-id,
    alkupvm                        = :alkupvm::TIMESTAMP,
    loppupvm                       = :loppupvm::TIMESTAMP,
    tyomenetelma                   = :tyomenetelma,
    tierekisteriosoite_laajennettu = ROW (:tie, :aosa, :aet, :losa, :let, :ajorata, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu,
    "paikkauskohteen-tila"         = :paikkauskohteen-tila::paikkauskohteen_tila,
    "suunniteltu-hinta"            = :suunniteltu-hinta,
    "suunniteltu-maara"            = :suunniteltu-maara,
    "toteutunut-hinta"             = :toteutunut-hinta,
    valmistumispvm                 = :valmistumispvm,
    yksikko                        = :yksikko,
    lisatiedot                     = :lisatiedot,
    "pot?"                         = :pot?,
    takuuaika                      = :takuuaika,
    "tiemerkintaa-tuhoutunut?"     = :tiemerkintaa-tuhoutunut?,
    tiemerkintapvm                 = :tiemerkintapvm
WHERE id = :id
RETURNING id;

--name: luo-uusi-paikkauskohde<!
INSERT INTO paikkauskohde ("luoja-id", "ulkoinen-id", nimi, poistettu, luotu,
                           "yhalahetyksen-tila", virhe, "ulkoinen-id", alkupvm, loppupvm, tyomenetelma,
                           tierekisteriosoite_laajennettu, "paikkauskohteen-tila", "urakka-id",
                           "suunniteltu-hinta", "suunniteltu-maara", yksikko, lisatiedot)
VALUES (:luoja-id,
        :ulkoinen-id,
        :nimi,
        false,
        :luotu,
        :yhalahetyksen-tila,
        :virhe,
        :ulkoinen-id,
        :alkupvm::TIMESTAMP,
        :loppupvm::TIMESTAMP,
        :tyomenetelma::tyomenetelma,
        ROW (:tie, :aosa, :aet, :losa, :let, :ajorata, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu,
        :paikkauskohteen-tila::paikkauskohteen_tila,
        :urakka-id,
        :suunniteltu-hinta,
        :suunniteltu-maara,
        :yksikko,
        :lisatiedot)
RETURNING id;

--name: poista-paikkauskohde!
UPDATE paikkauskohde
SET poistettu = true
WHERE id = :id;

--name: hae-paikkauskohde
-- Haetaan yksittäinen paikkauskohde
SELECT pk.id                                       AS id,
       pk.nimi                                     AS nimi,
       pk."ulkoinen-id"                            AS "ulkoinen-id",
       pk.luotu                                    AS luotu,
       pk.muokattu                                 AS muokattu,
       pk."urakka-id"                              AS "urakka-id",
       pk.tyomenetelma                             AS tyomenetelma,
       pk.alkupvm                                  AS alkupvm,
       pk.loppupvm                                 AS loppupvm,
       pk.tilattupvm                               AS tilattupvm,
       pk."paikkauskohteen-tila"                   AS "paikkauskohteen-tila",
       pk."suunniteltu-hinta"                      AS "suunniteltu-hinta",
       pk."suunniteltu-maara"                      AS "suunniteltu-maara",
       pk.yksikko                                  AS yksikko,
       pk.lisatiedot                               AS lisatiedot,
       pk."pot?"                                   AS "pot?",
       o.nimi                                      AS urakoitsija,
       (pk.tierekisteriosoite_laajennettu).tie     AS tie,
       (pk.tierekisteriosoite_laajennettu).aosa    AS aosa,
       (pk.tierekisteriosoite_laajennettu).aet     AS aet,
       (pk.tierekisteriosoite_laajennettu).losa    AS losa,
       (pk.tierekisteriosoite_laajennettu).let     AS let,
       (pk.tierekisteriosoite_laajennettu).ajorata AS ajorata,
       CASE
           WHEN (pk.tierekisteriosoite_laajennettu).tie IS NOT NULL THEN
               (SELECT *
                FROM tierekisteriosoitteelle_viiva(
                        CAST((pk.tierekisteriosoite_laajennettu).tie AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aosa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).aet AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).losa AS INTEGER),
                        CAST((pk.tierekisteriosoite_laajennettu).let AS INTEGER)))
           ELSE NULL
           END                                     AS geometria,
       pk.tiemerkintapvm                           AS tiemerkintapvm
FROM paikkauskohde pk,
     urakka u,
     organisaatio o
WHERE pk.poistettu = false
  -- paikkauskohteen-tila kentällä määritellään, näkyykö paikkauskohde paikkauskohdelistassa
  AND pk."paikkauskohteen-tila" IS NOT NULL
  AND pk.id = :id
  AND pk."urakka-id" = :urakka-id
  AND u.id = pk."urakka-id"
  AND o.id = u.urakoitsija;

-- name: hae-urakan-paikkauskohteet-ja-paikkaukset
select pk.id                                       AS id,
       pk.nimi                                     AS nimi,
       pk."ulkoinen-id"                            AS "ulkoinen-id",
       pk.luotu                                    AS luotu,
       pk.muokattu                                 AS muokattu,
       pk."urakka-id"                              AS "urakka-id",
       pk.tyomenetelma                             AS tyomenetelma,
       pk.alkupvm                                  AS alkupvm,
       pk.loppupvm                                 AS loppupvm,
       pk.tilattupvm                               AS tilattupvm,
       pk."paikkauskohteen-tila"                   AS "paikkauskohteen-tila",
       pk."suunniteltu-hinta"                      AS "suunniteltu-hinta",
       pk."suunniteltu-maara"                      AS "suunniteltu-maara",
       pk.yksikko                                  AS yksikko,
       pk.lisatiedot                               AS lisatiedot,
       (pk.tierekisteriosoite_laajennettu) .tie    AS tie,
       --ROW(p.alkuaika, p.loppuaika) as paikkaus
       jsonb_agg(row_to_json(row(p.id, p.alkuaika, p.loppuaika,
           (p.tierekisteriosoite).tie,
           (p.tierekisteriosoite).aosa,
           (p.tierekisteriosoite).aet,
           (p.tierekisteriosoite).losa,
           (p.tierekisteriosoite).let,
           p.tyomenetelma,
           p.massatyyppi,
           p.leveys,
           p.raekoko,
           p.kuulamylly,
           p.sijainti,
           p.massamaara,
           p."pinta-ala",
           p.lahde,
           pt.id, pt.ajorata, pt.reunat, pt.ajourat, pt.ajouravalit, pt.keskisaumat

           ))) as paikkaukset
FROM paikkauskohde pk
LEFT JOIN paikkaus p ON p."paikkauskohde-id" = pk.id AND p.poistettu = FALSE
    LEFT JOIN paikkauksen_tienkohta pt ON pt."paikkaus-id" = p.id
WHERE pk.poistettu = FALSE
  AND pk."paikkauskohteen-tila" in ('valmis', 'tilattu')
  AND pk."pot?" = FALSE
  AND pk."urakka-id" = :urakka-id
  AND (:alkuaika::DATE IS NULL OR pk.tilattupvm >= :alkuaika::DATE)
  AND (:loppuaika::DATE IS NULL OR pk.tilattupvm <= :loppuaika::DATE)
  AND ((:tyomenetelmat)::TEXT IS NULL OR pk.tyomenetelma IN (:tyomenetelmat))
-- Ehto  - jos tie on annettu
  AND (:tie::TEXT IS NULL OR (p.tierekisteriosoite).tie = :tie)
-- Ehto  - jos alkuosa on annettu
  AND (:aosa::TEXT IS NULL OR (p.tierekisteriosoite).aosa >= :aosa)
-- Ehto  - jos alkuetäisyys on annettu - hyödynnetään sitä vain jos alkuosa on annettu
  AND ((:aet::TEXT IS NULL AND :aosa::TEXT IS NULL OR  (p.tierekisteriosoite).aet >= :aet))
-- Ehto  - jos loppuosa on annettu
  AND (:losa::TEXT IS NULL OR (p.tierekisteriosoite).losa <= :losa)
-- Ehto  - jos loppuetäisyys on annettu - hyödynnetään sitä vian jos loppuosa on annettu
  AND ((:let::TEXT IS NULL AND :losa::TEXT IS NULL) OR (p.tierekisteriosoite).let <= :let)
GROUP BY pk.id;
