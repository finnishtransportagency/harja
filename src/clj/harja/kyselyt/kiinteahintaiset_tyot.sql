-- name: hae-kiinteahintaiset-tyot
-- Hakee kaikki urakan kiinteahintaiset tyot
SELECT kht.id,
       kht.vuosi,
       kht.kuukausi,
       kht.summa,
       kht.tehtava,
       kht.tehtavaryhma,
       kht.toimenpideinstanssi,
       kht.sopimus,
       tpik.nimi AS toimenpide
FROM kiinteahintainen_tyo kht
       LEFT JOIN toimenpideinstanssi tpi ON kht.toimenpideinstanssi = tpi.id
       LEFT JOIN toimenpidekoodi tpik ON tpik.id = tpi.toimenpide
WHERE tpi.urakka = :urakka
ORDER BY vuosi, kuukausi;

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee teiden hoidon urakan (MHU) kaikki kustannussuunnitelmat likaiseksi urakkakohtaisen toimenpideinstanssin ja maksuerätyypin mukaan
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