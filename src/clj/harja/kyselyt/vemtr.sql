-- name: hae-yh-suunnitellut-ja-toteutuneet-aikavalilla
with urakat as (select id, hallintayksikko
                from urakka u
                where (:hallintayksikko::integer is null or u.hallintayksikko = :hallintayksikko::integer)
                  and u.tyyppi = 'hoito'
                  and u.poistettu = false
                  and (u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)),
     toteumat as (select sum(tt.maara) as "maara", tpk.id as "toimenpidekoodi", o.id as "hallintayksikko", sum(tm.maara) as "materiaalimaara"
                    from toteuma t
                         LEFT JOIN toteuma_materiaali tm ON t.id = tm.toteuma AND tm.poistettu = FALSE
                         join urakat u
                         join organisaatio o on u.hallintayksikko = o.id on t.urakka = u.id
                         join toteuma_tehtava tt on tt.toteuma = t.id AND tt.urakka_id = t.urakka AND tt.poistettu = false
                         join toimenpidekoodi tpk on tt.toimenpidekoodi = tpk.id
                   where t.urakka in (select id from urakat)
                     and (t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
                     and t.poistettu = false
                   group by o.id, tpk.id),
     tyot as (select sum(yt.maara) as "maara", yt.tehtava as "tehtava", yt.urakka as "urakka"
              from yksikkohintainen_tyo yt
              where yt.urakka in (select id from urakat)
                and (yt.alkupvm, yt.loppupvm) overlaps (:alkupvm, :loppupvm)
              group by yt.urakka, yt.tehtava)
select toteumat.maara             as toteuma,
       tyot.maara                 as suunniteltu,
       -- 100.0   as "toteutunut-materiaalimaara",
       toteumat.materiaalimaara as "toteutunut-materiaalimaara",
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
          ELSE 8
         END)                 AS "toimenpide-jarjestys"
from toimenpideinstanssi tpi
       join urakka u on tpi.urakka = u.id
       join toimenpidekoodi emo on emo.id = tpi.toimenpide
       join toimenpidekoodi tehtava on tehtava.emo = tpi.toimenpide
       left join tyot on tyot.tehtava = tehtava.id and tyot.urakka = u.id
       join organisaatio o on o.id = u.hallintayksikko
       left join toteumat on toteumat.toimenpidekoodi = tehtava.id and toteumat.hallintayksikko = u.hallintayksikko
where tpi.urakka in (select id from urakat)
group by o.nimi, o.id, emo.nimi, tehtava.nimi, tehtava.suunnitteluyksikko, tehtava.yksikko, toteumat.maara, toteumat.materiaalimaara, tyot.maara, tpi.urakka, tehtava.jarjestys, emo.koodi
having coalesce(toteumat.maara, tyot.maara) >= 0
order by "toimenpide-jarjestys" ASC, tehtava.jarjestys ASC;
