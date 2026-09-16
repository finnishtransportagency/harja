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
with urakat as (select u.id, u.elinvoimakeskus_id
                  from urakka u
                 where (u.alkupvm, u.loppupvm) OVERLAPS (:alkupvm, :loppupvm)
                   and case
                        when :urakka::integer is not null then
                          u.id = :urakka
                        when :elinvoimakeskus::integer is not null then
                          u.elinvoimakeskus_id = :elinvoimakeskus
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
     suunnitelmat as (select sum(v.laskettu_maara) as "maara",
                             v.tehtava,
                             v.urakka
                      from urakka_tehtavamaara_yhteenveto v
                      where v.urakka in (select id from urakat)
                        and v.hoitokauden_alkuvuosi in (:hoitokausi)
                      group by v.urakka, v.tehtava)
select tpk.nimi                 as "nimi", --tehtävän nimi
       tpk.jarjestys            as "jarjestys",
       suunnitelmat.maara       as "suunniteltu",
       tpk.suunnitteluyksikko   as "suunnitteluyksikko",
       tpk.yksikko              as "yksikko",
       tpk.id                   as "toimenpidekoodi",
       u.elinvoimakeskus_id     as elinvoimakeskus_id,
       o.elinvoimakeskusnumero  as elinvoimakeskusnumero,
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
            on o.id = u.elinvoimakeskus_id
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
         u.elinvoimakeskus_id, o.elinvoimakeskusnumero, suunnitelmat.maara, toteumat.maara, toteumat.materiaalimaara
having coalesce(suunnitelmat.maara, toteumat.maara) >= 0
order by o.elinvoimakeskusnumero ASC, "toimenpide-jarjestys" ASC, tpk.jarjestys ASC;


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
   -- Rajaa johto- ja hallinto pois, jos muutokset on käytössä 
   AND (:muutokset-kaytossa? IS FALSE OR tr.yksiloiva_tunniste NOT IN ('a6614475-1950-4a61-82c6-fda0fd19bb54'))
   AND (tr.voimassaolo_alkuvuosi IS NULL OR tr.voimassaolo_alkuvuosi <= :urakka-voimassaolo-alkuvuosi::INTEGER)
   AND (tr.voimassaolo_loppuvuosi IS NULL OR tr.voimassaolo_loppuvuosi >= :urakka-voimassaolo-alkuvuosi::INTEGER)
 order by tr.jarjestys;

-- name: tehtavaryhman-tehtavat-urakalle
WITH maaramitattavat_tehtavat AS (
  SELECT
    tpk.id,
    tpk.nimi,
    tpk.jarjestys,
    tpk.emo,
    tpk."maaramitattava?",
    tpi.id as toimenpideinstanssi
  FROM tehtava tpk
    JOIN toimenpideinstanssi tpi on tpi.toimenpide = tpk.emo and tpi.urakka = :urakka-id
  WHERE
    tpk.tehtavaryhma = :tehtavaryhma-id
    AND tpk."maaramitattava?" IS TRUE
    AND tpk."mhu-tehtava?" IS TRUE
    AND tpk.piilota IS NOT true
    AND tpk.poistettu IS NOT true
    -- Tehtävän voimassaoloa verrataan aina urakan alkuvuoteen. Eli kun uudet urakat alkavat,
    -- niin voimassaolon perusteella voidaan urakalle määritellä jotain tiettyjä poikkeuksia tehtävien suhteen.
    AND (tpk.voimassaolo_alkuvuosi IS NULL OR tpk.voimassaolo_alkuvuosi <= :urakan-alkuvuosi::INTEGER)
    AND (tpk.voimassaolo_loppuvuosi IS NULL OR tpk.voimassaolo_loppuvuosi >= :urakan-alkuvuosi::INTEGER)
)
-- 1. Palautetaan kaikki maaramitattavat tehtävät
SELECT
  id,
  nimi,
  jarjestys,
  emo,
  "maaramitattava?" as "maaramitattava?",
  toimenpideinstanssi as "toimenpideinstanssi"
FROM maaramitattavat_tehtavat

UNION ALL

-- 2. Palautetaan "Muu tehtävä (ei määrämitattava)", jos ehdot täyttyvät
SELECT
  -1 as id,
  'Muu tehtävä (ei määrämitattava)' as nimi,
  99999 as jarjestys,
  NULL as emo,
  true as "maaramitattava?",
  (SELECT toimenpideinstanssi FROM maaramitattavat_tehtavat LIMIT 1) as "toimenpideinstanssi"
WHERE
  -- Ehto 1: On olemassa vähintään yksi maaramitattava tehtävä
  EXISTS (SELECT 1 FROM maaramitattavat_tehtavat)
  AND
  -- Ehto 2: On olemassa vähintään yksi EI-maaramitattava tehtävä
  EXISTS (
    SELECT 1 FROM tehtava t2
    WHERE t2.tehtavaryhma = :tehtavaryhma-id
      AND t2."maaramitattava?" IS NOT TRUE
      AND t2."mhu-tehtava?" IS TRUE
      AND t2.piilota IS NOT true
      AND t2.poistettu IS NOT true
      -- Tehtävän voimassaoloa verrataan aina urakan alkuvuoteen. Eli kun uudet urakat alkavat,
      -- niin voimassaolon perusteella voidaan urakalle määritellä jotain tiettyjä poikkeuksia tehtävien suhteen.
      AND (t2.voimassaolo_alkuvuosi IS NULL OR t2.voimassaolo_alkuvuosi <= :urakan-alkuvuosi::INTEGER)
      AND (t2.voimassaolo_loppuvuosi IS NULL OR t2.voimassaolo_loppuvuosi >= :urakan-alkuvuosi::INTEGER)
  )
ORDER BY jarjestys, nimi;


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
SELECT v.urakka                    AS "urakka",
       v.hoitokauden_alkuvuosi   AS "hoitokauden-alkuvuosi",
       t.jarjestys               AS "jarjestys",
       t.id                      AS "tehtava-id",
       v.laskettu_maara             AS "suunniteltu-maara",
       NULL AS "muuttunut-tarjouksesta?",
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
       LEFT OUTER JOIN urakka_tehtavamaara_yhteenveto v ON t.id = v.tehtava
                                                     AND v.urakka = :urakka
                                                     AND (v.hoitokauden_alkuvuosi in (:hoitokausi) OR t.aluetieto IS TRUE)
       LEFT JOIN sopimuksen_tehtavamaarat_tallennettu sp ON sp.urakka = :urakka
       JOIN tehtavaryhmaotsikko o ON tr.tehtavaryhmaotsikko_id = o.id,
     urakka u
WHERE u.id = :urakka
  AND (tr.voimassaolo_alkuvuosi IS NULL OR tr.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tr.voimassaolo_loppuvuosi IS NULL OR tr.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  AND (t.voimassaolo_alkuvuosi IS NULL OR t.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (t.voimassaolo_loppuvuosi IS NULL OR t.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  -- Suunnitteluyksikkö ei voi null tai euroa
  AND t.suunnitteluyksikko IS not null
  AND t.suunnitteluyksikko != 'euroa'
-- Eikä tehtävä kuulu mihinkään rahavaraukseen
  AND (select count(*) from rahavaraustehtava where tehtava_id = t.id) = 0
ORDER BY t.jarjestys, t."mhu-tehtava?" desc;


-- name: hae-validit-tehtava-idt
SELECT id as "tehtava-id", yksikko as "yksikko"
FROM tehtava
WHERE tehtavaryhma IS NOT NULL
  and yksikko is not null
  AND poistettu IS NOT TRUE
  AND piilota IS NOT TRUE;

-- name: hae-urakan-suunniteltu-materiaalin-kaytto-tehtavamaarista-analytiikalle
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
    NULL AS "hoito-materiaalimaara-id",
    v.id AS "mhu-materiaalimaara-id",
    NULL AS "suolaraja-id",
    v.tehtava AS "tehtava-id",
    v.hoitokauden_alkuvuosi AS "hoitokauden-alkuvuosi",
    SUM(v.laskettu_maara) as maara,
    v.muokattu as muokattu,
    v.luotu as luotu
FROM urakka_tehtavamaara_yhteenveto v
         JOIN urakka u ON v.urakka = u.id AND u.urakkanro IS NOT NULL
         JOIN tehtava tk ON v.tehtava = tk.id AND tk.materiaaliluokka_id IS NOT NULL
         JOIN materiaaliluokka ml ON tk.materiaaliluokka_id = ml.id
         LEFT JOIN materiaalikoodi mk ON tk.materiaalikoodi_id = mk.id
WHERE u.id = :urakka
  AND EXISTS (SELECT 1 FROM sopimuksen_tehtavamaarat_tallennettu stt 
              WHERE stt.urakka = u.id AND stt.tallennettu IS TRUE)
GROUP BY v.hoitokauden_alkuvuosi, mk.id, ml.nimi, ml.yksikko, ml.materiaalityyppi, v.id, v.tehtava, v.muokattu, v.luotu;

-- name: hae-alueurakan-suunnitellut-tehtavamaarat-analytiikalle
select sum(yt.maara) as "maara", tk.nimi as "tehtava", tk.id as "tehtava-id", NULL AS "mhu-tehtavamaara-id", yt.id as "hoito-tehtavamaara-id", yt.luotu as luotu,
       yt.muokattu as muokattu,
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
group by yt.urakka, yt.tehtava, tk.id, yt.id, "hoitokauden-alkuvuosi", yt.luotu, yt.muokattu;

-- name: hae-mhurakan-suunnitellut-tehtavamaarat-analytiikalle
-- Hakee materiaalien suunnittelutiedot urakalle.
-- Varmistetaan, että tarjouksen tiedot on syötetty. Muuten ei palauteta mitään.
SELECT
    SUM(v.laskettu_maara) as maara,
    tk.nimi as tehtava,
    tk.id as "tehtava-id",
    v.id as "mhu-tehtavamaara-id",
    NULL AS "hoito-tehtavamaara-id",
    v.hoitokauden_alkuvuosi AS "hoitokauden-alkuvuosi",
    v.muokattu as muokattu,
    v.luotu as luotu
FROM urakka_tehtavamaara_yhteenveto v
         JOIN urakka u ON v.urakka = u.id
         JOIN tehtava tk ON v.tehtava = tk.id AND tk.materiaaliluokka_id IS NULL AND tk.materiaalikoodi_id IS NULL
WHERE v.urakka = :urakka-id
  AND v.hoitokauden_alkuvuosi in (:hoitokauden-alkuvuodet)
  AND EXISTS (SELECT 1 FROM sopimuksen_tehtavamaarat_tallennettu stt 
              WHERE stt.urakka = u.id AND stt.tallennettu IS TRUE)
GROUP BY v.hoitokauden_alkuvuosi, tk.id, v.id, v.muokattu, v.luotu;
