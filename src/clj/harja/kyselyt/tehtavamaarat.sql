-- name: hae-hoitokauden-tehtavamaarat-urakassa
-- Hakee kannasta nykytilanteen, jota käytetään päättelemään luodaanko vai päivitetäänkö tallennettavaa tietoa.
SELECT ut.urakka                  as "urakka",
       ut."hoitokauden-alkuvuosi" as "hoitokauden-alkuvuosi",
       ut.tehtava                 as "tehtava-id",
       ut.maara                   as "maara"
FROM urakka_tehtavamaara ut
WHERE ut.urakka = :urakka
  AND ut."hoitokauden-alkuvuosi" = :hoitokausi
  AND ut.poistettu IS NOT TRUE;

-- name: hae-tehtavamaarat-ja-toteumat-aikavalilla
-- Raportin hakuhimmeli
with urakat as (select u.id
                from urakka u
                where (:alkupvm between u.alkupvm and u.loppupvm
                  or :loppupvm between u.alkupvm and u.loppupvm)
                  and case
                        when :urakka::integer is not null then
                          u.id = :urakka
                        when :hallintayksikko::integer is not null then
                          u.hallintayksikko = :hallintayksikko
                        else true
                  end
                  and u.tyyppi = 'teiden-hoito'),
     toteumat as (select sum(tt.maara) as "maara",
                         tt.toimenpidekoodi,
                         tt.poistettu,
                         tt.urakka_id
                  from toteuma t
                         join toteuma_tehtava tt on tt.toteuma = t.id and tt.poistettu = false
                    and tt.urakka_id in (select id from urakat)
                  where --t.lahde = 'harja-ui'
                    :alkupvm <= t.paattynyt
                    and case when :urakka::integer is not null then t.urakka = :urakka else true end
                    and :loppupvm >= t.alkanut
                    and t.poistettu = false
                  group by tt.toimenpidekoodi, tt.poistettu, tt.urakka_id),
     suunnitelmat as (select sum(ut.maara) as "maara",
                             ut.tehtava,
                             ut.urakka
                      from urakka_tehtavamaara ut
                      where ut.urakka in (select id from urakat)
                        and ut."hoitokauden-alkuvuosi" in (:hoitokausi)
                        and ut.poistettu is not true
                      group by ut.urakka, ut.tehtava)
select tpk.nimi               as "nimi",
       tpk.jarjestys          as "jarjestys",
       suunnitelmat.maara     as "suunniteltu",
       tpk.suunnitteluyksikko as "suunnitteluyksikko",
       tpk.yksikko            as "yksikko",
       tpk.id                 as "toimenpidekoodi",
       tpi.urakka             as "urakka",
       tpi.nimi               as "toimenpide",
       toteumat.maara         as "toteuma"
from toimenpideinstanssi tpi
       join toimenpidekoodi tpk on tpi.toimenpide = tpk.emo
       left join suunnitelmat
                 on suunnitelmat.tehtava = tpk.id
       left join toteumat
                 on toteumat.toimenpidekoodi = tpk.id
                   and toteumat.urakka_id = tpi.urakka
       join tehtavaryhma tr on tpk.tehtavaryhma = tr.id
where tpi.urakka in (select id from urakat)
group by tpk.id, tpk.nimi, tpk.yksikko, tpk.jarjestys, tpi.nimi, tpk.suunnitteluyksikko, tpi.urakka, suunnitelmat.maara, toteumat.maara
having coalesce(suunnitelmat.maara, toteumat.maara) >= 0
order by tpk.id;

-- name: lisaa-tehtavamaara<!
INSERT INTO urakka_tehtavamaara
  (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu, luoja)
VALUES (:urakka, :hoitokausi, :tehtava, :maara, current_timestamp, :kayttaja);

-- name: paivita-tehtavamaara!
-- Päivittää urakan hoitokauden tehtävämäärät
UPDATE urakka_tehtavamaara
SET maara     = :maara,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE urakka = :urakka
  AND "hoitokauden-alkuvuosi" = :hoitokausi
  AND tehtava = :tehtava;

-- name: tehtavaryhmat-ja-toimenpiteet-urakalle
SELECT distinct tpk3.id       as "toimenpide-id",
                tpk3.nimi     as "toimenpide",
                tr3.nimi      as "tehtavaryhma-nimi",
                tr3.id        as "tehtavaryhma-id",
                tr3.jarjestys as "jarjestys",
                tpi.id        as "toimenpideinstanssi"
FROM tehtavaryhma tr1
       JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
       JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
       LEFT JOIN toimenpidekoodi tpk4
                 ON tr3.id = tpk4.tehtavaryhma and tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                    tpk4.poistettu is not true AND tpk4.piilota is not true
       JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
       JOIN toimenpideinstanssi tpi on tpi.toimenpide = tpk3.id and tpi.urakka = :urakka
WHERE tr1.emo is null
order by tr3.jarjestys;

