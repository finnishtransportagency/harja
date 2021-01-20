-- name: listaa-kustannukset-paaryhmittain
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
-- Haetaan budjetoidut hankintakustannukset kustannusarvioitu-työ taulusta
SELECT kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       kt.tyyppi::TEXT                           AS maksutyyppi,
       CASE
           WHEN kt.tyyppi::TEXT = 'laskutettava-tyo' THEN 'hankinta'
           WHEN kt.tyyppi::TEXT = 'akillinen-hoitotyo' THEN 'akillinen-hoitotyo'
           WHEN kt.tyyppi::TEXT = 'vahinkojen-korjaukset' THEN 'vahinkojen-korjaukset'
           ELSE 'hankinta'
           END                                   AS toimenpideryhma,
       tk_tehtava.nimi                           AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           END                                   AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       tk_tehtava.jarjestys                      AS jarjestys,
       CASE
           WHEN kt.tyyppi::TEXT = 'laskutettava-tyo' THEN 'hankintakustannukset'
           WHEN kt.tyyppi::TEXT = 'akillinen-hoitotyo' THEN 'varaukset'
           WHEN kt.tyyppi::TEXT = 'vahinkojen-korjaukset' THEN 'varaukset'
           ELSE 'hankintakustannukset'
           END                                   AS paaryhma
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
    )
UNION ALL
-- Haetaan budjetoidut hankintakustannukset myös kiintehintainen_tyo taulusta
-- kiinteahintainen_tyo taulusta haetaan (suurin?) osa suunnitelluista kustannuksista.
-- Hinta on kiinteä, kun se on sopimuksessa sovittu, yleensä kuukausille jaettava könttäsumma.
SELECT kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
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
    )
