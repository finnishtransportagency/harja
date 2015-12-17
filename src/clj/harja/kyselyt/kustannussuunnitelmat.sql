-- name: lukitse-kustannussuunnitelma!
-- Lukitsee kustannussuunnitelman lähetyksen ajaksi
UPDATE kustannussuunnitelma
SET lukko = :lukko, lukittu = current_timestamp
WHERE maksuera = :numero AND (lukko IS NULL OR
                              (EXTRACT(EPOCH FROM (current_timestamp - lukittu)) > 300));

-- name: hae-maksuera-lahetys-idlla
-- Hakee kustannussuunnitel lahetys-id:llä
SELECT maksuera
FROM kustannussuunnitelma
WHERE lahetysid = :lahetysid;

-- name: hae-likaiset-kustannussuunnitelmat
-- Hakee maksuerät, jotka täytyy lähettää
SELECT maksuera
FROM kustannussuunnitelma
WHERE likainen = TRUE;

-- name: merkitse-kustannussuunnitelma-odottamaan-vastausta!
-- Merkitsee kustannussuunnitelma lähetetyksi, kirjaa lähetyksen id:n, avaa lukon ja merkitsee puhtaaksi
UPDATE kustannussuunnitelma
SET lahetysid = :lahetysid, lukko = NULL, tila = 'odottaa_vastausta', likainen = FALSE
WHERE maksuera = :numero;

-- name: merkitse-kustannussuunnitelma-lahetetyksi!
-- Merkitsee kustannussuunnitelman lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE kustannussuunnitelma
SET lahetetty = current_timestamp, tila = 'lahetetty'
WHERE maksuera = :numero;

-- name: merkitse-kustannussuunnitelmalle-lahetysvirhe!
-- Merkitsee kustannussuunnitelman lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE kustannussuunnitelma
SET tila = 'virhe'
WHERE maksuera = :numero;

-- name: luo-kustannussuunnitelma<!
-- Luo uuden kustannussuunnitelman.
INSERT INTO kustannussuunnitelma (maksuera, likainen, luotu)
VALUES (:maksuera, true, current_timestamp);

-- name: hae-kustannussuunnitelman-kokonaishintaiset-summat
SELECT
  kht.summa,
  kht.kuukausi,
  kht.vuosi
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN kokonaishintainen_tyo kht ON kht.toimenpideinstanssi = tpi.id
WHERE m.numero = :maksuera;

-- name: hae-kustannussuunnitelman-yksikkohintaiset-summat
SELECT
  yht.maara * yht.yksikkohinta,
  yht.alkupvm,
  yht.loppupvm
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN toimenpidekoodi tpk ON tpi.toimenpide = tpk.id
  JOIN yksikkohintainen_tyo yht ON yht.tehtava IN
                                   (SELECT id
                                    FROM toimenpidekoodi
                                    WHERE emo = tpk.id)
WHERE m.numero = :maksuera;