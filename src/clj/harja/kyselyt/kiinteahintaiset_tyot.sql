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

-- name: paivita-kiinteahintainen-tyo!
-- Päivittää kiinteahintaisen tyon summan, tunnisteena tpi, tehtävä-id, vuosi ja kk
UPDATE kiinteahintainen_tyo
SET summa     = :summa,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE toimenpideinstanssi = :toimenpideinstanssi
  AND ((:tehtavaryhma::INTEGER IS NULL AND tehtavaryhma IS NULL) OR tehtavaryhma = :tehtavaryhma)
  AND ((:tehtava::INTEGER IS NULL AND tehtava IS NULL) OR tehtava = :tehtava)
  AND vuosi = :vuosi
  AND kuukausi = :kuukausi;

-- name: lisaa-kiinteahintainen-tyo<!
-- Lisää kiinteähintaisen tyon
INSERT INTO kiinteahintainen_tyo
(vuosi, kuukausi, summa, toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, luotu, luoja)
VALUES (:vuosi, :kuukausi, :summa, :toimenpideinstanssi, :tehtavaryhma, :tehtava, :sopimus, current_timestamp,
        :kayttaja);

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee teiden hoidon urakan (MHU) kaikki kustannussuunnitelmat likaiseksi urakkakohtaisen toimenpideinstanssin ja maksuerätyypin mukaan
UPDATE kustannussuunnitelma
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                          JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                   WHERE m.tyyppi IN (:maksueratyyppi ::MAKSUERATYYPPI)
                     AND tpi.id = :toimenpideinstanssi);
