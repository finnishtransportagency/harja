-- name: mhu-paikkausten-kustannukset-tehtavaryhmittain
-- Paikkausten kustannukset saadaan muutamasta kovakoodatusta tehtäväryhmästä
SELECT tr.id,
       tr.nimi       AS tehtavaryhma,
       SUM(kk.summa) AS summa

  FROM tehtavaryhma tr
           JOIN kulu_kohdistus kk ON tr.id = kk.tehtavaryhma
           JOIN kulu k ON kk.kulu = k.id AND k.urakka = :urakkaid AND k.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
 WHERE tr.id IN (SELECT id
                   FROM tehtavaryhma
                  WHERE nimi IN ('H - Siltapäällysteet',
                                 'Y1 - Kuumapäällyste',
                                 'Y2 - Kylmäpäällyste',
                                 'Y3 - KT-Valu',
                                 'Y4 - Käsipaikkaus pikapaikkausmassalla',
                                 'Y5 - Puhallus-SIP',
                                 'Y6 - Saumojen juottaminen bitumilla',
                                 'Y7 - Valu',
                                 'Y8 - Päällysteiden paikkaus, muut työt'))
 GROUP BY tr.id, tr.jarjestys
 ORDER BY tr.jarjestys;

-- name: mhu-maarat-tehtavittain
-- Paikkausten määrät saadaan muutamasta kovakoodatusta tehtävästä
SELECT x.tehtava                AS tehtava,
       x.yksikko                AS yksikko,
       SUM(x.toteutunut_maara)  AS toteutunut,
       SUM(x.suunniteltu_maara) AS suunniteltu
  FROM (WITH valitut_tehtavat AS (SELECT t.id
                                    FROM tehtavaryhma tr
                                             JOIN tehtava t ON tr.id = t.tehtavaryhma
                                   WHERE tr.nimi IN ('H - Siltapäällysteet',
                                                     'Y1 - Kuumapäällyste',
                                                     'Y2 - Kylmäpäällyste',
                                                     'Y3 - KT-Valu',
                                                     'Y4 - Käsipaikkaus pikapaikkausmassalla',
                                                     'Y5 - Puhallus-SIP',
                                                     'Y6 - Saumojen juottaminen bitumilla',
                                                     'Y7 - Valu',
                                                     'Y8 - Päällysteiden paikkaus, muut työt'))
      SELECT teh.id,
             teh.nimi      AS tehtava,
             teh.yksikko   AS yksikko,
             SUM(tt.maara) AS toteutunut_maara,
             NULL          AS suunniteltu_maara,
             teh.jarjestys AS jarjestys
        FROM toteuma t
                 JOIN toteuma_tehtava tt ON t.id = tt.toteuma
                 JOIN tehtava teh ON tt.toimenpidekoodi = teh.id,
             valitut_tehtavat vt
       WHERE t.urakka = :urakkaid
         AND t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE
         AND t.poistettu IS FALSE
         AND teh.id IN (vt.id)
       GROUP BY teh.id, teh.nimi, teh.jarjestys

       UNION
-- Haetaan suunnitellut summat erikseen

      SELECT teh.id,
             teh.nimi      AS tehtava,
             teh.yksikko   AS yksikko,
             NULL          AS toteutunut_maara,
             ut.maara      AS suunniteltu_maara,
             teh.jarjestys AS jarjestys
        FROM urakka_tehtavamaara ut
                 JOIN tehtava teh ON ut.tehtava = teh.id,
             valitut_tehtavat vt
       WHERE ut.urakka = :urakkaid
         AND ut."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
         AND ut.poistettu IS FALSE
         AND teh.id IN (vt.id)
       GROUP BY teh.id, teh.nimi, teh.jarjestys, ut.maara
       ORDER BY jarjestys) x
 GROUP BY x.tehtava, x.yksikko;


-- name: mhu-paikkausten-suunnitellut-kustannukset
SELECT COALESCE(SUM(kt.summa_indeksikorjattu), SUM(kt.summa), 0) AS summa
  FROM kiinteahintainen_tyo kt
           JOIN sopimus s ON kt.sopimus = s.id AND s.urakka = :urakkaid
 WHERE (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
AND kt.toimenpideinstanssi = (SELECT id FROM toimenpideinstanssi tpi
                                        WHERE tpi.urakka = :urakkaid
                                          -- Päällysteiden paikkaus toimenpiteen TPI
                                          AND toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107'));

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
           JOIN paikkaus p ON pt.id = p.tyomenetelma AND p."paikkaus-tyyppi" = 'reikapaikkaus' AND p."urakka-id" = :urakkaid
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
                   END                AS "toteutunut-maara",
               pk."suunniteltu-maara" AS "suunniteltu-maara"
          FROM paikkauskohde_tyomenetelma pt
                   JOIN paikkauskohde pk ON pt.id = pk.tyomenetelma
                   LEFT JOIN paikkaus p ON pk.id = p."paikkauskohde-id"
              AND p.alkuaika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
              AND p.poistettu = FALSE
         WHERE pk."urakka-id" = :urakkaid
           AND pk.alkupvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
           AND pk.poistettu IS FALSE
         GROUP BY pt.id, pk.yksikko, pk.id

         UNION
-- Reikäpaikkaukset
        SELECT pt.nimi                   AS nimi,
               p."reikapaikkaus-yksikko" AS yksikko,
               COALESCE(SUM(p.maara), 0) AS "toteutunut-maara",
               NULL                      AS "suunniteltu-maara"
          FROM paikkauskohde_tyomenetelma pt
                   JOIN paikkaus p ON pt.id = p.tyomenetelma AND p."paikkaus-tyyppi" = 'reikapaikkaus'
              AND p."urakka-id" = :urakkaid
              AND p.alkuaika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
              AND p.poistettu = FALSE
         GROUP BY pt.id, pt.nimi, p."reikapaikkaus-yksikko") x
 GROUP BY nimi, yksikko
 ORDER BY nimi ASC;

-- name: hae-kasin-lisatyt-paikkauskustannukset
SELECT SUM(pk.summa) AS "toteutunut-hinta",
       pk.kustannustyyppi     AS nimi
  FROM paikkauskustannukset pk
 WHERE vuosi BETWEEN :alkuvuosi AND :loppuvuosi
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
 GROUP BY pk.pkluokka

 UNION

SELECT p.pkluokka,
       COALESCE(SUM(p."kustannus"), 0)  AS "toteutunut-hinta"
  FROM paikkaus p
 WHERE p."urakka-id" = :urakkaid
   AND p."paikkaus-tyyppi" = 'reikapaikkaus'
   AND p.alkuaika BETWEEN :alkupvm::DATE AND :loppupvm::DATE
   AND p.poistettu = FALSE
 GROUP BY p.pkluokka;
