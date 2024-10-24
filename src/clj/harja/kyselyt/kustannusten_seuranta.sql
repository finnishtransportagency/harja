-- name: listaa-kustannukset-paaryhmittain
-- Listaa kustannusten seurantaa varten tehtävien toteutuneet ja budjetoidut kustannukset.
-- Haetaan ensin urakan toimenpideinstanssi-id hoidonjohdolle
WITH urakan_toimenpideinstanssi_23150 AS
         (SELECT tpi.id AS id
          FROM toimenpideinstanssi tpi
                   JOIN toimenpide tpk3 ON tpk3.id = tpi.toimenpide
                   JOIN toimenpide tpk2 ON tpk3.emo = tpk2.id,
               maksuera m
          WHERE tpi.urakka = :urakka
            AND m.toimenpideinstanssi = tpi.id
            AND tpk2.koodi = '23150'
          limit 1)
-- Haetaan budjetoidut hankintakustannukset ja rahavaraukset kustannusarvioitu_tyo taulusta
-- Kaikki budjetoidut kustannukset ovat joko rahavarauksia tai hankintoja. Erillishankinnat on eriytetty omaksi haukseen
SELECT COALESCE(SUM(kt.summa), 0)                     AS budjetoitu_summa,
       COALESCE(SUM(kt.summa_indeksikorjattu), 0)     AS budjetoitu_summa_indeksikorjattu,
       0                                              AS toteutunut_summa,
       kt.tyyppi::TEXT                                AS maksutyyppi,
       'hankinta'                                     AS toimenpideryhma,
       COALESCE(tr.nimi, tk_tehtava.nimi)             AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           END                                        AS toimenpide,
       MIN(CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'budjetointi'                                  AS toteutunut,
       tk_tehtava.jarjestys                           AS jarjestys,
       'hankintakustannukset'                         AS paaryhma,
       kt.indeksikorjaus_vahvistettu                  AS indeksikorjaus_vahvistettu
FROM toimenpide tk,
     kustannusarvioitu_tyo kt
         LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = kt.tehtava
         LEFT JOIN tehtavaryhma tr ON tk_tehtava.tehtavaryhma = tr.id
         LEFT JOIN rahavaraus_urakka ru
             ON kt.rahavaraus_id = ru.rahavaraus_id
                AND ru.urakka_id = :urakka
         LEFT JOIN rahavaraus r ON kt.rahavaraus_id = r.id,
     toimenpideinstanssi tpi,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  -- Jätetään rahavaraukset pois
  AND kt.rahavaraus_id IS NULL
  AND kt.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  AND (tk.koodi = '23104' -- talvihoito
    OR tk.koodi = '23116' -- liikenneympariston-hoito
    OR tk.koodi = '23124' -- sorateiden-hoito
    OR tk.koodi = '20107' -- paallystepaikkaukset
    OR tk.koodi = '20191' -- mhu-yllapito
    OR tk.koodi = '14301' -- mhu-korvausinvestointi
    )
GROUP BY paaryhma, toimenpide, toimenpideryhma, maksutyyppi, tehtava_nimi, tk.koodi,
         tk_tehtava.jarjestys, tr.nimi, kt.indeksikorjaus_vahvistettu

 UNION ALL
-- Haetaan budjetoidut rahavaraukset erikseen, koska niillä ei ole toimenpideinstanssia
SELECT COALESCE(SUM(kt.summa), 0)                     AS budjetoitu_summa,
       COALESCE(SUM(kt.summa_indeksikorjattu), 0)     AS budjetoitu_summa_indeksikorjattu,
       0                                              AS toteutunut_summa,
       kt.tyyppi::TEXT                                AS maksutyyppi,
       'rahavaraus'                                   AS toimenpideryhma,
       COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi)      AS tehtava_nimi,
       COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi)      AS toimenpide,
       MIN(CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'budjetointi'                                  AS toteutunut,
       r.jarjestys                                    AS jarjestys,
       'rahavaraukset'                                AS paaryhma,
       kt.indeksikorjaus_vahvistettu                  AS indeksikorjaus_vahvistettu
  FROM kustannusarvioitu_tyo kt
           LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = kt.tehtava
           LEFT JOIN tehtavaryhma tr ON tk_tehtava.tehtavaryhma = tr.id
           LEFT JOIN rahavaraus_urakka ru
                     ON kt.rahavaraus_id = ru.rahavaraus_id
                         AND ru.urakka_id = :urakka
           LEFT JOIN rahavaraus r ON kt.rahavaraus_id = r.id,
       sopimus s
 WHERE s.urakka = :urakka
   AND kt.sopimus = s.id
   AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
   AND kt.rahavaraus_id IS NOT NULL
 GROUP BY paaryhma, toimenpide, toimenpideryhma, maksutyyppi, tehtava_nimi,
          tk_tehtava.jarjestys, tr.nimi, kt.indeksikorjaus_vahvistettu, r.jarjestys

UNION ALL
-- Haetaan budjetoidut hankintakustannukset myös kiintehintainen_tyo taulusta
-- kiinteahintainen_tyo taulusta haetaan (suurin?) osa suunnitelluista kustannuksista.
-- Hinta on kiinteä, kun se on sopimuksessa sovittu, yleensä kuukausille jaettava könttäsumma.
SELECT kt.summa                                  AS budjetoitu_summa,
       kt.summa_indeksikorjattu                  AS budjetoitu_summa_indeksikorjattu,
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
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       tk_tehtava.jarjestys                      AS jarjestys,
       'hankintakustannukset'                    AS paaryhma,
       kt.indeksikorjaus_vahvistettu             AS indeksikorjaus_vahvistettu
from toimenpide tk,
     kiinteahintainen_tyo kt
         LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = kt.tehtava,
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
       kt.summa_indeksikorjattu                  AS budjetoitu_summa_indeksikorjattu,
       0                                         AS toteutunut_summa,
       'kiinteahintainen'                        AS maksutyyppi,
       'hankinta'                                AS toimenpideryhma,
       'Erillishankinnat (W)'                    AS tehtava_nimi,
       'Erillishankinnat'                        AS toimenpide,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'budjetointi'                             AS toteutunut,
       0                                         AS jarjestys,
       'erillishankinnat'                        AS paaryhma,
       kt.indeksikorjaus_vahvistettu             AS indeksikorjaus_vahvistettu
from toimenpide tk,
     kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND kt.tehtavaryhma = (SELECT id
                         FROM tehtavaryhma
                         WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat (W)
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
UNION ALL
-- Budjetoidut Hoidonjohdon palkkiot
-- toimenpideinstanssi koodi = '23150'
-- haetaan mukaan budjettiin kustannusarvioitu_työ taulusta
SELECT SUM(kt.summa)                                  AS budjetoitu_summa,
       SUM(kt.summa_indeksikorjattu)                  AS budjetoitu_summa_indeksikorjattu,
       0                                              AS toteutunut_summa,
       'kiinteahintainen'                             AS maksutyyppi,
       'hankinta'                                     AS toimenpideryhma,
       'Hoidonjohtopalkkio (G)'                       AS tehtava_nimi,
       'Hoidonjohdonpalkkio'                          AS toimenpide,
       MIN(concat(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'budjetointi'                                  AS toteutunut,
       0                                              AS jarjestys,
       'hoidonjohdonpalkkio'                          AS paaryhma,
       kt.indeksikorjaus_vahvistettu                  AS indeksikorjaus_vahvistettu
from toimenpide tk,
     kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND (kt.tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Hoidonjohtopalkkio (G)')
    OR kt.tehtava = (SELECT id
                     from tehtava
                     WHERE yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744') -- Hoitourakan työnjohto
    OR kt.tehtava = (SELECT id
                     from tehtava
                     WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8')) -- Hoidonjohtopalkkio
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
GROUP BY tehtava_nimi, indeksikorjaus_vahvistettu
UNION ALL
-- Budjetoidut palkat haetaan johto_ja_hallintokorvaus taulusta
-- Palkat kuuluvat johto-ja-hallintokorvaus pääryhmään
SELECT SUM((hjh.tunnit * hjh.tuntipalkka * hjh."osa-kuukaudesta")) AS budjetoitu_summa,
       SUM((hjh.tunnit *
            hjh.tuntipalkka_indeksikorjattu *
            hjh."osa-kuukaudesta"))                                AS budjetoitu_summa_indeksikorjattu,
       0                                                           AS toteutunut_summa,
       'kiinteahintainen'                                          AS maksutyyppi,
       'hankinta'                                                  AS toimenpideryhma,
       jjht.toimenkuva                                             AS tehtava_nimi,
       'MHU Hoidonjohto'                                           AS toimenpide,
       MIN(concat(hjh.vuosi, '-', hjh.kuukausi, '-01'))            AS ajankohta,
       'hjh'                                                       AS toteutunut,
       160                                                         AS jarjestys,
       'johto-ja-hallintokorvaus'                                  AS paaryhma,
       hjh.indeksikorjaus_vahvistettu                              AS indeksikorjaus_vahvistettu
FROM johto_ja_hallintokorvaus hjh
         LEFT JOIN johto_ja_hallintokorvaus_toimenkuva jjht on hjh."toimenkuva-id" = jjht.id
WHERE hjh."urakka-id" = :urakka
  AND (concat(hjh.vuosi, '-', hjh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
GROUP BY tehtava_nimi, indeksikorjaus_vahvistettu
UNION ALL
-- Budjetoidut - Johto- ja hallintokorvaus haetaan myös kustannusarvioitu_tyo taulusta,
-- Toimistotarvikkeet saadaan yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'
-- ja Johto- ja hallintokorvaus (J) - tehtäväryhmältä
SELECT SUM(kt.summa)                                  AS budjetoitu_summa,
       SUM(kt.summa_indeksikorjattu)                  AS budjetoitu_summa_indeksikorjattu,
       0                                              AS toteutunut_summa,
       'kiinteahintainen'                             AS maksutyyppi,
       'hankinta'                                     AS toimenpideryhma,
       tk_tehtava.nimi                                AS tehtava_nimi,
       'MHU Hoidonjohto'                              AS toimenpide,
       MIN(concat(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'hjh'                                          AS toteutunut,
       160                                            AS jarjestys,
       'johto-ja-hallintokorvaus'                     AS paaryhma,
       kt.indeksikorjaus_vahvistettu                  AS indeksikorjaus_vahvistettu
from toimenpide tk,
     kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
         LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = kt.tehtava,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND kt.tehtava = (SELECT id from tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388')
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
GROUP BY tehtava_nimi, indeksikorjaus_vahvistettu
UNION ALL
-- Toteutuneet kustannukset haetaan kulu_kohdistus taulusta. Nämäkin on ryhmitelty vastaavasti kuten
-- budjetoidut kustannukset eli Hankintakustannukset, Johto- ja hallintokorvaus, Hoidonjohdonpalkkio sekä Erillishankinnat
-- Jos tehtävä on merkattu rahavaraukseksi, niin laitetaan se rahavarausten alle
SELECT 0                          AS budjetoitu_summa,
       0                          AS budjetoitu_summa_indeksikorjattu,
       coalesce(SUM(lk.summa), 0) AS toteutunut_summa,
       lk.maksueratyyppi::TEXT    AS maksutyyppi,
       CASE
           WHEN lk.tyyppi::TEXT = 'hankintakulu' THEN 'hankinta'
           WHEN lk.tyyppi::TEXT = 'muukulu' THEN 'muukulu'
           WHEN lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           WHEN lk.tyyppi::TEXT = 'rahavaraus' THEN 'rahavaraus'
           ELSE 'hankinta'
           END                    AS toimenpideryhma,
       COALESCE(tr.nimi, tk.nimi) AS tehtava_nimi,
       CASE
           WHEN (tk.koodi = '23104' AND lk.rahavaraus_id IS NULL) THEN 'Talvihoito'
           WHEN (tk.koodi = '23116' AND lk.rahavaraus_id IS NULL) THEN 'Liikenneympäristön hoito'
           WHEN (tk.koodi = '23124' AND lk.rahavaraus_id IS NULL) THEN 'Sorateiden hoito'
           WHEN (tk.koodi = '20107' AND lk.rahavaraus_id IS NULL) THEN 'Päällystepaikkaukset'
           WHEN (tk.koodi = '20191' AND lk.rahavaraus_id IS NULL) THEN 'MHU Ylläpito'
           WHEN (tk.koodi = '14301' AND lk.rahavaraus_id IS NULL) THEN 'MHU Korvausinvestointi'
           WHEN lk.rahavaraus_id IS NOT NULL THEN COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi)
           END                    AS toimenpide,
       MIN(l.erapaiva)::TEXT      AS ajankohta,
       'toteutunut'               AS toteutunut,
       tr.jarjestys               AS jarjestys,
       CASE
           WHEN lk.tyyppi::TEXT = 'rahavaraus' THEN 'rahavaraukset'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS TRUE) THEN 'muukulu-tavoitehintainen'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS FALSE) THEN 'muukulu-eitavoitehintainen'
           WHEN lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           ELSE 'hankintakustannukset'
           END                    AS paaryhma,
       NOW()                      AS indeksikorjaus_vahvistettu -- kuluja ei indeksivahvisteta, joten ne on aina "true"
FROM kulu_kohdistus lk
         LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma
         LEFT JOIN rahavaraus_urakka ru
                    ON lk.rahavaraus_id = ru.rahavaraus_id
                    AND ru.urakka_id = :urakka
         LEFT JOIN rahavaraus r ON lk.rahavaraus_id = r.id,
     toimenpideinstanssi tpi,
     toimenpide tk,
     kulu l
WHERE l.urakka = :urakka
  AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND l.poistettu IS NOT TRUE
  AND lk.kulu = l.id
  AND lk.toimenpideinstanssi = tpi.id
  AND lk.poistettu IS NOT TRUE
  AND tpi.toimenpide = tk.id
  -- Näillä toimenpidekoodi.koodi rajauksilla rajataan johto- ja hallintokorvaus, hoidonjohdonpalkkio ja erilliskorvaus ulos
  AND (tk.koodi = '23104' OR tk.koodi = '23116'
    OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
       tk.koodi = '14301')
GROUP BY tr.nimi, tk.nimi, lk.tyyppi, lk.maksueratyyppi, tk.koodi, tr.jarjestys, tr.yksiloiva_tunniste,
         lk.rahavaraus_id, COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi), lk.tavoitehintainen
UNION ALL
-- Toteutuneet erillishankinnat, hoidonjohdonpalkkio, johto- ja hallintokorvaukset
-- ja vuoden päättämiseen liittyvät kulut kulu_kohdistus taulusta.
-- Rajaus tehty toimenpidekoodi.koodi = 23151 perusteella
SELECT 0                          AS budjetoitu_summa,
       0                          AS budjetoitu_summa_indeksikorjattu,
       coalesce(SUM(lk.summa), 0) AS toteutunut_summa,
       lk.maksueratyyppi::TEXT    AS maksutyyppi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'hankinta'
           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           WHEN lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           WHEN lk.tyyppi::TEXT = 'muukulu' THEN 'muukulu'
           WHEN tr.yksiloiva_tunniste IN ('55c920e7-5656-4bb0-8437-1999add714a3',
                                           '19907c24-dd26-460f-9cb4-2ed974b891aa',
                                           'be34116b-2264-43e0-8ac8-3762b27a9557')
               THEN 'Hoitokauden päättäminen'
           END                   AS toimenpideryhma,
       CASE
           WHEN lk.tehtavaryhma IS NULL AND lk.tyyppi::TEXT = 'lisatyo' THEN tk.nimi
           WHEN lk.tehtavaryhma IS NULL AND lk.tyyppi::TEXT = 'muukulu' THEN tk.nimi
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'Johto- ja hallintokorvaus (käsin kirjattu)'
           ELSE tr.nimi
           END                   AS tehtava_nimi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'Erillishankinnat'
           WHEN tr.yksiloiva_tunniste = '55c920e7-5656-4bb0-8437-1999add714a3' THEN 'Tavoitepalkkio'
           WHEN tr.yksiloiva_tunniste = '19907c24-dd26-460f-9cb4-2ed974b891aa' THEN 'Urakoitsija maksaa tavoitehinnan ylityksestä'
           WHEN tr.yksiloiva_tunniste = 'be34116b-2264-43e0-8ac8-3762b27a9557' THEN 'Urakoitsija maksaa kattohinnan ylityksestä'
           else 'Johto- ja Hallintokorvaus'
           END                   AS toimenpide,
       MIN(l.erapaiva)::TEXT     AS ajankohta,
       'toteutunut'              AS toteutunut,
       MIN(tr.jarjestys) AS jarjestys,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'johto-ja-hallintokorvaus'
           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           WHEN lk.tehtavaryhma IS NULL AND lk.tyyppi::TEXT = 'lisatyo' THEN 'lisatyo'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS TRUE) THEN 'muukulu-tavoitehintainen'
           WHEN (lk.tyyppi::TEXT = 'muukulu' AND lk.tavoitehintainen IS FALSE) THEN 'muukulu-eitavoitehintainen'
           WHEN tr.yksiloiva_tunniste = '55c920e7-5656-4bb0-8437-1999add714a3' THEN 'tavoitepalkkio'
           WHEN tr.yksiloiva_tunniste = '19907c24-dd26-460f-9cb4-2ed974b891aa' THEN 'tavoitehinnan-ylitys'
           WHEN tr.yksiloiva_tunniste = 'be34116b-2264-43e0-8ac8-3762b27a9557' THEN 'kattohinnan-ylitys'
           END                   AS paaryhma,
       NOW()                     AS indeksikorjaus_vahvistettu -- kuluja ei indeksivahvisteta, joten ne on aina "true"
FROM kulu_kohdistus lk
     LEFT JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
     toimenpideinstanssi tpi,
     toimenpide tk,
     kulu l
WHERE l.urakka = :urakka
  AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND l.poistettu IS NOT TRUE
  AND lk.kulu = l.id
  AND lk.poistettu IS NOT TRUE
  AND lk.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  -- Näillä toimenpidekoodi.koodi rajauksilla rajataan Hankintakustannukset ulos
  AND tk.koodi = '23151'
GROUP BY tehtava_nimi, tr.nimi, tr.yksiloiva_tunniste, lk.tyyppi, lk.maksueratyyppi, toimenpideryhma, toimenpide, paaryhma
UNION ALL
-- Osa toteutuneista erillishankinnoista, hoidonjohdonpalkkioista ja johdon- hallintakorvauksesta
-- siirretään kustannusarvoitu_tyo taulusta toteutuneet_kustannukset tauluun aina kuukauden viimeisenä päivänä.
-- Rajaus tehty toimenpidekoodi.koodi = 23151 perusteella
SELECT 0                                            AS budjetoitu_summa,
       0                                            AS budjetoitu_summa_indeksikorjattu,
       coalesce(SUM(t.summa_indeksikorjattu), 0)    AS toteutunut_summa,
       'kokonaishintainen'                          AS maksutyyppi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'hankinta'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'hankinta'
           WHEN tk_tehtava.yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8' then 'hoidonjohdonpalkkio'
--           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           END                                      AS toimenpideryhma,
       coalesce(tr.nimi, tk_tehtava.nimi)           AS tehtava_nimi,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'Erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'Johto- ja Hallintokorvaus'
           END                                      AS toimenpide,
       MIN(concat(t.vuosi, '-', t.kuukausi, '-01')) AS ajankohta,
       'toteutunut'                                      AS toteutunut,
       MIN(tk_tehtava.jarjestys)                    AS jarjestys,
       CASE
           WHEN tr.nimi = 'Erillishankinnat (W)' THEN 'erillishankinnat'
           WHEN tk_tehtava.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' THEN 'johto-ja-hallintokorvaus'
           WHEN tr.nimi = 'Johto- ja hallintokorvaus (J)' THEN 'johto-ja-hallintokorvaus'
           WHEN tr.nimi = 'Hoidonjohtopalkkio (G)' THEN 'hoidonjohdonpalkkio'
           WHEN tk_tehtava.yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8' then 'hoidonjohdonpalkkio' --'c9712637-fbec-4fbd-ac13-620b5619c744' THEN 'hoidonjohdonpalkkio'
           END                                      AS paaryhma,
       t.indeksikorjaus_vahvistettu                 AS indeksikorjaus_vahvistettu
    FROM toteutuneet_kustannukset t
         LEFT JOIN tehtava tk_tehtava ON tk_tehtava.id = t.tehtava
         LEFT JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma,
     toimenpideinstanssi tpi,
     toimenpide tk
WHERE t.urakka_id = :urakka
  AND (concat(t.vuosi, '-', t.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND t.toimenpideinstanssi = tpi.id
  AND tpi.toimenpide = tk.id
  -- Rajataan vain hoidon johto toimenpiteeseen
  AND tk.koodi = '23151'
GROUP BY tehtava_nimi, toimenpideryhma, paaryhma, tr.nimi, tk_tehtava.yksiloiva_tunniste, indeksikorjaus_vahvistettu
UNION ALL
-- Budjetoidut bonukset eli tilaajan rahavaraukset - Jotka tulee toimenpideinstanssille, joka saadaan, kun käytetään
-- toimenpidekoodia 23150
-- Nämä rahavaraukset ovat ainoa poikkeus uudistuneeseen rahavarausjärjestelmään.
SELECT SUM(kt.summa)                                  AS budjetoitu_summa,
       SUM(kt.summa)                                  AS budjetoitu_summa_indeksikorjattu, -- Näitä ei indeksikorjata
       0                                              AS toteutunut_summa,
       MIN(kt.tyyppi)::TEXT                           AS maksutyyppi,
       'bonus'                                        AS toimenpideryhma,
       'Tilaajan varaus'                              AS tehtava_nimi,
       'Tavoitehinnan ulkopuoliset rahavaraukset'     AS toimenpide,
       MIN(concat(kt.vuosi, '-', kt.kuukausi, '-01')) AS ajankohta,
       'budjetointi'                                  AS toteutunut,
       0                                              AS jarjestys,
       'ulkopuoliset-rahavaraukset'                   AS paaryhma,
       NOW()                                          AS indeksikorjaus_vahvistettu -- bonukset eli tavoitehinnan ulkopuolisia rahavarauksia ei vahvisteta
FROM kustannusarvioitu_tyo kt,
     sopimus s
WHERE s.urakka = :urakka
  AND kt.toimenpideinstanssi = (select id from urakan_toimenpideinstanssi_23150)
  AND kt.tehtava IS NULL
  -- Tämä kovakoodattu tehtäväryhmä on nimeltään - Johto- ja hallintokorvaus (J). Se on päätetty
  -- tulkita Bonuksien alle tulevaksi Tilaajan varaukseksi Kustannusten suunnittelu sivulla, koska sen toimenpideinstanssin
  -- id on 23150.
  -- Tehtäväryhmä: Johto- ja hallintokorvaus (J) = 'a6614475-1950-4a61-82c6-fda0fd19bb54'
  AND kt.tehtavaryhma =
      (select id from tehtavaryhma tr where tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54')
  AND kt.sopimus = s.id
  AND (concat(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
GROUP BY tehtava_nimi, indeksikorjaus_vahvistettu
UNION ALL
-- Toteutuneet erilliskustannukset eli bonukset
-- Toteutuneita bonuksia voidaan lisätä erilliskustannusnäytötä ja ne menee erilliskustannuksiksi
-- Tässä ei ole siis mukana kustannusarvoitu_tyo tauluun tallennetut "Tilaajan rahavaraukset" jotka pohjimmiltaan on
-- budjetoituja bonuksia ja jotka haetaan sitten erikseen toteutuneet_kustannukset taulusta, koska sinne siirretään kaikki toteutuneet
-- kustannusarvoidut_työt
SELECT 0                        AS budjetoitu_summa,
       0                        AS budjetoitu_summa_indeksikorjattu,
       SUM((SELECT korotettuna
              FROM erilliskustannuksen_indeksilaskenta(ek.laskutuskuukausi, ek.indeksin_nimi,
                 ek.rahasumma,ek.urakka , ek.tyyppi,
CASE WHEN u.tyyppi = 'teiden-hoito'::urakkatyyppi THEN TRUE ELSE FALSE END))) AS toteutunut_summa,
       CASE
           WHEN ek.tyyppi = 'lupausbonus' THEN 'lupausbonus'
           WHEN ek.tyyppi = 'alihankintabonus' THEN 'alihankintabonus'
           ELSE 'bonus' END     AS maksutyyppi,
       'bonus'                  AS toimenpideryhma,
       MIN(ek.tyyppi)::TEXT     AS tehtava_nimi,
       'bonukset'               AS toimenpide,
       MIN(ek.pvm)::TEXT        AS ajankohta,
       'bonus'                  AS toteutunut,
       0                        AS jarjestys,
       'bonukset'               AS paaryhma,
       NOW()                    AS indeksikorjaus_vahvistettu -- erilliskustannuksia ei indeksivahvisteta, joten ne on aina "true"
FROM erilliskustannus ek
     JOIN urakka u ON ek.urakka = u.id,
     sopimus s
WHERE s.urakka = :urakka
  AND ek.sopimus = s.id
  AND ek.laskutuskuukausi BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND ek.poistettu IS NOT TRUE
GROUP BY ek.tyyppi, ek.indeksin_nimi
UNION ALL
-- Sanktiot -- sanktiot taulusta. Lisätään sanktioille indeksi totetuneeseen summaan, mikäli indeksi on asetettu
SELECT 0                       AS budjetoitu_summa,
       0                       AS budjetoitu_summa_indeksikorjattu,
       CASE
           WHEN s.indeksi IS NULL THEN SUM(s.maara) * -1
           ELSE
                    SUM(s.maara + (SELECT korotus
                    FROM sanktion_indeksikorotus(s.perintapvm, s.indeksi,s.maara, :urakka::INTEGER, s.sakkoryhma)))
                    * -1
           END                 AS toteutunut_summa,
       CASE
           WHEN s.sakkoryhma = 'lupaussanktio' THEN 'lupaussanktio'
           ELSE 'sanktio' END  AS maksutyyppi,
       'sanktio'               AS toimenpideryhma,
       MIN(st.nimi)::TEXT      AS tehtava_nimi,
       'sanktiot'              AS toimenpide,
       MIN(s.perintapvm)::TEXT AS ajankohta,
       'sanktio'               AS toteutunut,
       0                       AS jarjestys,
       'sanktiot'              AS paaryhma,
       NOW()                   AS indeksikorjaus_vahvistettu -- sanktioita ei indeksivahvisteta, joten ne on aina "true"
FROM sanktio s
     JOIN toimenpideinstanssi tpi ON tpi.urakka = :urakka AND tpi.id = s.toimenpideinstanssi
     JOIN sanktiotyyppi st ON s.tyyppi = st.id
WHERE s.perintapvm BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND s.poistettu IS NOT TRUE
GROUP BY s.tyyppi, s.indeksi, s.sakkoryhma
-- Urakan päätös-taulusta haetaan toteutumiin edellisen vuoden siirrot.
UNION ALL
SELECT 0                                          AS budjetoitu_summa,
       0                                          AS budjetoitu_summa_indeksikorjattu,
       coalesce(SUM(up.siirto), 0)                AS toteutunut_summa,
       'siirto'                                   AS maksutyyppi,
       'siirto'                                   AS toimenpideryhma,
       'Kustannusten siirto edelliseltä vuodelta' AS tehtava_nimi,
       'Siirto'                                   AS toimenpide,
       DATE(MAX(up.muokattu))::TEXT               AS ajankohta,
       'siirto'                                   AS toteutunut,
       0                                          AS jarjestys,
       'siirto'                                   AS paaryhma,
       NOW()                                      AS indeksikorjaus_vahvistettu -- urakan päätöksia ei indeksivahvisteta, joten ne on aina "true"
FROM urakka_paatos up
WHERE up."urakka-id" = :urakka
  AND up."hoitokauden-alkuvuosi" + 1 = :hoitokauden-alkuvuosi::INTEGER
  AND up.siirto != 0
  AND up.poistettu = FALSE
GROUP BY up.tyyppi, up."hoitokauden-alkuvuosi"
-- Urakan päätös-taulusta haetaan myös budjetit hoitokauden päättymiseen liittyvistä kuluista
UNION ALL
SELECT CASE
           WHEN up.tyyppi = 'tavoitehinnan-alitus'
               -- Urakoitsijan maksu on negatiivinen kun saadaan tavoitepalkkiota
               THEN COALESCE(SUM(up."urakoitsijan-maksu"), 0) * -1
           ELSE SUM(up."urakoitsijan-maksu")
           END                      AS budjetoitu_summa,
       0                            AS budjetoitu_summa_indeksikorjattu,
       0                            AS toteutunut_summa,
       'kokonaishintainen'          AS maksutyyppi,
       'Hoitokauden päättäminen'    AS toimenpideryhma,
       CASE
           WHEN up.tyyppi = 'tavoitehinnan-alitus'::paatoksen_tyyppi
               THEN 'Hoitovuoden päättäminen / Tavoitepalkkio'
           WHEN up.tyyppi = 'tavoitehinnan-ylitys'::paatoksen_tyyppi
               THEN 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä'
           WHEN up.tyyppi = 'kattohinnan-ylitys'::paatoksen_tyyppi
               THEN 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä'
           END                      AS tehtava_nimi,
       CASE
           WHEN up.tyyppi = 'tavoitehinnan-alitus'::paatoksen_tyyppi
               THEN 'Tavoitepalkkio'
           WHEN up.tyyppi = 'tavoitehinnan-ylitys'::paatoksen_tyyppi
               THEN 'Urakoitsija maksaa tavoitehinnan ylityksestä'
           WHEN up.tyyppi = 'kattohinnan-ylitys'::paatoksen_tyyppi
               THEN 'Urakoitsija maksaa kattohinnan ylityksestä'
           END                      AS toimenpide,
       DATE(MAX(up.muokattu))::TEXT AS ajankohta,
       'budjetointi'                AS toteutunut,
       0                            AS jarjestys,
       CASE
           WHEN up.tyyppi = 'tavoitehinnan-alitus'::paatoksen_tyyppi
               THEN 'tavoitepalkkio'
           WHEN up.tyyppi = 'tavoitehinnan-ylitys'::paatoksen_tyyppi
               THEN 'tavoitehinnan-ylitys'
           WHEN up.tyyppi = 'kattohinnan-ylitys'::paatoksen_tyyppi
               THEN 'kattohinnan-ylitys'
           END                      AS paaryhma,
       NOW()                        AS indeksikorjaus_vahvistettu -- urakan päätöksia ei indeksivahvisteta, joten ne on aina "true"
FROM urakka_paatos up
WHERE up."urakka-id" = :urakka
  AND up."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi::INTEGER
  AND up.poistettu = FALSE
  -- Vain tavoitepalkkio ja tavoite- tai kattohinnan ylitys näytetään kustannusten seurannassa
  AND up.tyyppi IN ('tavoitehinnan-alitus', 'tavoitehinnan-ylitys', 'kattohinnan-ylitys')
GROUP BY up.tyyppi, up."hoitokauden-alkuvuosi"
-- Tavoitehinnan oikaisut vaikuttavat tavoitehinnan oikaisu -pääryhmään ja ne merkitään budjetti sarakkeeseen.
UNION ALL
SELECT SUM(toik.summa)                AS budjetoitu_summa,
       SUM(toik.summa)                AS budjetoitu_summa_indeksikorjattu,
       0                              AS toteutunut_summa,
       'tavoitehinnanoikaisu'         AS maksutyyppi,
       'tavoitehinnanoikaisu'         AS toimenpideryhma,
       MIN(toik.otsikko)              AS tehtava_nimi,
       MIN(toik.otsikko)              AS toimenpide,
       DATE(MAX(toik.muokattu))::TEXT AS ajankohta,
       'tavoitehinnanoikaisu'         as toteutunut,
       0                              AS jarjestys,
       'tavoitehinnanoikaisu'         AS paaryhma,
       null                           AS indeksikorjaus_vahvistettu
FROM tavoitehinnan_oikaisu toik
WHERE toik."urakka-id" = :urakka
  AND toik."hoitokauden-alkuvuosi" = :hoitokauden-alkuvuosi::INTEGER
  AND toik.poistettu = FALSE
GROUP BY toik.otsikko
ORDER BY jarjestys ASC, ajankohta asc;
