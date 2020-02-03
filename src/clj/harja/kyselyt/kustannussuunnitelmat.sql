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
SELECT
  k.maksuera,
  u.id   AS urakkaid,
  tpi.id AS tpi_id
FROM kustannussuunnitelma k
  JOIN maksuera m ON k.maksuera = m.numero
  JOIN toimenpideinstanssi tpi ON m.toimenpideinstanssi = tpi.id
  JOIN urakka u ON tpi.urakka = u.id
WHERE k.likainen = TRUE;

-- name: merkitse-toimenpiteen-kustannussunnitelmat-likaisiksi!
-- Merkitsee kaikki toimenpiteen kustannussuunnitelmat likaisiksi uudelleen lähetystä varten
UPDATE kustannussuunnitelma
SET likainen = TRUE,
muokattu = current_timestamp
WHERE maksuera IN (select numero from  maksuera WHERE toimenpideinstanssi = :tpi AND
  toimenpideinstanssi IN (select id from toimenpideinstanssi where loppupvm > current_timestamp - INTERVAL '3 months'));

-- name: merkitse-kustannussuunnitelma-odottamaan-vastausta!
-- Merkitsee kustannussuunnitelma lähetetyksi, kirjaa lähetyksen id:n, avaa lukon ja merkitsee puhtaaksi
UPDATE kustannussuunnitelma
SET lahetysid = :lahetysid, lukko = NULL, tila = 'odottaa_vastausta', likainen = FALSE, lahetetty = current_timestamp
WHERE maksuera = :numero;

-- name: merkitse-kustannussuunnitelma-lahetetyksi!
-- Merkitsee kustannussuunnitelman lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE kustannussuunnitelma
SET tila = 'lahetetty'
WHERE maksuera = :numero;

-- name: merkitse-kustannussuunnitelmalle-lahetysvirhe!
-- Merkitsee kustannussuunnitelman lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE kustannussuunnitelma
SET tila = 'virhe', lukko = NULL, lukittu = NULL
WHERE maksuera = :numero;

-- name: luo-kustannussuunnitelma<!
-- Luo uuden kustannussuunnitelman.
INSERT INTO kustannussuunnitelma (maksuera, likainen, luotu)
VALUES (:maksuera, TRUE, current_timestamp);

-- name: hae-kustannussuunnitelman-kokonaishintaiset-summat
SELECT
  kht.vuosi,
  Sum(COALESCE(kht.summa, 0)) AS summa
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN kokonaishintainen_tyo kht ON kht.toimenpideinstanssi = tpi.id
WHERE m.numero = :maksuera
GROUP BY vuosi;

-- name: hae-teiden-hoidon-kustannussuunnitelman-kokonaishintaiset-summat
-- Teiden hoidon urakoissa (MHU) kokonaishintainen Sampoon lähetettävä kustannussuunnitelma koostuu
-- kaikista kiinteähintaisten, kustannusarvioitujen ja yksikköhintaisten töiden suunnittelutiedoista.
-- Tieto äkillisiin hoitotöihin ja vahinkojen korjauksiin varatuista rahavaroista lähetetään myös tässä kustannussuunnitelmassa.
SELECT
  kt.vuosi as vuosi,
  Sum(COALESCE(kt.summa, 0)) AS summa
FROM maksuera m
       JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
       JOIN kiinteahintainen_tyo kt on tpi.id = kt.toimenpideinstanssi
WHERE m.numero = :maksuera GROUP BY kt.vuosi
UNION ALL
SELECT
  ka.vuosi as vuosi,
  Sum(COALESCE(ka.summa, 0)) AS summa
FROM maksuera m
       JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
       JOIN kustannusarvioitu_tyo ka on tpi.id = ka.toimenpideinstanssi
WHERE m.numero = :maksuera GROUP BY ka.vuosi
UNION ALL
SELECT
  jhk.vuosi as vuosi,
  Sum(COALESCE((jhk.tunnit * jhk.tuntipalkka), 0)) AS summa1
FROM maksuera m
       JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi and tpi.toimenpide = (select id from toimenpidekoodi where koodi = '23151') -- hoidon johto
       JOIN johto_ja_hallintokorvaus jhk on tpi.urakka = jhk."urakka-id"
WHERE m.numero = :maksuera GROUP BY jhk.vuosi;

-- name: hae-kanavaurakan-kustannussuunnitelman-yksikkohintaiset-summat
SELECT
  Extract(YEAR FROM yht.alkupvm) AS vuosi,
  Sum(arvioitu_kustannus)        AS summa
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN toimenpidekoodi tpk ON tpi.toimenpide = tpk.id
  JOIN yksikkohintainen_tyo yht ON yht.tehtava IN
                                   (SELECT id
                                    FROM toimenpidekoodi
                                    WHERE emo = tpk.id)
                                   AND
                                   yht.urakka = tpi.urakka
WHERE m.numero = :maksuera
GROUP BY Extract(YEAR FROM yht.alkupvm);

-- name: hae-hoitourakan-kustannussuunnitelman-yksikkohintaiset-summat
SELECT
  Extract(YEAR FROM yht.alkupvm)                              AS vuosi,
  Sum(COALESCE(yht.maara, 0) * COALESCE(yht.yksikkohinta, 0)) AS summa
FROM maksuera m
       JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
       JOIN toimenpidekoodi tpk ON tpi.toimenpide = tpk.id
       JOIN yksikkohintainen_tyo yht ON yht.tehtava IN
                                        (SELECT id
                                         FROM toimenpidekoodi
                                         WHERE emo = tpk.id)
  AND
                                        yht.urakka = tpi.urakka
WHERE m.numero = :maksuera
GROUP BY Extract(YEAR FROM yht.alkupvm)
ORDER BY vuosi;

-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT maksuera
              FROM kustannussuunnitelma
              WHERE maksuera = :numero :: BIGINT);

-- name: tuotenumero-loytyy
SELECT exists(SELECT numero
              FROM maksuera m
                JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
                JOIN toimenpidekoodi tpk3 ON tpi.toimenpide = tpk3.id
                JOIN toimenpidekoodi tpk2 ON tpk3.emo = tpk2.id
              WHERE m.numero = :numero AND tpk2.tuotenumero IS NOT NULL);

-- name: harja.kyselyt.kustannussuunnitelmat/hae-urakka-maksueranumerolla
SELECT u.id
FROM urakka u
  JOIN toimenpideinstanssi tpi ON u.id = tpi.urakka
  JOIN maksuera m ON tpi.id = m.toimenpideinstanssi
WHERE m.numero = :numero;

