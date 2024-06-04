-- name: hae-suunnitellut-hoitokauden-tehtavamaarat-urakassa
-- Hakee kannasta nykytilanteen, jota käytetään päättelemään luodaanko vai päivitetäänkö tallennettavaa tietoa.
SELECT ut.urakka                  as "urakka",
       ut."hoitokauden-alkuvuosi" as "hoitokauden-alkuvuosi",
       ut.tehtava                 as "tehtava-id",
       ut.maara                   as "maara",
       tk.aluetieto               as "aluetieto?"
FROM urakka_tehtavamaara ut
     JOIN tehtava tk on tk.id = ut.tehtava
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
       join tehtava tpk on tpi.toimenpide = tpk.emo AND tpk.yksikko NOT ilike 'euro%' AND tpk."raportoi-tehtava?" = TRUE
       join toimenpide tpk3 on tpi.toimenpide = tpk3.id
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
  (urakka, "hoitokauden-alkuvuosi", tehtava, maara, "muuttunut-tarjouksesta?", luotu, luoja)
VALUES (:urakka, :hoitokausi, :tehtava, :maara, true, current_timestamp, :kayttaja);

-- name: lisaa-urakka-tehtavamaara-mutta-ala-paivita<!
insert into urakka_tehtavamaara
(urakka, "hoitokauden-alkuvuosi", tehtava, maara, "muuttunut-tarjouksesta?", luotu, luoja)
values (:urakka, :hoitokauden-alkuvuosi, :tehtava, :maara, :muuttunut-tarjouksesta?, current_timestamp, :kayttaja)
on conflict do nothing;

-- name: paivita-tehtavamaara!
-- Päivittää urakan hoitokauden tehtävämäärät
UPDATE urakka_tehtavamaara
SET maara     = :maara,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja,
    "muuttunut-tarjouksesta?" = :muuttunut-tarjouksesta?
WHERE urakka = :urakka
  AND "hoitokauden-alkuvuosi" = :hoitokausi
  AND tehtava = :tehtava;

-- name: tehtavaryhmat-ja-toimenpiteet-urakalle
-- Pois jätetyt lisätyöhön viittaavat tehtäväryhmät ovat ainoastaan toteumien kirjaamista varten.
-- Nämä dummy-tehtäväryhmät ja niihin liitetyt tehtävät tarvitaan, koska toteumiin on pakko liittää tehtävä.
-- Lisätöiden kulut voidaan kohdistaa ilman tehtävää ja tehtäväryhmää suoraan toimenpiteelle.
SELECT distinct tp.id       as "toimenpide-id",
                tp.nimi     as "toimenpide",
                tr.nimi      as "tehtavaryhma-nimi",
                tr.id        as "tehtavaryhma-id",
                tr.jarjestys as "jarjestys",
                tpi.id        as "toimenpideinstanssi"
FROM tehtavaryhma tr
       LEFT JOIN tehtava t
                 ON tr.id = t.tehtavaryhma AND t."mhu-tehtava?" is true AND
                    t.poistettu is not true AND t.piilota is not true
       JOIN toimenpide tp ON t.emo = tp.id
       JOIN toimenpideinstanssi tpi on tpi.toimenpide = tp.id and tpi.urakka = :urakka
 WHERE tr.nimi not like ('%Lisätyöt%')
   AND (tr.voimassaolo_alkuvuosi IS NULL OR tr.voimassaolo_alkuvuosi <= :urakka-voimassaolo-alkuvuosi::INTEGER)
   AND (tr.voimassaolo_loppuvuosi IS NULL OR tr.voimassaolo_loppuvuosi >= :urakka-voimassaolo-loppuvuosi::INTEGER)
 order by tr.jarjestys;

-- name: hae-sopimuksen-tehtavamaarat-urakalle
select st.maara                    as "sopimuksen-tehtavamaara",
       st.tehtava                  as "tehtava",
       st.hoitovuosi               as "hoitovuosi",
       tpk.aluetieto               as "aluetieto"
from sopimus_tehtavamaara st
       JOIN tehtava tpk on st.tehtava = tpk.id
where st.urakka = :urakka;

-- name: hae-sopimuksen-tehtavamaaran-maara
select st.maara                    as "sopimuksen-maara",
       st.tehtava                  as "tehtava",
       st.hoitovuosi               as "hoitokauden-alkuvuosi",
       tpk.aluetieto               as "aluetieto?"
