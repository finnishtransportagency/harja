
-- select tpk4.emo, tpk4.nimi as tpknimi, tpi.nimi as tpinimi, tyo.id, tyo.urakka, tt.maara as "tot-maara", tyo.maara as "suunniteltu-maara", tyo.yksikko
-- v1
select tpk4.emo as emo, tpk4.nimi as tpknimi, tt.maara as "tot-maara", tm.maara as "toteutunut-materiaalimaara", 0 as "suunniteltu-maara"

          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
	 toimenpidekoodi tpk4
	 WHERE tpk4.id = tt.toimenpidekoodi
union
select tpk4.emo as emo, tpk4.nimi as tpknimi, 0 as "tot-maara", 0 as "toteutunut-materiaalimaara", tyo.maara as "suunniteltu-maara"

from yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava;

-- v2
select MIN(tpk4.emo) as emo, MIN(tpk4.nimi) as tpknimi, SUM(tt.maara) as "tot-maara", SUM(tm.maara) as "toteutunut-materiaalimaara", 0 as "suunniteltu-maara"
          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
  toimenpidekoodi tpk4 
  WHERE tpk4.id = tt.toimenpidekoodi AND t.alkanut > '2020-11-01' GROUP BY tpk4.id
union
select MIN(tpk4.emo) as emo, MIN(tpk4.nimi) as tpknimi, 0 as "tot-maara", 0 as "toteutunut-materiaalimaara", SUM(tyo.maara) as "suunniteltu-maara"
from yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava GROUP BY tpk4.id;

-- v3

SELECT MIN(tpk4.emo) as emo, MIN(LEFT(tpk4.nimi, 10)) as tpknimi, ROUND(SUM(tt.maara), 2) as "tot-maara", ROUND(SUM(tm.maara), 2) as "toteutunut-materiaalimaara", 0 as "suunniteltu-maara"
          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
  toimenpidekoodi tpk4, urakka u
  WHERE u.tyyppi = 'hoito' and t.urakka = u.id and tpk4.id = tt.toimenpidekoodi AND t.alkanut > '2020-11-01' GROUP BY tpk4.id
union
select MIN(tpk4.emo) as emo, MIN(LEFT(tpk4.nimi, 10)) as tpknimi, 0 as "tot-maara", 0 as "toteutunut-materiaalimaara", SUM(tyo.maara) as "suunniteltu-maara"
from yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava,
  urakka u
  WHERE u.id = tyo.urakka AND u.tyyppi = 'hoito' GROUP BY tpk4.id;
  
-- v4

CREATE TEMPORARY VIEW yh_vemtr_toteutuneet AS
  SELECT MIN(tpk4.emo) as emo, MIN(LEFT(tpk4.nimi, 10)) as tpknimi, ROUND(SUM(tt.maara), 2) as "tot-maara", ROUND(SUM(tm.maara), 2) as "toteutunut-materiaalimaara", 0 as "suunniteltu-maara"
          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
  toimenpidekoodi tpk4, urakka u
  WHERE u.tyyppi = 'hoito' and (not u.poistettu) and t.urakka = u.id and tpk4.id = tt.toimenpidekoodi AND (not tpk4.poistettu) AND t.alkanut > '2020-11-01' GROUP BY tpk4.id;
  
CREATE TEMPORARY VIEW yh_vemtr_suunnitellut AS
  select MIN(tpk4.emo) as emo, MIN(LEFT(tpk4.nimi, 10)) as tpknimi, 0 as "tot-maara", 0 as "toteutunut-materiaalimaara", SUM(tyo.maara) as "suunniteltu-maara"
from yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava,
  urakka u
  WHERE u.id = tyo.urakka AND u.tyyppi = 'hoito' GROUP BY tpk4.id;

CREATE TEMPORARY VIEW mhu_vemtr AS
  WITH urakat AS (select u.id
                from urakka u
                where ('2020-01-01' between u.alkupvm and u.loppupvm
                  or '2020-12-31' between u.alkupvm and u.loppupvm)
  ),
     toteumat as (select tt.maara,
                         tt.toimenpidekoodi,
                         tt.poistettu,
                         tt.urakka_id
                  from toteuma t
                         join toteuma_tehtava tt on tt.toteuma = t.id and tt.poistettu = false
                              and tt.urakka_id in (select id from urakat)
                  where t.poistettu is not true)
select tpk.nimi            as "nimi",
       tpk.jarjestys       as "jarjestys",
       sum(ut.maara)       as "suunniteltu",
       ut."hoitokauden-alkuvuosi",
       tpk.suunnitteluyksikko as "suunnitteluyksikko",
       tpk.yksikko         as "yksikko",
       tpk.id              as "toimenpidekoodi",
       ut.urakka as "urakka",
       tpi.nimi             as "toimenpide",
       sum(toteumat.maara) as "toteuma"
