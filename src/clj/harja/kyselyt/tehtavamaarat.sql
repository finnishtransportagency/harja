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
with urakat as (select u.id, u.hallintayksikko
                from urakka u
                where (u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)
                  and case
                        when :urakka::integer is not null then
                          u.id = :urakka
                        when :hallintayksikko::integer is not null then
                          u.hallintayksikko = :hallintayksikko
                        else true
                  end
                  and u.tyyppi = 'teiden-hoito'),
     toteumat as (SELECT sum(rtm.tehtavamaara) as "maara",
                         sum(rtm.materiaalimaara) as "materiaalimaara",
                         rtm.toimenpidekoodi,
                         rtm.urakka_id
                    FROM urakat u
                         JOIN raportti_toteuma_maarat rtm ON rtm.urakka_id = u.id
                   WHERE
                         case when :urakka::integer is not null then rtm.urakka_id = :urakka else true end
                     AND (rtm.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
                   GROUP by rtm.toimenpidekoodi, rtm.urakka_id),
     suunnitelmat as (select sum(ut.maara) as "maara",
                             ut.tehtava,
                             ut.urakka
                      from urakka_tehtavamaara ut
                      where ut.urakka in (select id from urakat)
                        and ut."hoitokauden-alkuvuosi" in (:hoitokausi)
                        and ut.poistettu is not true
                      group by ut.urakka, ut.tehtava)
select tpk.nimi                 as "nimi", --tehtävän nimi
       tpk.jarjestys            as "jarjestys",
       suunnitelmat.maara       as "suunniteltu",
       tpk.suunnitteluyksikko   as "suunnitteluyksikko",
       tpk.yksikko              as "yksikko",
       tpk.id                   as "toimenpidekoodi",
       u.hallintayksikko        as "hallintayksikko",
       o.elynumero              as elynumero,
       tpk3.nimi                as "toimenpide",
       toteumat.maara           as "toteuma",
       toteumat.materiaalimaara as "toteutunut-materiaali",
       (CASE
            WHEN tpk3.koodi = '23104' THEN 1
            WHEN tpk3.koodi = '23116' THEN 2
            WHEN tpk3.koodi = '23124' THEN 3
            WHEN tpk3.koodi = '20107' THEN 4
            WHEN tpk3.koodi = '20191' THEN 5
            WHEN tpk3.koodi = '14301' THEN 6
            WHEN tpk3.koodi = '23151' THEN 7
           END)                 AS "toimenpide-jarjestys"
from toimenpideinstanssi tpi
       join urakat u
       join organisaatio o
            on o.id = u.hallintayksikko
            on u.id = tpi.urakka
       join toimenpidekoodi tpk on tpi.toimenpide = tpk.emo AND tpk.yksikko NOT ilike 'euro%'
       join toimenpidekoodi tpk3 on tpi.toimenpide = tpk3.id
       left join suunnitelmat
                 on suunnitelmat.tehtava = tpk.id
                   and suunnitelmat.urakka = tpi.urakka
       left join toteumat
                 on toteumat.toimenpidekoodi = tpk.id
                   and toteumat.urakka_id = tpi.urakka
       join tehtavaryhma tr on tpk.tehtavaryhma = tr.id
where tpi.urakka in (select id from urakat)
group by tpk.id, tpk.nimi, tpk.yksikko, tpk.jarjestys, tpk3.nimi, tpk3.koodi, tpk.suunnitteluyksikko,
         u.hallintayksikko, o.elynumero, suunnitelmat.maara, toteumat.maara, toteumat.materiaalimaara
having coalesce(suunnitelmat.maara, toteumat.maara) >= 0
order by o.elynumero ASC, "toimenpide-jarjestys" ASC, tpk.jarjestys ASC;


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
-- Pois jätetyt lisätyöhön viittaavat tehtäväryhmät ovat ainoastaan toteumien kirjaamista varten.
-- Nämä dummy-tehtäväryhmät ja niihin liitetyt tehtävät tarvitaan, koska toteumiin on pakko liittää tehtävä.
-- Lisätöiden kulut voidaan kohdistaa ilman tehtävää ja tehtäväryhmää suoraan toimenpiteelle.
SELECT distinct tpk3.id       as "toimenpide-id",
                tpk3.nimi     as "toimenpide",
                tr3.nimi      as "tehtavaryhma-nimi",
                tr3.id        as "tehtavaryhma-id",
                tr3.jarjestys as "jarjestys",
                tpi.id        as "toimenpideinstanssi"
FROM tehtavaryhma tr1
       JOIN tehtavaryhma tr2 ON tr1.id = tr2.emo
       JOIN tehtavaryhma tr3 ON tr2.id = tr3.emo and tr3.nimi not like ('%Lisätyöt%')
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