from sopimus_tehtavamaara st
         JOIN tehtava tpk on st.tehtava = tpk.id
where st.urakka = :urakka-id
  and st.tehtava = :tehtava-id;

-- name: poista-sopimuksen-tehtavamaara!
DELETE
  FROM sopimus_tehtavamaara st
 WHERE st.urakka = :urakka-id
   AND st.tehtava = :tehtava-id
   AND st.hoitovuosi = :vuosi;

-- name: mhu-suunniteltavat-tehtavat
-- Palauttaa tehtävähierarkian käyttöliittymän Suunnittelu > Tehtävä- ja määräluettelo-näkymää varten.
-- Äkillistä hoitotyötä ja Kolmansien osapuolten aiheuttaminen vahinkojen korjausta ei suunnitella tehtävälistalla.
  WITH rahavaraustehtava AS (
      SELECT rt.id, rt.tehtava_id
        FROM rahavaraus_urakka rvu
                 JOIN rahavaraus_tehtava rt ON rvu.rahavaraus_id = rt.rahavaraus_id
       WHERE rvu.urakka_id = :urakka
  )
SELECT ut.urakka                    AS "urakka",
       ut."hoitokauden-alkuvuosi"   AS "hoitokauden-alkuvuosi",
       t.jarjestys               AS "jarjestys",
       t.id                      AS "tehtava-id",
       ut.maara                     AS "suunniteltu-maara",
       ut."muuttunut-tarjouksesta?" AS "muuttunut-tarjouksesta?",
       o.otsikko                    AS "otsikko",
       tp.nimi                    AS "Toimenpide",
       tp.koodi                   AS "Toimenpidekoodi",
       tr.nimi                     AS "alataso",
       t.nimi                    AS "tehtava",
       t.suunnitteluyksikko      AS "yksikko",
       t.api_seuranta            AS "API-seuranta",
       t.api_tunnus              AS "API-tunnus",
       t.poistettu               AS "Poistettu",
       t.piilota                 AS "Piilota", -- älä näytä riviä käyttäjälle
       t."mhu-tehtava?"          AS "Ensisijainen",
       t.voimassaolo_alkuvuosi   AS "voimassaolo_alkuvuosi",
       t.voimassaolo_loppuvuosi  AS "voimassaolo_loppuvuosi",
       t.aluetieto               AS "aluetieto",
       sp.tallennettu               AS "sopimus-tallennettu",
       (select count(*) from rahavaraustehtava where tehtava_id = t.id) > 0 as "onko-rahavaraus?"
FROM tehtavaryhma tr
       LEFT JOIN tehtava t ON tr.id = t.tehtavaryhma 
                                AND t."mhu-tehtava?" is true 
                                AND t.poistettu is not true 
                                AND t.piilota is not true 
       JOIN toimenpide tp ON t.emo = tp.id
       LEFT OUTER JOIN urakka_tehtavamaara ut ON t.id = ut.tehtava
                                                     AND ut.urakka = :urakka
                                                     AND (ut."hoitokauden-alkuvuosi" in (:hoitokausi) OR t.aluetieto IS TRUE)
       LEFT JOIN sopimuksen_tehtavamaarat_tallennettu sp ON sp.urakka = :urakka
       JOIN tehtavaryhmaotsikko o ON tr.tehtavaryhmaotsikko_id = o.id,
     urakka u