from urakka_tehtavamaara ut
       join toimenpidekoodi tpk on ut.tehtava = tpk.id
       join toimenpideinstanssi tpi on tpi.toimenpide = tpk.emo and tpi.urakka = ut.urakka
       left outer join toteumat on toteumat.toimenpidekoodi = ut.tehtava and toteumat.urakka_id = ut.urakka
       join tehtavaryhma tr on tpk.tehtavaryhma = tr.id
where ut.poistettu is not true
  and ut."hoitokauden-alkuvuosi" in (2020)
  and ut.urakka in (select id from urakat)
group by tpk.id, tpk.nimi, tpk.yksikko, ut."hoitokauden-alkuvuosi", tpk.jarjestys, tpi.nimi, tpk.suunnitteluyksikko, ut.urakka;

-- v5

CREATE OR REPLACE TEMPORARY VIEW yh_vemtr_toteutuneet AS
  SELECT MIN(tpk4.nimi) as nimi, 0 as jarjestys, 0 as "suunniteltu", null as "hoitokauden-alkuvuosi", tpk4.suunnitteluyksikko, tpk4.yksikko, tpk4.id as toimenpidekoodi, ROUND(SUM(tm.maara), 2) as "toteutunut-materiaalimaara", null as toimenpide, ROUND(SUM(tt.maara), 2) as "toteuma"
          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
  toimenpidekoodi tpk4, urakka u
  WHERE u.tyyppi = 'hoito' and (not u.poistettu) and t.urakka = u.id and tpk4.id = tt.toimenpidekoodi AND (not tpk4.poistettu) AND t.alkanut > '2020-11-01' GROUP BY tpk4.id;
  
CREATE OR REPLACE TEMPORARY VIEW yh_vemtr_suunnitellut AS
  select LEFT(tpk4.nimi, 10) as nimi, 0 as jarjestys, SUM(tyo.maara) as "suunniteltu",
         --  xxx as "hoitokauden-alkuvuosi", -- XXX miten hanskata? pitääkö kyselystä tehdä semmoinen että se partitioi hoitokausittain rivit? onko meillä joku taulu, tai gen_hoitokausi sproc joka antaa mahdolliset hoitokaudet?
	 tpk4.suunnitteluyksikko, tpk4.suunnitteluyksikko as yksikko, tpk4.id as toimenpidekoodi,
	 null as urakka, -- u.id as urakka,
	 null as toimenpide, -- mistä tpi (josta tpi.nimi as toimenpide)?
	 0 as "toteuma", 0 as "toteutunut-materiaalimaara"
from yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava,
  urakka u
  WHERE u.id = tyo.urakka AND u.tyyppi = 'hoito' GROUP BY tpk4.id;

CREATE OR REPLACE TEMPORARY VIEW mhu_vemtr AS
  WITH urakat AS (select u.id
                from urakka u
                where ('2020-01-01' between u.alkupvm and u.loppupvm
                  or '2020-12-31' between u.alkupvm and u.loppupvm)
  ),
     toteumat as (select tt.maara,
                         tt.toimenpidekoodi,
                         tt.poistettu,
                         tt.urakka_id
                  from toteuma t
                         join toteuma_tehtava tt on tt.toteuma = t.id and tt.poistettu = false
                              and tt.urakka_id in (select id from urakat)
                  where t.poistettu is not true)
select tpk.nimi            as "nimi",
       tpk.jarjestys       as "jarjestys",
       sum(ut.maara)       as "suunniteltu", -- ut = urakka_tehtavamaara
       ut."hoitokauden-alkuvuosi",
       tpk.suunnitteluyksikko as "suunnitteluyksikko",
       tpk.yksikko         as "yksikko",
       tpk.id              as "toimenpidekoodi",
       ut.urakka as "urakka",
       tpi.nimi             as "toimenpide",
       sum(toteumat.maara) as "toteuma"
from urakka_tehtavamaara ut
       join toimenpidekoodi tpk on ut.tehtava = tpk.id
       join toimenpideinstanssi tpi on tpi.toimenpide = tpk.emo and tpi.urakka = ut.urakka
       left outer join toteumat on toteumat.toimenpidekoodi = ut.tehtava and toteumat.urakka_id = ut.urakka
       join tehtavaryhma tr on tpk.tehtavaryhma = tr.id
where ut.poistettu is not true
  and ut."hoitokauden-alkuvuosi" in (2020)
  and ut.urakka in (select id from urakat)
group by tpk.id, tpk.nimi, tpk.yksikko, ut."hoitokauden-alkuvuosi", tpk.jarjestys, tpi.nimi, tpk.suunnitteluyksikko, ut.urakka;

select * from mhu_vemtr union select * from yh_vemtr_suunnitellut union select * from yh_vemtr_toteutuneet;

-- 1. emo tpknimi tot-maara toteutunut-materiaalimaara suunniteltu-maara
-- 2. nimi jarjestys suunniteltu ut.hoitokauden-alkluvuosi suunnitteluyksikko yksikko toimenpidekoodi urakka toimenpdie toteuma

-- mistä hoitokauden alkuvuosi yh-caseen?
