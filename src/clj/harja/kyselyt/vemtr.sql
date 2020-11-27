-- name: kysely

-- 

-- mh-urakoiden toteuma_tehtava -taulun linkitystietoja vastaa vanhoissa
-- yh-urakoissa toimenpidekoodi: taso = 3 -> toimenpiteet, taso = 4 -> tehtävät

DROP VIEW IF EXISTS yh_vemtr_toteutuneet;
CREATE OR REPLACE TEMPORARY VIEW yh_vemtr_toteutuneet AS
     SELECT tpk4.nimi as nimi, -- todo: tulee random nimi palautuneiden joukosta, elleivät satu olemaan samoja?
     NULL ::integer as jarjestys,
     NULL ::numeric as "suunniteltu",
     -- NULL ::smallint "hoitokauden-alkuvuosi",
     (select extract(year from alkupvm) from urakka where id = tpi.urakka) as "hoitokauden-alkuvuosi",
     tpk4.suunnitteluyksikko,
     tpk4.yksikko,
     tpk4.id as toimenpidekoodi,
     tpi.urakka as urakka,
     tpi.nimi as toimenpide,
     ROUND(SUM(tt.maara), 2) as toteuma,
     ROUND(SUM(tm.maara), 2) as "toteutunut-materiaalimaara", -- usein tyhjä
     'yh-toteutuneet' as rivityyppi

          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.poistettu = FALSE,
  toimenpidekoodi tpk4, urakka u, toimenpideinstanssi tpi
  WHERE tpi.toimenpide = tpk4.emo and tpi.urakka = u.id and u.tyyppi = 'hoito' and (not u.poistettu) and t.urakka = u.id and tpk4.id = tt.toimenpidekoodi AND (not tpk4.poistettu) AND t.alkanut > '2020-11-01' GROUP BY tpk4.id, tpk4.nimi, tpk4.yksikko, tpi.urakka, tpi.nimi; -- t.alkanut-rajaus nopeuttaa devkäytössä, muutetaan parametriksi

DROP VIEW IF EXISTS yh_vemtr_suunnitellut;
CREATE OR REPLACE TEMPORARY VIEW yh_vemtr_suunnitellut AS
  select tpk4.nimi as nimi,
         NULL ::integer as jarjestys,
	 SUM(tyo.maara) as "suunniteltu",
  	 (select extract(year from alkupvm) from urakka where id = tyo.urakka) as "hoitokauden-alkuvuosi",
	 tpk4.suunnitteluyksikko,
	 tpk4.yksikko,
	 tpk4.id as toimenpidekoodi,
	 tyo.urakka as urakka,
	 tpi.nimi as toimenpide, -- mistä tpi (josta tpi.nimi as toimenpide)?
	 null::numeric as "toteuma",
	 null ::numeric as "toteutunut-materiaalimaara",
	 'yh-suunnitellut' as rivityyppi
FROM yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava	,
  urakka u, toimenpideinstanssi tpi
  WHERE tpi.toimenpide = tpk4.emo and tpi.urakka = u.id and u.id = tyo.urakka AND u.tyyppi = 'hoito' GROUP BY tpk4.id, tyo.urakka, tpi.nimi;

DROP VIEW IF EXISTS mhu_vemtr;
CREATE OR REPLACE TEMPORARY VIEW mhu_vemtr AS
  WITH urakat AS (SELECT u.id
                FROM urakka u
                WHERE ('2020-01-01' between u.alkupvm and u.loppupvm -- fixme: parametrit
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
       sum(ut.maara)       as "suunniteltu",
       ut."hoitokauden-alkuvuosi"::smallint as "hoitokauden-alkuvuosi",
       tpk.suunnitteluyksikko as "suunnitteluyksikko",
       tpk.yksikko         as "yksikko",
       tpk.id              as "toimenpidekoodi",
       tpi.urakka          as "urakka",
       tpi.nimi            as "toimenpide",
       sum(toteumat.maara) as "toteuma",
       null ::numeric       as "toteutunut-materiaalimaara",
       'mh-toteutuneet' as rivityyppi

from toimenpideinstanssi tpi
       join toimenpidekoodi tpk on tpi.toimenpide = tpk.emo -- linkitys, ks meneeö samalla muissa vieweissä.
       left join urakka_tehtavamaara ut
                 on ut.tehtava = tpk.id
                   and ut.urakka = tpi.urakka
                   and ut.poistettu is not true
                   and ut."hoitokauden-alkuvuosi" in (2020) -- fixme: parametri
       left join toteumat
                 on toteumat.toimenpidekoodi = tpk.id
                   and toteumat.urakka_id = tpi.urakka

       join tehtavaryhma tr on tpk.tehtavaryhma = tr.id
WHERE ut.poistettu is not true
  and ut."hoitokauden-alkuvuosi" in (2020) -- fixme: parametri
  and ut.urakka in (SELECT id FROM urakat)
group by tpk.id, tpk.nimi, tpk.yksikko, ut."hoitokauden-alkuvuosi", tpk.jarjestys, tpi.nimi, tpk.suunnitteluyksikko, tpi.urakka;

SELECT * FROM mhu_vemtr UNION SELECT * FROM yh_vemtr_suunnitellut UNION SELECT * FROM yh_vemtr_toteutuneet;

-- mistä hoitokauden alkuvuosi yh-caseen?


