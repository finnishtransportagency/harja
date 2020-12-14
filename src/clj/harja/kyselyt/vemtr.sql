
-- name: hae-yh-toteutuneet-tehtavamaarat-ja-toteumat-aikavalilla

-- todo 1 - onko samasta työstä tarkoitus tuottaa sekä toteutuneiden ja suunniteltujen rivit,
-- vai pitäisikö näiden jotenkin sulkea toisiaan pois, vai yhdistetäänkö ne raporttikoodissa?

-- todo 2 - toimenpiteiden järjestys - mukaile tm-rapsan puolelta

-- todo 3 - ryhmittely toimenpiteen (l3) mukaan ja summat elyittäin, eli esim talvihoito pp elyssä ois yksi rivi


SELECT tpk4.nimi as nimi,
     NULL ::integer as jarjestys,
     NULL ::numeric as "suunniteltu",
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
  WHERE tpi.toimenpide = tpk4.emo and tpi.urakka = u.id and u.tyyppi = 'hoito' and
        (not u.poistettu) and t.urakka = u.id and tpk4.id = tt.toimenpidekoodi and
	(not tpk4.poistettu) AND
	(t.alkanut, t.paattynyt) OVERLAPS (:alkupvm, :loppupvm)
	and ((:hallintayksikko::integer is null) or (u.hallintayksikko = :hallintayksikko::integer))
  GROUP BY tpk4.id, tpk4.nimi, tpk4.yksikko, tpi.urakka, tpi.nimi


-- name: hae-yh-suunnitellut-tehtavamaarat-ja-toteumat-aikavalilla
SELECT tpk4.nimi as nimi,
         NULL ::integer as jarjestys,
	 SUM(tyo.maara) as "suunniteltu",
  	 (select extract(year from alkupvm) from urakka where id = tyo.urakka) as "hoitokauden-alkuvuosi",
	 tpk4.suunnitteluyksikko,
	 tpk4.yksikko,
	 tpk4.id as toimenpidekoodi,
	 tyo.urakka as urakka,
	 tpi.nimi as toimenpide,
	 null::numeric as "toteuma",
	 null ::numeric as "toteutunut-materiaalimaara",
	 'yh-suunnitellut' as rivityyppi
FROM yksikkohintainen_tyo tyo
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tyo.tehtava,
  urakka u, toimenpideinstanssi tpi
  WHERE tpi.toimenpide = tpk4.emo and tpi.urakka = u.id and
        u.id = tyo.urakka AND u.tyyppi = 'hoito' and (not u.poistettu) and
	(u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)
	and ((:hallintayksikko::integer is null) or (u.hallintayksikko = :hallintayksikko::integer))

  GROUP BY tpk4.id, tyo.urakka, tpi.nimi

-- name: hae-yh-suunnitellut-ja-toteutuneet-aikavalilla
with urakat as (select id, hallintayksikko
                from urakka u
                where (:hallintayksikko::integer is null or u.hallintayksikko = :hallintayksikko::integer)
                  and u.tyyppi = 'hoito'
                  and u.poistettu = false
                  and (u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)),
     toteumat as (select sum(tt.maara) as "maara", tpk.id as "toimenpidekoodi", o.id as "hallintayksikko"
                  from toteuma t
                         join urakka u
                         join organisaatio o on u.hallintayksikko = o.id
                              on t.urakka = u.id

                         join toteuma_tehtava tt on tt.toteuma = t.id
                         join toimenpidekoodi tpk on tt.toimenpidekoodi = tpk.id

                  where t.urakka in (select id
                                     from urakka u
                                     where (:hallintayksikko::integer is null or u.hallintayksikko = :hallintayksikko::integer)
                                       and u.tyyppi = 'hoito'
                                       and u.poistettu = false
                                       and (u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm))
                  group by o.id, tpk.id),
     tyot as (select sum(yt.maara) as "maara", yt.tehtava as "tehtava", yt.urakka as "urakka"
              from yksikkohintainen_tyo yt
              where yt.urakka in (select id from urakat)
                and (yt.alkupvm, yt.loppupvm) overlaps (:alkupvm, :loppupvm)
              group by yt.urakka, yt.tehtava)
select toteumat.maara             as toteuma,
       tyot.maara                 as suunniteltu,
       o.id                     as hallintayksikko,
       tehtava.nimi               as nimi,
       emo.nimi                   as toimenpide,
       tehtava.suunnitteluyksikko as suunnitteluyksikko,
       tehtava.yksikko            as yksikko,
       tehtava.jarjestys as jarjestys,
       'yksikkohintaiset' as rivityyppi,
       (CASE
          WHEN emo.koodi = '23104' THEN 1
          WHEN emo.koodi = '23116' THEN 2
          WHEN emo.koodi = '23124' THEN 3
          WHEN emo.koodi = '20107' THEN 4
          WHEN emo.koodi = '20191' THEN 5
          WHEN emo.koodi = '14301' THEN 6
          WHEN emo.koodi = '23151' THEN 7
         END)                 AS "toimenpide-jarjestys"
from toimenpideinstanssi tpi
       join urakka u on tpi.urakka = u.id
       join toimenpidekoodi emo on emo.id = tpi.toimenpide
       join toimenpidekoodi tehtava on tehtava.emo = tpi.toimenpide
       left join tyot on tyot.tehtava = tehtava.id and tyot.urakka = u.id
       join organisaatio o on o.id = u.hallintayksikko
       left join toteumat on toteumat.toimenpidekoodi = tehtava.id and toteumat.hallintayksikko = u.hallintayksikko
where tpi.urakka in (select id from urakat)
group by o.nimi, o.id, emo.nimi, tehtava.nimi, tehtava.suunnitteluyksikko, tehtava.yksikko, toteumat.maara, tyot.maara, tpi.urakka, tehtava.jarjestys, emo.koodi
having coalesce(toteumat.maara, tyot.maara) >= 0
