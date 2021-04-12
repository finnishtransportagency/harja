-- name: hae-yh-suunnitellut-ja-toteutuneet-aikavalilla
with urakat as (select id, hallintayksikko
                from urakka u
                where (:hallintayksikko::integer is null or u.hallintayksikko = :hallintayksikko::integer)
                  and u.tyyppi = 'hoito'
                  and u.poistettu = false
                  and (u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)),
     toteumat as (select sum(rtm.tehtavamaara) as "maara",
                         rtm.toimenpidekoodi as "toimenpidekoodi",
                         rtm.hallintayksikko_id as "hallintayksikko",
                         sum(rtm.materiaalimaara) as "materiaalimaara"
                    from raportti_toteuma_maarat rtm
                   where (:hallintayksikko::integer is null or rtm.hallintayksikko_id = :hallintayksikko::integer)
                     and (rtm.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
                   group by rtm.hallintayksikko_id, rtm.toimenpidekoodi),
     tyot as (select sum(yt.maara) as "maara", yt.tehtava as "tehtava", yt.urakka as "urakka"
              from yksikkohintainen_tyo yt
              where yt.urakka in (select id from urakat)
                and (yt.alkupvm, yt.loppupvm) overlaps (:alkupvm, :loppupvm)
              group by yt.urakka, yt.tehtava)
select SUM(toteumat.maara)           as toteuma,
       SUM(tyot.maara)               as suunniteltu,
       SUM(toteumat.materiaalimaara) as "toteutunut-materiaalimaara",
       o.id                          as hallintayksikko,
       o.elynumero                   as elynumero,
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
       join toimenpidekoodi emo on emo.id = tpi.toimenpide
       join toimenpidekoodi tehtava on tehtava.emo = tpi.toimenpide AND tehtava.yksikko NOT ILIKE 'euro%' AND tehtava."raportoi-tehtava?" = TRUE
       left join tyot on tyot.tehtava = tehtava.id and tyot.urakka = u.id
       join organisaatio o on o.id = u.hallintayksikko
       left join toteumat on toteumat.toimenpidekoodi = tehtava.id and toteumat.hallintayksikko = u.hallintayksikko
where tpi.urakka in (select id from urakat)
group by o.nimi, o.id, o.elynumero, emo.nimi, tehtava.nimi, tehtava.suunnitteluyksikko, tehtava.yksikko, tehtava.jarjestys, emo.koodi
having coalesce(SUM(toteumat.maara), SUM(tyot.maara)) >= 0
order by o.elynumero ASC, "toimenpide-jarjestys" ASC, tehtava.jarjestys ASC;
