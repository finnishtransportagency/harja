-- name: kysely

DROP VIEW IF EXISTS yh_vemtr_toteutuneet;
CREATE OR REPLACE TEMPORARY VIEW yh_vemtr_toteutuneet AS
     SELECT MIN(tpk4.nimi) as nimi,
     0 as jarjestys,
     0 as "suunniteltu",
     0::smallint as "hoitokauden-alkuvuosi",
     tpk4.suunnitteluyksikko,
     tpk4.yksikko,
     tpk4.id as toimenpidekoodi,
     0 as urakka,
     '' as toimenpide,
     ROUND(SUM(tt.maara), 2) as toteuma,
     ROUND(SUM(tm.maara), 2) as "toteutunut-materiaalimaara"
          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
  toimenpidekoodi tpk4, urakka u
  WHERE u.tyyppi = 'hoito' and (not u.poistettu) and t.urakka = u.id and tpk4.id = tt.toimenpidekoodi AND (not tpk4.poistettu) AND t.alkanut > '2020-11-01' GROUP BY tpk4.id;
  
DROP VIEW IF EXISTS yh_vemtr_suunnitellut;
CREATE OR REPLACE TEMPORARY VIEW yh_vemtr_suunnitellut AS
  select LEFT(tpk4.nimi, 10) as nimi,
         0 as jarjestys,
	 SUM(tyo.maara) as "suunniteltu",
         0::smallint as "hoitokauden-alkuvuosi", --  xxx as "hoitokauden-alkuvuosi", -- XXX miten hanskata? pitääkö kyselystä tehdä semmoinen että se partitioi hoitokausittain rivit? onko meillä joku taulu, tai gen_hoitokausi sproc joka antaa mahdolliset hoitokaudet?
	 tpk4.suunnitteluyksikko,
	 tpk4.suunnitteluyksikko as yksikko,
	 tpk4.id as toimenpidekoodi,
	 0 as urakka, -- u.id as urakka,
	 '' as toimenpide, -- mistä tpi (josta tpi.nimi as toimenpide)?
	 0 as "toteuma", 0 as "toteutunut-materiaalimaara"
FROM yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava,
  urakka u
  WHERE u.id = tyo.urakka AND u.tyyppi = 'hoito' GROUP BY tpk4.id;

DROP VIEW IF EXISTS mhu_vemtr;
CREATE OR REPLACE TEMPORARY VIEW mhu_vemtr AS
  WITH urakat AS (SELECT u.id
                FROM urakka u
                WHERE ('2020-01-01' between u.alkupvm and u.loppupvm
                  or '2020-12-31' between u.alkupvm and u.loppupvm)
  ),
     toteumat as (SELECT tt.maara,
                         tt.toimenpidekoodi,
                         tt.poistettu,
                         tt.urakka_id
                  FROM toteuma t
                         join toteuma_tehtava tt on tt.toteuma = t.id and tt.poistettu = false
                              and tt.urakka_id in (SELECT id FROM urakat)
                  WHERE t.poistettu is not true)
SELECT tpk.nimi            as "nimi",
       tpk.jarjestys       as "jarjestys",
       sum(ut.maara)       as "suunniteltu", -- ut = urakka_tehtavamaara
       ut."hoitokauden-alkuvuosi"::smallint as "hoitokauden-alkuvuosi",
       tpk.suunnitteluyksikko as "suunnitteluyksikko",
       tpk.yksikko         as "yksikko",
       tpk.id              as "toimenpidekoodi",
       ut.urakka as "urakka",
       tpi.nimi             as "toimenpide",
       sum(toteumat.maara) as "toteuma",
       0 as "toteutunut-materiaalimaara"
FROM urakka_tehtavamaara ut
       join toimenpidekoodi tpk on ut.tehtava = tpk.id
       join toimenpideinstanssi tpi on tpi.toimenpide = tpk.emo and tpi.urakka = ut.urakka
       left outer join toteumat on toteumat.toimenpidekoodi = ut.tehtava and toteumat.urakka_id = ut.urakka
       join tehtavaryhma tr on tpk.tehtavaryhma = tr.id
WHERE ut.poistettu is not true
  and ut."hoitokauden-alkuvuosi" in (2020)
  and ut.urakka in (SELECT id FROM urakat)
group by tpk.id, tpk.nimi, tpk.yksikko, ut."hoitokauden-alkuvuosi", tpk.jarjestys, tpi.nimi, tpk.suunnitteluyksikko, ut.urakka;

SELECT * FROM mhu_vemtr UNION SELECT * FROM yh_vemtr_suunnitellut UNION SELECT * FROM yh_vemtr_toteutuneet;

-- mistä hoitokauden alkuvuosi yh-caseen?
