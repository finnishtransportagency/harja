-- name: listaa-tehtavat-ja-toimenpiteet-kustannusten-seurantaan
-- Listaa kustannusten seurantaa varten tehtävien toteutuneet kustannukset ja budjetoidut kustannukset.
-- Arvioidut/Budjetoidut kustannukset tallennetaan KUSTANNUSARVOITU_TYO-tauluun.
-- Kustannusarvioitu työ toteutuu neljällä eri tyypillä
-- laskutettava-tyo, joka voi olla vaikka erillishankintoja.
-- muut-rahavaraukset
-- akillinen-hoitotyo on työtä, jonka kustannuksia ei voida tarkkaan arvioida, mutta johon tehdään rahavaraus. Summa on joka kuukaudelle sama.
-- vahinkojen-korjaukset on samaan tapaan työtä, jonka kustannuksia ei voida tarkkaan arvioida, mutta johon tehdään rahavaraus. Summa on joka kuukaudelle sama.
WITH urakan_toimenpideinstanssi_23150 AS
         (SELECT tpi.id AS id
          FROM toimenpideinstanssi tpi
                   JOIN toimenpidekoodi tpk3 ON tpk3.id = tpi.toimenpide
                   JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id,
               maksuera m
          WHERE tpi.urakka = :urakka
            AND m.toimenpideinstanssi = tpi.id
            AND tpk2.koodi = '23150')
SELECT tpi.id                                    AS toimenpideinstanssi_id,
       tk.nimi                                   AS toimenpidekoodi_nimi,
       kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       tk.koodi,
       kt.tyyppi::TEXT                           AS maksutyyppi,
       CASE
           WHEN kt.tyyppi::TEXT = 'laskutettava-tyo' THEN 'hankinta'
           ELSE 'rahavaraus'
           END                                   AS toimenpideryhma,
       tk_tehtava.nimi                           AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END                                   AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       tk_tehtava.jarjestys                      AS jarjestys,
       'hankintakustannukset'                    AS paaryhma
FROM toimenpidekoodi tk,
     kustannusarvioitu_tyo kt
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = kt.tehtava,
     toimenpideinstanssi tpi,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  AND (tk.koodi = '23104' -- talvihoito
    OR tk.koodi = '23116' -- liikenneympariston-hoito
    OR tk.koodi = '23124' -- sorateiden-hoito
    OR tk.koodi = '20107' -- paallystepaikkaukset
    OR tk.koodi = '20191' -- mhu-yllapito
    OR tk.koodi = '14301' -- mhu-korvausinvestointi
    OR tk.koodi = '23151' -- mhu-johto
    )
UNION ALL
-- kiinteahintainen_tyo taulusta haetaan osa suunnitelluista kustannuksista eli hankinnat. Tämä on siis vain budejetointi sarakkeeseen.
-- Kiinteät suunnitellut kustannukset tallennetaan KIINTEAHINTAINEN_TYO-tauluun.
-- Hinta on kiinteä, kun se on sopimuksessa sovittu, yleensä kuukausille jaettava könttäsumma.
SELECT tpi.id                                    AS toimenpideinstanssi_id,
       tk.nimi                                   AS toimenpidekoodi_nimi,
       kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       tk.koodi,
       'kiinteahintainen'                        AS maksutyyppi,
       'hankinta'                                AS toimenpideryhma,
       tk_tehtava.nimi                           AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END                                   AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       tk_tehtava.jarjestys                      AS jarjestys,
       'hankintakustannukset'                    AS paaryhma
FROM toimenpidekoodi tk,
     kiinteahintainen_tyo kt
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = kt.tehtava,
     toimenpideinstanssi tpi
WHERE tpi.urakka = :urakka
  AND kt.toimenpideinstanssi = tpi.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
  AND (tk.koodi = '23104' -- talvihoito
    OR tk.koodi = '23116' -- liikenneympariston-hoito
    OR tk.koodi = '23124' -- sorateiden-hoito
    OR tk.koodi = '20107' -- paallystepaikkaukset
    OR tk.koodi = '20191' -- mhu-yllapito
    OR tk.koodi = '14301' -- mhu-korvausinvestointi
    OR tk.koodi = '23151' -- mhu-johto
    )
-- Budjetoidut Erillishankinnat - toimenpide_koodi = '23150' - haetaan mukaan budjettiin kustannusarvioitu_työ taulusta, kun toimenpidekoodi on 23150
UNION ALL
SELECT tpi.id                                    AS toimenpideinstanssi_id,
       tk.nimi                                   AS toimenpidekoodi_nimi,
       kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       tk.koodi,
       'kiinteahintainen'                        AS maksutyyppi,
       'hankinta'                                AS toimenpideryhma,
       'Erillishankinnat (W)'                    AS tehtava_nimi,
       'Erillishankinnat'                        AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       0                                         AS jarjestys,
       'erillishankinnat'                        AS paaryhma