-- name: hae-tehtavahierarkia
-- Palauttaa tehtävähierarkian kokonaisuudessaan.
-- Käytä tehtävä- ja määräluettelossa hierarkian hakemiseen SQL-lausetta: hae-tehtavahierarkia-maarineen.
SELECT tr1.jarjestys               as "otsikon-jarjestys",
       tpk4.jarjestys              as "jarjestys",
       tpk4.id                     as "tehtava-id",
       tr3.otsikko                 as "otsikko",
       tpk3.nimi                   as "Toimenpide",
       tpk3.koodi                  as "Toimenpidekoodi",
       tr1.nimi                    as "ylataso",
       tr1.id                      as "ylataso-id",
       tr2.nimi                    as "valitaso",
       tr2.id                      as "valitaso-id",
       tr3.nimi                    as "alataso",
       tr3.id                      as "alataso-id",
       tpk4.nimi                   as "tehtava",
       tpk4.suunnitteluyksikko     as "yksikko",
       tpk4.api_seuranta           as "API-seuranta",
       tpk4.api_tunnus             as "API-tunnus",
       tpk4.poistettu              as "Poistettu",
       tpk4.piilota                as "Piilota", -- älä näytä riviä käyttäjälle
       tpk4.ensisijainen           as "Ensisijainen",
       tpk4.voimassaolo_alkuvuosi  as "voimassaolo_alkuvuosi",
       tpk4.voimassaolo_loppuvuosi as "voimassaolo_loppuvuosi"
FROM tehtavaryhma tr1
       JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
       JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
       LEFT JOIN toimenpidekoodi tpk4
                 ON tr3.id = tpk4.tehtavaryhma and tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                    tpk4.poistettu is not true AND tpk4.piilota is not true
       JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
       JOIN toimenpideinstanssi tpi on tpk3.id = tpi.toimenpide
       JOIN urakka u on tpi.urakka = u.id AND u.id = :urakka
WHERE tr1.emo is null
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
ORDER BY tpk4.jarjestys, tpk4.ensisijainen desc;

-- name: hae-tehtavahierarkia-maarineen
-- Palauttaa tehtävähierarkian käyttöliittymän Suunnittelu > Tehtävä- ja määräluettelo-näkymää varten.
-- Äkillistä hoitotyötä ja Kolmansien osapuolten aiheuttaminen vahinkojen korjausta ei suunnitella tehtävälistalla.
SELECT ut.urakka                   as "urakka",
       ut."hoitokauden-alkuvuosi"  as "hoitokauden-alkuvuosi",
       tr1.jarjestys               as "otsikon-jarjestys",
       tpk4.jarjestys              as "jarjestys",
       tpk4.id                     as "tehtava-id",
       ut.maara                    as "maara",
       tr3.otsikko                 as "otsikko",
       tpk3.nimi                   as "Toimenpide",
       tpk3.koodi                  as "Toimenpidekoodi",
       tr1.nimi                    as "ylataso",
       tr2.nimi                    as "valitaso",
       tr3.nimi                    as "alataso",
       tpk4.nimi                   as "tehtava",
       tpk4.suunnitteluyksikko     as "yksikko",
       tpk4.api_seuranta           as "API-seuranta",
       tpk4.api_tunnus             as "API-tunnus",
       tpk4.poistettu              as "Poistettu",
       tpk4.piilota                as "Piilota", -- älä näytä riviä käyttäjälle
       tpk4.ensisijainen           as "Ensisijainen",
       tpk4.voimassaolo_alkuvuosi  as "voimassaolo_alkuvuosi",
       tpk4.voimassaolo_loppuvuosi as "voimassaolo_loppuvuosi"
FROM tehtavaryhma tr1
       JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
       JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo
       LEFT JOIN toimenpidekoodi tpk4
                 ON tr3.id = tpk4.tehtavaryhma AND tpk4.taso = 4 AND tpk4.ensisijainen is true AND
                    tpk4.poistettu is not true AND tpk4.piilota is not true AND (tpk4.yksiloiva_tunniste NOT IN
                                                                                 (
                                                                                  'd373c08b-32eb-4ac2-b817-04106b862fb1', --'Äkillinen hoitotyö (talvihoito)',
                                                                                  '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974', --'Äkillinen hoitotyö (l.ymp.hoito)',
                                                                                  '1f12fe16-375e-49bf-9a95-4560326ce6cf', --'Äkillinen hoitotyö (soratiet)',
                                                                                  'b3a7a210-4ba6-4555-905c-fef7308dc5ec', --'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)',
                                                                                  '63a2585b-5597-43ea-945c-1b25b16a06e2', --'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)',
                                                                                  '49b7388b-419c-47fa-9b1b-3797f1fab21d' --'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)'
                                                                                   -- ei ehkä samassa järjestyksessä, mutta nuo tehtävät
                                                                                   ) or tpk4.yksiloiva_tunniste is null)
       JOIN toimenpidekoodi tpk3 ON tpk4.emo = tpk3.id
       LEFT OUTER JOIN urakka_tehtavamaara ut
                       ON tpk4.id = ut.tehtava AND ut.urakka = :urakka AND ut."hoitokauden-alkuvuosi" in (:hoitokausi)
       LEFT OUTER JOIN urakka u ON ut.urakka = u.id
WHERE tr1.emo is null
  AND (tpk4.voimassaolo_alkuvuosi IS NULL OR tpk4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tpk4.voimassaolo_loppuvuosi IS NULL OR tpk4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
ORDER BY tpk4.jarjestys, tpk4.ensisijainen desc;


-- name: hae-validit-tehtava-idt
SELECT id as "tehtava-id", yksikko as "yksikko"
FROM toimenpidekoodi
WHERE tehtavaryhma IS NOT NULL
  and yksikko is not null
  AND poistettu IS NOT TRUE
  AND piilota IS NOT TRUE;
