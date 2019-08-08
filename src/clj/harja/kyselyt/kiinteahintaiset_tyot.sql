-- name: hae-kiinteahintaiset-tyot
-- Hakee kaikki urakan kiinteahintaiset tyot
SELECT
  kht.id,
  kht.vuosi,
  kht.kuukausi,
  kht.summa,
  tpik.nimi AS toimenpide
FROM kiinteahintainen_tyo kht
  LEFT JOIN toimenpideinstanssi tpi ON kht.toimenpideinstanssi = tpi.id
  LEFT JOIN toimenpidekoodi tpik ON tpik.id = tpi.toimenpide
WHERE tpi.urakka = :urakka
ORDER BY vuosi, kuukausi;

-- name: paivita-kiinteahintainen-tyo!
-- Päivittää kiinteahintaisen tyon summan, tunnisteena tpi, tehtävä-id, vuosi ja kk
UPDATE kiinteahintainen_tyo
SET
summa = :summa,
muokattu = current_timestamp,
muokkaaja = :kayttaja
WHERE toimenpideinstanssi = :toimenpideinstanssi
      AND vuosi = :vuosi AND kuukausi = :kuukausi;

-- name: lisaa-kiinteahintainen-tyo<!
-- Lisää kiinteähintaisen tyon
INSERT INTO kiinteahintainen_tyo
(vuosi, kuukausi, summa, toimenpideinstanssi, sopimus, luotu, luoja)
VALUES (:vuosi, :kuukausi, :summa, :toimenpideinstanssi, :sopimus, current_timestamp , :kayttaja);

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee teiden hoidon urakan (MHU) kaikki kustannussuunnitelmat likaiseksi toimenpiteen mukaan
UPDATE kustannussuunnitelma
SET likainen = TRUE
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                     JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                   WHERE m.tyyppi IN ('kokonaishintainen', 'akillinen-hoitotyo','muu')
                   AND tpi.id IN (:toimenpideinstanssit));