UNION ALL
-- Budjetoidut Erillishankinnat - toimenpideinstanssi koodi = '23150'
-- Haetaan mukaan budjettiin kustannusarvioitu_työ taulusta, kun tehtäväryhmä = 'Erillishankinnat (W)'
SELECT kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
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
UNION ALL
-- Budjetoidut Hoidonjohdon palkkiot
-- toimenpideinstanssi koodi = '23150'
-- haetaan mukaan budjettiin kustannusarvioitu_työ taulusta
SELECT SUM(kt.summa)                                  AS budjetoitu_summa,
       0                                              AS toteutunut_summa,
       'kiinteahintainen'                             AS maksutyyppi,
       'hankinta'                                     AS toimenpideryhma,
       'Hoidonjohtopalkkio (G)'                       AS tehtava_nimi,
       'Hoidonjohdonpalkkio'                          AS toimenpide,
       MIN(kt.luotu)                                  AS luotu,
       MIN(concat(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'budjetointi'                                  AS toteutunut,
       0                                              AS jarjestys,
       'hoidonjohdonpalkkio'                          AS paaryhma
FROM toimenpidekoodi tk,
     kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND (kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)')
    OR kt.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744'))
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
GROUP BY tehtava_nimi
UNION ALL
-- Budjetoidut palkat haetaan johto_ja_hallintakorvaus taulusta
-- Palkat kuuluvat johto-ja-hallintakorvaus pääryhmään
SELECT SUM((hjh.tunnit * hjh.tuntipalkka))              AS budjetoitu_summa,
       0                                                AS toteutunut_summa,
       'kiinteahintainen'                               AS maksutyyppi,
       'palkat'                                         AS toimenpideryhma,
       jjht.toimenkuva                                  AS tehtava_nimi,
       'MHU Hoidonjohto'                                AS toimenpide,
       MIN(hjh.luotu)                                   AS luotu,
       MIN(concat(hjh.vuosi, '-', hjh.kuukausi, '-01')) AS ajankohta,
       'hjh'                                            AS toteutunut,
       160                                              AS jarjestys,
       'johto-ja-hallintakorvaus'                       AS paaryhma
FROM johto_ja_hallintokorvaus hjh
         LEFT JOIN johto_ja_hallintokorvaus_toimenkuva jjht on hjh."toimenkuva-id" = jjht.id
WHERE hjh."urakka-id" = :urakka
  AND (concat(hjh.vuosi, '-', hjh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
GROUP BY tehtava_nimi
UNION ALL
-- Johto- ja hallintokorvaus haetaan myös kustannusarvioitu_tyo taulusta,
-- Toimistotarvikkeet saadaan yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'
-- ja Johto- ja hallintokorvaus (J) - tehtäväryhmältä
-- ja ne tallentuu sinne.
-- Nämä on budjetoituja kustannuksia.
SELECT SUM(kt.summa)                                  AS budjetoitu_summa,
       0                                              AS toteutunut_summa,
       'kiinteahintainen'                             AS maksutyyppi,
       'toimistokulut'                                AS toimenpideryhma,
       tk_tehtava.nimi                                AS tehtava_nimi,
       'MHU Hoidonjohto'                              AS toimenpide,
       MIN(kt.luotu)                                  AS luotu,
       MIN(concat(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'hjh'                                          AS toteutunut,
       160                                            AS jarjestys,
       'johto-ja-hallintakorvaus'                     AS paaryhma
FROM toimenpidekoodi tk,
     kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = kt.tehtava,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND kt.tehtava = (SELECT id FROM toimenpidekoodi WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
GROUP BY tehtava_nimi
UNION ALL
-- Toteutuneet kustannukset haetaan lasku_kohdistus taulusta. Nämäkin on ryhmitelty vastaavasti kuten
-- budjetoidut kustannukset eli Hankintakustannukset, Johto- ja hallintokorvaus, Hoidonjohdonpalkkio sekä Erillishankinnat
-- Ensimmäisenä haetaan pelkästään Hankintakustannukset
SELECT 0                          AS budjetoitu_summa,
       SUM(lk.summa)              AS toteutunut_summa,
       lk.maksueratyyppi::TEXT    AS maksutyyppi,
       CASE
           WHEN lk.maksueratyyppi::TEXT = 'kokonaishintainen' AND tr.nimi != 'Tilaajan rahavaraus (T3)' THEN 'hankinta'
           WHEN lk.maksueratyyppi::TEXT = 'kokonaishintainen' AND tr.nimi = 'Tilaajan rahavaraus (T3)'
               THEN 'rahavaraus'
           WHEN lk.maksueratyyppi::TEXT = 'yksikkohintainen' THEN 'hankinta'
           WHEN lk.maksueratyyppi::TEXT = 'akillinen-hoitotyo' THEN 'rahavaraus'
           WHEN lk.maksueratyyppi::TEXT = 'muu' THEN 'rahavaraus' -- muu = vahinkojen-korjaukset
           WHEN lk.maksueratyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           ELSE 'hankinta'
           END                    AS toimenpideryhma,
       COALESCE(tr.nimi, tk.nimi) AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           END                    AS toimenpide,
       MIN(lk.luotu)              AS luotu,
       MIN(l.erapaiva)::TEXT      AS ajankohta,
       'toteutunut'               AS toteutunut,
       tk_tehtava.jarjestys       AS jarjestys,
       CASE
           WHEN lk.maksueratyyppi::TEXT = 'akillinen-hoitotyo' THEN 'varaukset'
           WHEN lk.maksueratyyppi::TEXT = 'muu' THEN 'varaukset' -- muu = vahinkojen-korjaukset
           WHEN lk.maksueratyyppi::TEXT = 'kokonaishintainen' AND tr.nimi = 'Tilaajan rahavaraus (T3)' THEN 'varaukset'
           ELSE 'hankintakustannukset'
           END                    AS paaryhma
FROM lasku_kohdistus lk
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
         LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
     toimenpideinstanssi tpi,
     toimenpidekoodi tk,
     lasku l
WHERE l.urakka = :urakka
  AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND l.poistettu IS NOT TRUE
  AND lk.lasku = l.id
  AND lk.toimenpideinstanssi = tpi.id
  AND lk.poistettu IS NOT TRUE
  AND tpi.toimenpide = tk.id
  -- Näillä toimenpidekoodi.koodi rajauksilla rajataan johto- ja hallintakorvaus, hoidonjohdonpalkkio ja erilliskorvaus ulos
  AND (tk.koodi = '23104' OR tk.koodi = '23116'
    OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
       tk.koodi = '14301')
GROUP BY tr.nimi, tk.nimi ,lk.maksueratyyppi,tk.koodi, tk_tehtava.jarjestys
UNION ALL
-- Toteutuneet erillishankinnat, hoidonjohdonpalkkio ja johto- ja hallintakorvaukset lasku_kohdistus taulusta.
-- Rajaus tehty toimenpidekoodi.koodi = 23151 perusteella
SELECT 0                         AS budjetoitu_summa,
       SUM(lk.summa)             AS toteutunut_summa,
       lk.maksueratyyppi::TEXT   AS maksutyyppi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'toimistokulut'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'toimistokulut'
           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           WHEN lk.tehtavaryhma IS NULL AND lk.tehtava IS NULL AND lk.maksueratyyppi::TEXT = 'lisatyo'
               THEN 'toimistokulut'
           END                   AS toimenpideryhma,
       tr.nimi                   AS tehtava_nimi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'Erillishankinnat'
           WHEN tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'Johto- ja Hallintakorvaus'
           END                   AS toimenpide,
       MIN(lk.luotu)             AS luotu,
       MIN(l.erapaiva)::TEXT     AS ajankohta,
       MIN(tr.nimi)              AS toteutunut,
       MIN(tk_tehtava.jarjestys) AS jarjestys,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'johto-ja-hallintakorvaus'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'johto-ja-hallintakorvaus'
           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           WHEN lk.tehtavaryhma IS NULL AND lk.tehtava IS NULL AND lk.maksueratyyppi::TEXT = 'lisatyo'
               THEN 'johto-ja-hallintakorvaus'
           END                   AS paaryhma
FROM lasku_kohdistus lk
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
         LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
     toimenpideinstanssi tpi,
     toimenpidekoodi tk,
     lasku l
WHERE l.urakka = :urakka
  AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND l.poistettu IS NOT TRUE
  AND lk.lasku = l.id
  AND lk.poistettu IS NOT TRUE
  AND lk.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  -- Näillä toimenpidekoodi.koodi rajauksilla rajataan Hankintakustannukset ulos
  AND tk.koodi = '23151'
GROUP BY tehtava_nimi, lk.maksueratyyppi, toimenpideryhma, toimenpide, paaryhma, tk.yksiloiva_tunniste
UNION ALL
-- Osa toteutuneista erillishankinnoista, hoidonjohdonpalkkioista ja johdon- hallintakorvauksesta
-- siirretään kustannusarvoitu_tyo taulusta toteutunut_tyo tauluun aina kuukauden viimeisenä päivänä.
-- Rajaus tehty toimenpidekoodi.koodi = 23151 perusteella
SELECT 0                                           AS budjetoitu_summa,
       SUM((SELECT korotettuna
        FROM laske_kuukauden_indeksikorotus(:hoitokauden-alkuvuosi::INTEGER, 9::INTEGER,
                                            (SELECT u.indeksi as nimi FROM urakka u WHERE u.id = :urakka)::VARCHAR,
                                            coalesce(t.summa, 0)::NUMERIC,
                                            (SELECT indeksilaskennan_perusluku(:urakka::INTEGER))::NUMERIC)))
                                                   AS toteutunut_summa,
       'kokonaishintainen'                         AS maksutyyppi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'toimistokulut'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'palkat'
           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           END                                      AS toimenpideryhma,
       coalesce(tr.nimi, tk_tehtava.nimi)           AS tehtava_nimi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'Erillishankinnat'
           WHEN tk.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'Johto- ja Hallintakorvaus'
           END                                      AS toimenpide,
       MIN(t.luotu)                                 AS luotu,
       MIN(concat(t.vuosi, '-', t.kuukausi, '-01')) AS ajankohta,
       tr.nimi                                      AS toteutunut,
       MIN(tk_tehtava.jarjestys)                    AS jarjestys,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'johto-ja-hallintakorvaus'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'johto-ja-hallintakorvaus'
           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           WHEN tk_tehtava.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744' THEN 'hoidonjohdonpalkkio'
           END                                      AS paaryhma
FROM toteutunut_tyo t
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = t.tehtava
         LEFT JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma,
     toimenpideinstanssi tpi,
     toimenpidekoodi tk
WHERE t.urakka_id = :urakka
  AND (concat(t.vuosi, '-', t.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND t.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  -- Rajataan vain hoidon johto toimenpiteeseen
  AND tk.koodi = '23151'
GROUP BY tehtava_nimi, toimenpideryhma,paaryhma, tr.nimi,  tk.yksiloiva_tunniste, tk_tehtava.yksiloiva_tunniste
UNION ALL
-- Budjetoidut bonukset eli tilaajan varaukset - Jotka tulee toimenpideinstanssille, joka saadaan, kun käytetään
-- toimenpidekoodia 23150
SELECT SUM(kt.summa)                                  AS budjetoitu_summa,
       0                                              AS toteutunut_summa,
       MIN(kt.tyyppi)::TEXT                           AS maksutyyppi,
       'bonus'                                        AS toimenpideryhma,
       'Tilaajan varaus'                              AS tehtava_nimi,
       'MHU Hoidonjohto'                              AS toimenpide,
       MIN(kt.luotu)                                  AS luotu,
       MIN(concat(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'budjetointi'                                  AS toteutunut,
       0                                              AS jarjestys,
       'bonukset'                                     AS paaryhma
FROM kustannusarvioitu_tyo kt,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND kt.tehtava IS NULL
  -- Tämä kovakoodattu tehtäväryhmä on nimeltään - Johto- ja hallintokorvaus (J). Se on päätetty
  -- tulkita Bonuksien alle tulevaksi Tilaajan varaukseksi Kustannusten suunnittelu sivulla, koska sen toimenpideinstanssin
  -- id on 23150.
  AND kt.tehtavaryhma = (select id from tehtavaryhma tr where tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54')
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
GROUP BY tehtava_nimi
UNION ALL
-- Toteutuneet erilliskustannukset eli bonukset
-- Toteutuneita bonuksia voidaan lisätä erilliskustannusnäytötä ja ne menee erilliskustannuksiksi
-- Tässä ei ole siis mukana kustannusarvoitu_tyo tauluun tallennetut "Tilaajan varaukset" jotka pohjimmiltaan on
-- budjetoituja bonuksia ja jotka haetaan sitten erikseen toteutunut_tyo taulusta, koska sinne siirretään kaikki toteutuneet
-- kustannusarvoidut_työt
SELECT 0                    AS budjetoitu_summa,
       CASE
           WHEN ek.tyyppi::TEXT = 'lupausbonus' OR ek.tyyppi::TEXT = 'asiakastyytyvaisyysbonus'
               THEN SUM((SELECT korotettuna
                     FROM laske_kuukauden_indeksikorotus(:hoitokauden-alkuvuosi::INTEGER, 9::INTEGER,
                                                         (SELECT u.indeksi as nimi FROM urakka u WHERE u.id = :urakka)::VARCHAR,
                                                         coalesce(ek.rahasumma, 0)::NUMERIC,
                                                         (SELECT indeksilaskennan_perusluku(:urakka::INTEGER))::NUMERIC)))
           ELSE SUM(ek.rahasumma)
           END              AS toteutunut_summa,
       'bonus'              AS maksutyyppi,
       'bonus'              AS toimenpideryhma,
       MIN(ek.tyyppi)::TEXT AS tehtava_nimi,
       'bonus'              AS toimenpide,
       MIN(ek.luotu)        AS luotu,
       MIN(ek.pvm)::TEXT    AS ajankohta,
       'bonus'              as toteutunut,
       0                    AS jarjestys,
       'bonukset'           AS paaryhma
FROM erilliskustannus ek,
     sopimus s
WHERE s.urakka = :urakka
  AND ek.sopimus = s.id
  AND ek.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND ek.pvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND ek.poistettu IS NOT TRUE
GROUP BY ek.tyyppi
ORDER BY jarjestys ASC, ajankohta asc;
