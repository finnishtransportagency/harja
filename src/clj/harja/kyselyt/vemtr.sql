-- name: hae-yh-suunnitellut-ja-toteutuneet-aikavalilla
with urakat as (select id, hallintayksikko
                from urakka u
                where (:elinvoimakeskus::integer is null or u.elinvoimakeskus_id = :elinvoimakeskus::integer)
                  and u.tyyppi = 'hoito'
                  and u.poistettu = false
                  and (u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)),
     toteumat as (select sum(rtm.tehtavamaara) as "maara",
                         rtm.toimenpidekoodi as "toimenpidekoodi",
                         u.elinvoimakeskus_id as "elinvoimakeskus_id",
                         sum(rtm.materiaalimaara) as "materiaalimaara"
                    from raportti_toteuma_maarat rtm
                    left join urakka u on rtm.urakka_id = u.id
                   where rtm.urakka_id IN (SELECT id FROM urakat)
                     and (rtm.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
                     and u.tyyppi='hoito'
                   group by u.elinvoimakeskus_id, rtm.toimenpidekoodi),
     tyot as (select sum(yt.maara) as "maara", yt.tehtava as "tehtava", yt.urakka as "urakka"
              from yksikkohintainen_tyo yt
              where yt.urakka in (select id from urakat)
                and (yt.alkupvm, yt.loppupvm) overlaps (:alkupvm, :loppupvm)
              group by yt.urakka, yt.tehtava)
select (SELECT maara FROM toteumat t WHERE t.toimenpidekoodi = tehtava.id AND t.elinvoimakeskus_id = o.id)           as toteuma,
       SUM(tyot.maara)               as suunniteltu,
       (SELECT materiaalimaara FROM toteumat t WHERE t.toimenpidekoodi = tehtava.id AND t.elinvoimakeskus_id = o.id) as "toteutunut-materiaalimaara",
       o.id                          as hallintayksikko,
       o.elinvoimakeskusnumero       as elynumero,
       tehtava.nimi                  as nimi,
       emo.nimi                      as toimenpide,
       tehtava.suunnitteluyksikko    as suunnitteluyksikko,
       tehtava.yksikko               as yksikko,
       tehtava.jarjestys             as jarjestys,
       'yksikkohintaiset'            as rivityyppi,
       (CASE
            WHEN emo.koodi = '23104' THEN 1
            WHEN emo.koodi = '23116' THEN 2
            WHEN emo.koodi = '23124' THEN 3
            WHEN emo.koodi = '20107' THEN 4
            WHEN emo.koodi = '20191' THEN 5
            WHEN emo.koodi = '14301' THEN 6
            WHEN emo.koodi = '23151' THEN 7
            ELSE 8
           END)                      AS "toimenpide-jarjestys"
from toimenpideinstanssi tpi
       join urakka u on tpi.urakka = u.id
       join toimenpide emo on emo.id = tpi.toimenpide
       join tehtava on tehtava.emo = tpi.toimenpide AND tehtava.yksikko NOT ILIKE 'euro%' AND tehtava."raportoi-tehtava?" = TRUE
       left join tyot on tyot.tehtava = tehtava.id and tyot.urakka = u.id
       join organisaatio o on o.id = u.elinvoimakeskus_id
where tpi.urakka in (select id from urakat)
group by o.nimi, o.id, o.elinvoimakeskusnumero, emo.nimi, tehtava.nimi, tehtava.id, tehtava.suunnitteluyksikko, tehtava.yksikko, tehtava.jarjestys, emo.koodi
having coalesce((SELECT maara FROM toteumat t WHERE t.toimenpidekoodi = tehtava.id AND t.elinvoimakeskus_id = o.id), SUM(tyot.maara)) >= 0
order by o.elinvoimakeskusnumero ASC, "toimenpide-jarjestys" ASC, tehtava.jarjestys ASC;
