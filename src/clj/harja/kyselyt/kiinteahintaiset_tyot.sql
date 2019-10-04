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
-- Merkitsee teiden hoidon urakan (MHU) kaikki kustannussuunnitelmat likaiseksi toimenpiteen mukaan
UPDATE kustannussuunnitelma
SET likainen = TRUE
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                          JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                   WHERE m.tyyppi IN ('kokonaishintainen', 'akillinen-hoitotyo', 'muu')
                     AND tpi.id IN (:toimenpideinstanssit));

