-- name: hae-kustannukset-tyomenetelmittain
SELECT x.nimi                     AS nimi,
       SUM(x."toteutunut-hinta")  AS "toteutunut-hinta",
       SUM(x."suunniteltu-hinta") AS "suunniteltu-hinta"
  FROM (SELECT pt.id,
               pt.nimi,
               SUM(pk."toteutunut-hinta")  AS "toteutunut-hinta",
               SUM(pk."suunniteltu-hinta") AS "suunniteltu-hinta"
          FROM paikkauskohde_tyomenetelma pt
                   JOIN paikkauskohde pk ON pt.id = pk.tyomenetelma
         WHERE pk."urakka-id" = :urakkaid
           AND pk.alkupvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
           AND pk."paikkauskohteen-tila" = 'valmis'
           AND pk.poistettu = FALSE
         GROUP BY pt.id, pk."paikkauskohteen-tila"

         UNION

        SELECT pt.id,
               pt.nimi,
               NULL                        AS "toteutunut-hinta",
               SUM(pk."suunniteltu-hinta") AS "suunniteltu-hinta"
          FROM paikkauskohde_tyomenetelma pt
                   JOIN paikkauskohde pk ON pt.id = pk.tyomenetelma
         WHERE pk."urakka-id" = :urakkaid
           AND pk.alkupvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
           AND pk."paikkauskohteen-tila" = 'tilattu'
           AND pk.poistettu = FALSE
         GROUP BY pt.id, pk."paikkauskohteen-tila"

         UNION
-- Reikäpaikkaukset
        SELECT pt.id                         AS id,
               pt.nimi,
               COALESCE(SUM(p.kustannus), 0) AS "toteutunut-hinta",
               NULL                          AS "suunniteltu-hinta"
          FROM paikkauskohde_tyomenetelma pt
                   LEFT JOIN paikkaus p ON pt.id = p.tyomenetelma
                                               AND p."paikkaus-tyyppi" = 'reikapaikkaus'
                                               AND p."urakka-id" = :urakkaid
              AND p.alkuaika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
              AND p.poistettu = FALSE
         GROUP BY pt.id, pt.nimi) x
 GROUP BY nimi
ORDER BY nimi ASC;

-- name: hae-reikapaikkauskustannukset-tyomenetelmittain
SELECT pt.id                         AS id,
       pt.nimi,
       p."reikapaikkaus-yksikko"     AS yksikko,
       COALESCE(SUM(p.kustannus), 0) AS "toteutunut-hinta",
       COALESCE(SUM(p.maara), 0)     AS "toteutunut-maara"
  FROM paikkauskohde_tyomenetelma pt
           LEFT JOIN paikkaus p ON pt.id = p.tyomenetelma AND p."urakka-id" = :urakkaid
      AND p.alkuaika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
      AND p.poistettu = FALSE
 GROUP BY pt.id, pt.nimi, p."reikapaikkaus-yksikko"
ORDER BY pt.nimi ASC;

-- name: hae-maarat-tyomenetelmittain
SELECT x.nimi                     AS nimi,
       x.yksikko,
       SUM(x."toteutunut-maara")  AS "toteutunut-maara",
       SUM(x."suunniteltu-maara") AS "suunniteltu-maara"
  FROM (SELECT pt.nimi,
               pk.yksikko,
               CASE
                   WHEN pk.yksikko = 'jm' THEN COALESCE(SUM(p.juoksumetri), 0)
                   WHEN pk.yksikko = 'kpl' THEN COALESCE(SUM(p.kpl), 0)
                   WHEN pk.yksikko = 't' THEN COALESCE(SUM(p.massamaara), 0)
                   WHEN pk.yksikko = 'm2' THEN COALESCE(SUM(p."pinta-ala"), 0)
                   ELSE 0
                   END                     AS "toteutunut-maara",
               SUM(pk."suunniteltu-maara") AS "suunniteltu-maara"
          FROM paikkauskohde_tyomenetelma pt
                   JOIN paikkauskohde pk ON pt.id = pk.tyomenetelma
                   LEFT JOIN paikkaus p ON pk.id = p."paikkauskohde-id"
              AND p.alkuaika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
              AND p.poistettu = FALSE
         WHERE pk."urakka-id" = :urakkaid
           AND pk.alkupvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
           AND pk."paikkauskohteen-tila" = 'valmis'
           AND pk.poistettu IS FALSE
         GROUP BY pt.id, pk."paikkauskohteen-tila", pk.yksikko

         UNION

        SELECT pt.nimi                     AS nimi,
               pk.yksikko                  AS yksikko,
               0                           AS "toteutunut-maara",
               SUM(pk."suunniteltu-maara") AS "suunniteltu-maara"
          FROM paikkauskohde_tyomenetelma pt
                   JOIN paikkauskohde pk ON pt.id = pk.tyomenetelma
         WHERE pk."urakka-id" = :urakkaid
           AND pk.alkupvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
           AND pk."paikkauskohteen-tila" = 'tilattu'
           AND pk.poistettu IS FALSE
         GROUP BY pt.id, pk."paikkauskohteen-tila", pk.yksikko

         UNION
-- Reikäpaikkaukset
        SELECT pt.nimi                   AS nimi,
               p."reikapaikkaus-yksikko" AS yksikko,
               COALESCE(SUM(p.maara), 0) AS "toteutunut-maara",
               NULL                      AS "suunniteltu-maara"
          FROM paikkauskohde_tyomenetelma pt
                   LEFT JOIN paikkaus p ON pt.id = p.tyomenetelma AND p."urakka-id" = :urakkaid
              AND p.alkuaika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
              AND p.poistettu = FALSE
         GROUP BY pt.id, pt.nimi, p."reikapaikkaus-yksikko") x
 GROUP BY nimi, yksikko
ORDER BY nimi ASC;

-- name: hae-kasin-lisatyt-paikkauskustannukset
SELECT SUM(pk.summa) AS "toteutunut-hinta",
       pk.kustannustyyppi     AS nimi
  FROM paikkauskustannukset pk
 WHERE vuosi = :vuosi
   AND pk.urakka = :urakkaid
 GROUP BY pk.kustannustyyppi;

-- name: hae-kustannukset-pkluokittain
SELECT pk.pkluokka,
       COALESCE(SUM(pk."toteutunut-hinta"), 0)  AS "toteutunut-hinta"
  FROM paikkauskohde pk
 WHERE pk."urakka-id" = :urakkaid
   AND pk.alkupvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
   AND pk."paikkauskohteen-tila" = 'valmis'
   AND pk.poistettu = FALSE
 GROUP BY pk.pkluokka;
