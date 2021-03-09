-- name: hae-urakan-kustannusarvioidut-tyot-nimineen
-- Lähes sama kuin hae-kustannusarvoidut-tyot mutta palauttaa tyypin, tehtavan, ja toimenpiteen nimet
SELECT kat.id,
       kat.vuosi,
       kat.kuukausi,
       kat.summa,
       kat.tyyppi ::TOTEUMATYYPPI,
       tpik_t.yksiloiva_tunniste AS "tehtavan-tunniste",
       tr.yksiloiva_tunniste AS "tehtavaryhman-tunniste",
       tpik_tpi.koodi AS "toimenpiteen-koodi",
       kat.sopimus,
       kat.indeksikorjattu
FROM kustannusarvioitu_tyo kat
       LEFT JOIN toimenpideinstanssi tpi ON kat.toimenpideinstanssi = tpi.id
       LEFT JOIN toimenpidekoodi tpik_tpi ON tpik_tpi.id = tpi.toimenpide
       LEFT JOIN toimenpidekoodi tpik_t ON tpik_t.id = kat.tehtava
       LEFT JOIN tehtavaryhma tr ON kat.tehtavaryhma = tr.id
WHERE tpi.urakka = :urakka and (case when 'kaikki' = :indeksikorjatut then :indeksikorjatut else kat.indeksikorjattu end) = :indeksikorjatut);


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