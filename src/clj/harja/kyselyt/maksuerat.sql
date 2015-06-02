-- name: hae-urakan-maksuerat
-- Hakee id:n perusteella maksueran lähettämiseen tarvittavat tiedot
SELECT
  m.numero,
  m.tyyppi,
  m.nimi,
  m.tila,
  m.lahetetty,
  tpi.id       AS toimenpideinstanssi_id,
  tpi.nimi     AS toimenpideinstanssi_nimi,
  tpi.alkupvm  AS toimenpideinstanssi_alkupvm,
  tpi.loppupvm AS toimenpideinstanssi_loppupvm,
  s.sampoid    AS sopimus_sampoid,
  k.tila       AS kustannussuunnitelma_tila,
  k.lahetetty  AS kustannussuunnitelma_lahetetty,

  -- Kokonaishintaisten töiden suunniteltu summa
  CASE WHEN m.tyyppi = 'kokonaishintainen'
    THEN

      (SELECT SUM(kht.summa)
       FROM kokonaishintainen_tyo kht
       WHERE kht.toimenpideinstanssi = tpi.id)
  END          AS kokonaishintaisettyot_summa,

  -- Yksikköhintaisten töiden suunniteltu summa
  CASE WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT SUM(yht.maara * yht.yksikkohinta)
       FROM yksikkohintainen_tyo yht
       WHERE yht.tehtava IN (SELECT id
                             FROM toimenpidekoodi
                             WHERE emo = tpk.id))
  END          AS yksikkohintaisettyot_summa,

  -- Yksikköhintaisten toteumien summa
  CASE WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT (sum(tt.maara * yt.yksikkohinta))
       FROM toteuma t
         JOIN toteuma_tehtava tt ON tt.toteuma = t.id
         JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                     (tpk.emo IN (SELECT id
                                                  FROM toimenpidekoodi emo
                                                  WHERE emo.id = tpi.toimenpide))
         JOIN yksikkohintainen_tyo yt
           ON u.id = yt.urakka AND yt.tehtava = tpk.id AND
              t.alkanut >= yt.alkupvm AND t.alkanut <= yt.loppupvm
       WHERE t.tyyppi = 'yksikkohintainen')
  END          AS yksikkohintaisettyot_toteumat

-- TODO: Lisättävä bonusten, sakkojen, indeksien, lisätöiden, äkillisten hoitotöiden sekä muiden maksuerien summien haku

FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN urakka u ON u.id = tpi.urakka
  JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
  JOIN kustannussuunnitelma k ON m.numero = k.maksuera
  JOIN toimenpidekoodi tpk ON tpi.toimenpide = tpk.id
WHERE tpi.urakka = :urakkaid;



-- name: hae-lahetettava-maksuera
-- Hakee numeron perusteella maksueran lähettämiseen tarvittavat tiedot
SELECT
  m.numero,
  m.tyyppi,
  m.nimi,
  m.tila,
  m.lahetetty,
  tpi.id                   AS toimenpideinstanssi_id,
  tpi.nimi                 AS toimenpideinstanssi_nimi,
  tpi.alkupvm              AS toimenpideinstanssi_alkupvm,
  tpi.loppupvm             AS toimenpideinstanssi_loppupvm,
  tpi.tuotepolku           AS toimenpideinstanssi_tuotepolku,
  s.sampoid                AS sopimus_sampoid,
  u.sampoid                AS urakka_sampoid,
  k.tila                   AS kustannussuunnitelma_tila,
  k.lahetetty              AS kustannussuunnitelma_lahetetty,

  -- Tuotenumero
  (SELECT emo.tuotenumero
   FROM toimenpidekoodi emo
   WHERE emo.id = tpk.emo) AS tuotenumero,

  -- Kokonaishintaisten töiden suunniteltu summa
  CASE WHEN m.tyyppi = 'kokonaishintainen'
    THEN
      (SELECT SUM(kht.summa)
       FROM kokonaishintainen_tyo kht
       WHERE kht.toimenpideinstanssi = tpi.id)
  END                      AS kokonaishintaisettyot_summa,

  -- Yksikköhintaisten töiden suunniteltu summa
  CASE WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT SUM(yht.maara * yht.yksikkohinta)
       FROM yksikkohintainen_tyo yht
       WHERE yht.tehtava IN (SELECT id
                             FROM toimenpidekoodi
                             WHERE emo = tpk.id))
  END                      AS yksikkohintaisettyot_summa,

  -- Yksikköhintaisten toteumien summa
  CASE WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT (sum(tt.maara * yt.yksikkohinta))
       FROM toteuma t
         JOIN toteuma_tehtava tt ON tt.toteuma = t.id
         JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                     (tpk.emo IN (SELECT id
                                                  FROM toimenpidekoodi emo
                                                  WHERE emo.id = tpi.toimenpide))
         JOIN yksikkohintainen_tyo yt
           ON u.id = yt.urakka AND yt.tehtava = tpk.id AND t.alkanut >= yt.alkupvm AND t.alkanut <= yt.loppupvm
       WHERE t.tyyppi = 'yksikkohintainen')
  END                      AS yksikkohintaisettyot_toteumat
-- TODO: Lisättävä bonusten, sakkojen, indeksien, lisätöiden, äkillisten hoitotöiden sekä muiden maksuerien summien haku

FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN urakka u ON u.id = tpi.urakka
  JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
  JOIN kustannussuunnitelma k ON m.numero = k.maksuera
  JOIN toimenpidekoodi tpk ON tpi.toimenpide = tpk.id
WHERE m.numero = :numero;


-- name: lukitse-maksuera!
-- Lukitsee maksuerän lähetyksen ajaksi
UPDATE maksuera
SET lukko = :lukko, lukittu = current_timestamp
WHERE numero = :numero AND (lukko IS NULL OR
                            (EXTRACT(EPOCH FROM (current_timestamp - lukittu)) > 300));


-- name: hae-maksueranumero-lahetys-idlla
-- Hakee maksueranumeron lahetys-id:llä
SELECT numero
FROM maksuera
WHERE lahetysid = :lahetysid;


-- name: merkitse-maksuera-odottamaan-vastausta!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE maksuera
SET lahetysid = :lahetysid, lukko = NULL, tila = 'odottaa_vastausta'
WHERE numero = :numero;


-- name: merkitse-maksuera-lahetetyksi!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE maksuera
SET lahetetty = current_timestamp, tila = 'lahetetty'
WHERE numero = :numero;


-- name: merkitse-maksueralle-lahetysvirhe!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE maksuera
SET tila = 'virhe'
WHERE numero = :numero;