WHERE u.id = :urakka
  AND (tr.voimassaolo_alkuvuosi IS NULL OR tr.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tr.voimassaolo_loppuvuosi IS NULL OR tr.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  AND (t.voimassaolo_alkuvuosi IS NULL OR t.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (t.voimassaolo_loppuvuosi IS NULL OR t.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  -- Suunnitteluyksikkö ei voi olla null normaali tehtävällä, mutta rahavarauksella se voi olla.
  AND (t.suunnitteluyksikko IS not null OR (select count(*) from rahavaraustehtava where tehtava_id = t.id) > 0)
ORDER BY t.jarjestys, t."mhu-tehtava?" desc;


-- name: hae-validit-tehtava-idt
SELECT id as "tehtava-id", yksikko as "yksikko"
FROM tehtava
WHERE tehtavaryhma IS NOT NULL
  and yksikko is not null
  AND poistettu IS NOT TRUE
  AND piilota IS NOT TRUE;

-- name: hae-urakan-suunniteltu-materiaalin-kaytto-tehtavamaarista
-- Hakee materiaalien suunnittelutiedot urakalle.
-- Varmistetaan, että tarjouksen tiedot on syötetty. Muuten ei palauteta mitään.
SELECT
    mk.id as materiaali_id,
    mk.nimi as materiaali,
    mk.yksikko AS materiaali_yksikko,
    mk.materiaalityyppi AS materiaali_tyyppi,
    ml.nimi as materiaaliluokka,
    ml.yksikko AS materiaaliluokka_yksikko,
    ml.materiaalityyppi AS materiaaliluokka_tyyppi,
    ut."hoitokauden-alkuvuosi",
    SUM(ut.maara) as maara,
    ut.muokattu,
    ut.luotu
FROM urakka_tehtavamaara ut
         JOIN urakka u ON ut.urakka = u.id AND u.urakkanro IS NOT NULL
         JOIN tehtava tk ON ut.tehtava = tk.id AND tk.materiaaliluokka_id IS NOT NULL
         JOIN materiaaliluokka ml ON tk.materiaaliluokka_id = ml.id
         LEFT JOIN materiaalikoodi mk ON tk.materiaalikoodi_id = mk.id
         JOIN sopimuksen_tehtavamaarat_tallennettu stt on u.id = stt.urakka AND stt.tallennettu IS TRUE
WHERE ut.poistettu IS NOT TRUE
  AND u.id = :urakka
GROUP BY ut."hoitokauden-alkuvuosi", mk.id, ml.nimi, ml.yksikko, ml.materiaalityyppi, ut.muokattu, ut.luotu;

-- name: hae-alueurakan-suunnitellut-tehtavamaarat
select sum(yt.maara) as "maara", tk.nimi as "tehtava", tk.id as "tehtava-id", MAX(yt.luotu) as luotu,
       MAX(yt.muokattu) as muokattu,
       CASE
           WHEN EXTRACT(MONTH FROM yt.alkupvm)::int = 1 AND EXTRACT(DAY FROM yt.alkupvm)::int = 1 THEN (EXTRACT(YEAR FROM yt.alkupvm) -1)::INT
           WHEN EXTRACT(MONTH FROM yt.alkupvm)::int = 10 AND EXTRACT(DAY FROM yt.alkupvm)::int = 1 THEN EXTRACT(YEAR FROM yt.alkupvm)::INT
           END
           AS "hoitokauden-alkuvuosi"
from yksikkohintainen_tyo yt
     JOIN tehtava tk on yt.tehtava = tk.id
where yt.urakka = :urakka-id
  -- Yksikköhintainen työ taulussa tehtävät on suunniteltu erikseen hoitokauden alkuosalle ja loppuosalle
  -- joten käytetään varmuuden vuoksi overlaps funktiota, joka palauttaa tiedot, mikäli edes osa suunnitellusta
  -- aikavälistä osuu annettuun ajankohtaan.
  and (yt.alkupvm, yt.loppupvm) overlaps (:alkupvm, :loppupvm)
group by yt.urakka, yt.tehtava, tk.id, "hoitokauden-alkuvuosi";

-- name: hae-mhurakan-suunnitellut-tehtavamaarat
-- Hakee materiaalien suunnittelutiedot urakalle.
-- Varmistetaan, että tarjouksen tiedot on syötetty. Muuten ei palauteta mitään.
SELECT
    SUM(ut.maara) as maara,
    tk.nimi as tehtava,
    tk.id as "tehtava-id",
    ut."hoitokauden-alkuvuosi",
    ut.muokattu,
    ut.luotu
FROM urakka_tehtavamaara ut
         JOIN tehtava tk ON ut.tehtava = tk.id AND tk.materiaaliluokka_id IS NULL AND tk.materiaalikoodi_id IS NULL
         JOIN sopimuksen_tehtavamaarat_tallennettu stt on ut.urakka = stt.urakka AND stt.tallennettu IS TRUE
WHERE ut.poistettu IS NOT TRUE
  AND ut.urakka = :urakka-id
  AND ut."hoitokauden-alkuvuosi" in (:hoitokauden-alkuvuodet)
GROUP BY ut."hoitokauden-alkuvuosi", tk.id, ut.muokattu, ut.luotu;
