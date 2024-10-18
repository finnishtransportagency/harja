-- name: hae-urakan-kustannusarvioidut-tyot-nimineen
-- Haetaan kustannusarvioidut työt, joissa ei ole rahavarauksia mukana. Eli talvihoidon, liikenneympäristön hoidon, sorateiden hoidon yms.
SELECT kat.id,
       kat.vuosi,
       kat.kuukausi,
       kat.summa,
       kat.summa_indeksikorjattu AS "summa-indeksikorjattu",
       kat.tyyppi ::TOTEUMATYYPPI,
       tpik_t.yksiloiva_tunniste AS "tehtavan-tunniste",
       tr.yksiloiva_tunniste AS "tehtavaryhman-tunniste",
       tpik_tpi.koodi AS "toimenpiteen-koodi",
       kat.sopimus,
       kat.indeksikorjaus_vahvistettu AS "indeksikorjaus-vahvistettu"
  FROM kustannusarvioitu_tyo kat
           LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
           LEFT JOIN toimenpide tpik_tpi ON tpik_tpi.id = tpi.toimenpide
           LEFT JOIN tehtava tpik_t ON tpik_t.id = kat.tehtava
           LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
 WHERE tpi.urakka = :urakka
   -- Jätetään rahavaraukset pois hausta
   AND kat.rahavaraus_id IS NULL;


-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee teiden hoidon urakan (MHU) kustannussuunnitelmat likaiseksi urakkakohtaisen toimenpideinstanssin ja maksuerätyypin mukaan
UPDATE kustannussuunnitelma
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                               JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                   WHERE tpi.id = :toimenpideinstanssi AND tpi.loppupvm > current_timestamp - INTERVAL '3 months');

-- name: merkitse-maksuerat-likaisiksi!
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE toimenpideinstanssi IN (SELECT id
                              FROM toimenpideinstanssi
                              WHERE id = :toimenpideinstanssi
                                AND loppupvm > current_timestamp - INTERVAL '3 months');

-- name: hae-rahavarauskustannus
-- Haetaan yksittäinen urakan rahavaraus
SELECT kt.id, kt.vuosi, kt.kuukausi, kt.summa, kt.summa_indeksikorjattu
  FROM kustannusarvioitu_tyo kt
 WHERE kt.rahavaraus_id = :rahavaraus_id
   AND ((kt.vuosi = :vuosi AND kt.kuukausi >= 10) OR (kt.vuosi = :vuosi + 1 AND kt.kuukausi <= 9))
   AND kt.sopimus = :sopimus_id;

-- name: hae-tavoitehinnan-ulkopuolinen-rahavarauskustannus
-- Haetaan tavoitehinnan ulkopuolinen rahavarauskustannus, joka on pohjimmiltaan sovittu, että se on
-- Johto- ja hallintokorvaukseen kohdistuva laskutettava-työ tyyppinen kulu
SELECT kt.id, kt.vuosi, kt.kuukausi, kt.summa
  FROM kustannusarvioitu_tyo kt
 WHERE ((kt.vuosi = :vuosi AND kt.kuukausi >= 10) OR (kt.vuosi = :vuosi + 1 AND kt.kuukausi <= 9))
   AND kt.sopimus = :sopimus_id
   AND kt.tyyppi = 'laskutettava-tyo'
   AND kt.tehtavaryhma = :tehtavaryhma-id;

-- name: hae-tavoitehinnan-ulkopuolisen-rahavarauksen-toimenpideinstanssi
SELECT tpi.id
  FROM toimenpideinstanssi tpi
       JOIN toimenpide tp ON tpi.toimenpide = tp.id AND tp.koodi = '23151' -- Koodi: 23151 viittaa Hoidon johdon toimenpiteeseen.
 WHERE tpi.urakka = :urakka_id;

-- name: hae-urakan-suunnitellut-tavoitehinnan-ulkopuoliset-rahavaraukset
-- Haetaan tavoitehinnan ulkopuoliset rahavaraukset kustannusarvioitu_tyo taulusta.
-- Käytännössä nämä ovat johto ja hallintkorvaukseen kohdistuvia laskutettava-tyo tyyppisiä suunniteltuja kustannuksia.
  WITH urakan_alkuvuodet AS (SELECT GENERATE_SERIES(:alkuvuosi::INT, :loppuvuosi::INT - 1) AS year)
SELECT 'Tavoitehinnan ulkopuoliset rahavaraukset' AS nimi, -- Tämä on johto ja hallintokorvaus, mutta se nimetään UIlla näin
       SUM(kt.summa)                              AS summa,
       kt.indeksikorjaus_vahvistettu              AS "indeksikorjaus-vahvistettu",
       CASE
           WHEN kt.vuosi = y.year AND kt.kuukausi >= 10 THEN kt.vuosi
           WHEN kt.vuosi - 1 = y.year AND kt.kuukausi <= 9 THEN kt.vuosi - 1
           ELSE y.year
           END                                    AS hoitokauden_alkuvuosi
  FROM urakan_alkuvuodet y
           JOIN sopimus s ON s.urakka = :urakka_id
           LEFT JOIN kustannusarvioitu_tyo kt
                     ON kt.sopimus = s.id AND kt.tyyppi = 'laskutettava-tyo' AND kt.tehtavaryhma = :tehtavaryhma_id
                         AND ((kt.vuosi = y.year AND kt.kuukausi >= 10) OR
                              (kt.vuosi = y.year + 1 AND kt.kuukausi <= 9))
 GROUP BY kt.indeksikorjaus_vahvistettu, hoitokauden_alkuvuosi
 ORDER BY hoitokauden_alkuvuosi;