FROM toimenpidekoodi tk,
     kustannusarvioitu_tyo kt
     JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Erillishankinnat (W)')
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
-- Budjetoidut Hoidonjohdon palkkiot - toimenpide_koodi = '23150' - haetaan mukaan budjettiin kustannusarvioitu_työ taulusta, kun toimenpidekoodi on 23150
UNION ALL
SELECT tpi.id                                    AS toimenpideinstanssi_id,
       tk.nimi                                   AS toimenpidekoodi_nimi,
       kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       tk.koodi,
       'kiinteahintainen'                        AS maksutyyppi,
       'hankinta'                                AS toimenpideryhma,
       'Hoidonjohtopalkkio (G)'                  AS tehtava_nimi,
       'Hoidonjohdonpalkkio'                     AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       0                                         AS jarjestys,
       'hoidonjohdonpalkkio'                     AS paaryhma
FROM toimenpidekoodi tk,
     kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND ( kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)')
            OR kt.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744'))
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
UNION ALL
-- Johto- ja hallintokorvaus haetaan johto_ja_hallintokorvaus taulusta
-- Nämä on budjetoituja kustannuksia.
SELECT 0                                           AS toimenpideinstanssi_id,
       jjht.toimenkuva                             AS toimenpidekoodi_nimi,
       (hjh.tunnit * hjh.tuntipalkka)              AS budjetoitu_summa,
       0                                           AS toteutunut_summa,
       '0'                                         AS koodi,
       'kiinteahintainen'                          AS maksutyyppi,
       'hankinta'                                  AS toimenpideryhma,
       jjht.toimenkuva                             AS tehtava_nimi,
       'MHU Hoidonjohto'                           AS toimenpide,
       hjh.luotu                                   AS luotu,
       concat(hjh.vuosi, '-', hjh.kuukausi, '-01') AS ajankohta,
       'hjh'                                       AS toteutunut,
       160                                         AS jarjestys,
       'johto-ja-hallintakorvaus'                  AS paaryhma
FROM johto_ja_hallintokorvaus hjh
         LEFT JOIN johto_ja_hallintokorvaus_toimenkuva jjht on hjh."toimenkuva-id" = jjht.id
WHERE hjh."urakka-id" = :urakka
  AND (concat(hjh.vuosi, '-', hjh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
UNION ALL
-- Johto- ja hallintokorvaus haetaan myös kustannusarvioitu_tyo taulusta, koska muut kulut on toimistotarvikemateriaaleja
-- ja ne tallentuu sinne.
-- Nämä on budjetoituja kustannuksia.
SELECT tpi.id                                    AS toimenpideinstanssi_id,
       tk.nimi                                   AS toimenpidekoodi_nimi,
       kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       '0'                                       AS koodi,
       'kiinteahintainen'                        AS maksutyyppi,
       'hankinta'                                AS toimenpideryhma,
       'Muut kulut'                              AS tehtava_nimi,
       'MHU Hoidonjohto'                         AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'hjh'                                     AS toteutunut,
       160                                       AS jarjestys,
       'johto-ja-hallintakorvaus'                AS paaryhma
FROM toimenpidekoodi tk,
     kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND kt.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
UNION ALL
-- Toteutuneet kustannukset
SELECT tpi.id                  AS toimenpideinstanssi_id,
       tk.nimi                 AS toimenpidekoodi_nimi,
       0                       AS budjetoitu_summa,
       lk.summa                AS toteutunut_summa,
       tk.koodi,
       lk.maksueratyyppi::TEXT AS maksutyyppi,
       CASE
           WHEN lk.maksueratyyppi::TEXT = 'kokonaishintainen' THEN 'hankinta'
           ELSE 'rahavaraus'
           END AS toimenpideryhma,
       tk_tehtava.nimi         AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END                 AS toimenpide,
       lk.luotu                AS luotu,
       l.erapaiva::TEXT        AS ajankohta,
       tr.nimi                 AS toteutunut,
       tk_tehtava.jarjestys    AS jarjestys,
       'hankintakustannukset' AS paaryhma
FROM lasku_kohdistus lk
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
         JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
     toimenpideinstanssi tpi,
     toimenpidekoodi tk,
     lasku l
WHERE l.urakka = :urakka
  AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND lk.lasku = l.id
  AND lk.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  AND (tk.koodi = '23104' OR tk.koodi = '23116'
    OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
       tk.koodi = '14301' OR tk.koodi = '23151')
ORDER BY jarjestys ASC;

-- name: hae-kustannusten-seurannan-toimenpiteet
-- Toimenpiteet menee joka paikassa ristiin. Tässä tarkoitetaan kovakoodattua listaa,
-- joka poikkeaa mm. määrien toteumissa käytettävistä toimenpiteistä täysin.
SELECT id, koodi, nimi
  FROM toimenpidekoodi t
 WHERE (t.koodi = '23104'
    OR t.koodi = '23116'
    OR t.koodi = '23124'
    OR t.koodi = '20107'
    OR t.koodi = '20191'
    OR t.koodi = '14301'
    OR t.koodi = '23151');