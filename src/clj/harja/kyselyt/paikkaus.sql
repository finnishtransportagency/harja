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
 WHERE "ulkoinen-id" = :ulkoinen-id