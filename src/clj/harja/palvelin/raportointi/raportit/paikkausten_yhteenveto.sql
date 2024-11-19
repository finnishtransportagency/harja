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
SELECT x.tehtava AS tehtava,
       x.yksikko AS yksikko,
       SUM(x.toteutunut_maara) AS toteutunut,
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
