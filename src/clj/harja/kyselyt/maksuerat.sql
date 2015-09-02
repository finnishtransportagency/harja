-- name: hae-urakan-maksuerat
-- Hakee id:n perusteella maksueran lähettämiseen tarvittavat tiedot
SELECT
  m.numero     AS numero,
  m.tyyppi     AS maksuera_tyyppi,
  m.nimi       AS maksuera_nimi,
  m.tila       AS maksuera_tila,
  m.lahetetty  AS maksuera_lahetetty,
  tpi.id       AS toimenpideinstanssi_id,
  tpi.nimi     AS toimenpideinstanssi_nimi,
  tpi.alkupvm  AS toimenpideinstanssi_alkupvm,
  tpi.loppupvm AS toimenpideinstanssi_loppupvm,
  s.sampoid    AS sopimus_sampoid,
  k.tila       AS kustannussuunnitelma_tila,
  k.lahetetty  AS kustannussuunnitelma_lahetetty,

  -- Kustannussuunnitelman summa
  CASE

  WHEN m.tyyppi = 'kokonaishintainen'
    THEN
      (SELECT SUM(kht.summa)
       FROM kokonaishintainen_tyo kht
       WHERE kht.toimenpideinstanssi = tpi.id)

  WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT SUM(yht.maara * yht.yksikkohinta)
       FROM yksikkohintainen_tyo yht
       WHERE yht.tehtava IN (SELECT id
                             FROM toimenpidekoodi
                             WHERE emo = tpk.id) AND
             yht.urakka = u.id)

  ELSE 1

  END          AS kustannussuunnitelma_summa,

  -- Maksuerän summa
  CASE

  WHEN m.tyyppi = 'kokonaishintainen'
    THEN
      (SELECT SUM(kht.summa)
       FROM kokonaishintainen_tyo kht
       WHERE kht.toimenpideinstanssi = tpi.id)

  WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT (sum(tt.maara * yt.yksikkohinta))
       FROM toteuma t
         JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
         JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                     (tpk.emo IN (SELECT id
                                                  FROM toimenpidekoodi emo
                                                  WHERE emo.id = tpi.toimenpide))
         JOIN yksikkohintainen_tyo yt
           ON u.id = yt.urakka AND yt.tehtava = tpk.id AND
              t.alkanut >= yt.alkupvm AND t.alkanut <= yt.loppupvm
       WHERE t.tyyppi = 'yksikkohintainen')

  WHEN m.tyyppi = 'lisatyo'
    THEN
      (SELECT (sum(tt.maara * yt.yksikkohinta))
       FROM toteuma t
         JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
         JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                     (tpk.emo IN (SELECT id
                                                  FROM toimenpidekoodi emo
                                                  WHERE emo.id = tpi.toimenpide))
         JOIN yksikkohintainen_tyo yt
           ON u.id = yt.urakka AND yt.tehtava = tpk.id AND
              t.alkanut >= yt.alkupvm AND t.alkanut <= yt.loppupvm
       WHERE t.tyyppi = 'lisatyo')

  WHEN m.tyyppi = 'akillinen-hoitotyo'
    THEN
      (SELECT (sum(ek.rahasumma))
       FROM erilliskustannus ek
       WHERE ek.toimenpideinstanssi = tpi.id AND
             ek.tyyppi = 'akillinen-hoitotyo')

  WHEN m.tyyppi = 'sakko'
    THEN
      -- Normaalit sakot
      coalesce((SELECT (sum(sa.maara))
                FROM sanktio sa
                WHERE sa.toimenpideinstanssi = tpi.id),
               0)
      +
      -- Suolasakot


      coalesce((SELECT *
                FROM urakan_suolasakot(CAST(:urakkaid AS INTEGER))
                WHERE tpk.koodi = '23104'),
               0)

  WHEN m.tyyppi = 'muu'
    THEN
      (SELECT
         -- Muutostyo
         coalesce((SELECT (sum(tt.maara * yt.yksikkohinta))
                   FROM toteuma t
                     JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
                     JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                                 (tpk.emo IN (SELECT id
                                                              FROM toimenpidekoodi emo
                                                              WHERE emo.id = tpi.toimenpide))
                     JOIN yksikkohintainen_tyo yt
                       ON u.id = yt.urakka AND yt.tehtava = tpk.id AND
                          t.alkanut >= yt.alkupvm AND t.alkanut <= yt.loppupvm
                   WHERE t.tyyppi = 'muutostyo'),
                  0)
         +
         -- Erilliskustannukset
           -- Fixme: indeksien laskenta!
         coalesce((SELECT (sum(ek.rahasumma))
                   FROM erilliskustannus ek
                   WHERE ek.toimenpideinstanssi = tpi.id AND
                         ek.tyyppi != 'akillinen-hoitotyo'),
                  0))

  -- TODO: Lisättävä bonusten, sakkojen & indeksien maksuerien summien haku
  ELSE 0

  END          AS maksuera_summa

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
  m.numero                 AS numero,
  m.tyyppi                 AS maksuera_tyyppi,
  m.nimi                   AS maksuera_nimi,
  m.tila                   AS maksuera_tila,
  m.lahetetty              AS maksuera_lahetetty,
  tpi.id                   AS toimenpideinstanssi_id,
  tpi.nimi                 AS toimenpideinstanssi_nimi,
  tpi.alkupvm              AS toimenpideinstanssi_alkupvm,
  tpi.loppupvm             AS toimenpideinstanssi_loppupvm,
  tpi.tuotepolku           AS toimenpideinstanssi_tuotepolku,
  tpi.vastuuhenkilo_id     AS toimenpideinstanssi_vastuuhenkilo,
  tpi.talousosasto_id      AS toimenpideinstanssi_talousosasto,
  tpi.talousosastopolku    AS toimenpideinstanssi_talousosastopolku,
  tpi.sampoid              AS toimenpideinstanssi_sampoid,
  s.sampoid                AS sopimus_sampoid,
  u.sampoid                AS urakka_sampoid,
  k.tila                   AS kustannussuunnitelma_tila,
  k.lahetetty              AS kustannussuunnitelma_lahetetty,

  -- Tuotenumero
  (SELECT emo.tuotenumero
   FROM toimenpidekoodi emo
   WHERE emo.id = tpk.emo) AS tuotenumero,

  -- Kustannussuunnitelman summa
  CASE
  WHEN m.tyyppi = 'kokonaishintainen'
    THEN
      (SELECT SUM(kht.summa)
       FROM kokonaishintainen_tyo kht
       WHERE kht.toimenpideinstanssi = tpi.id)
  WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT SUM(yht.maara * yht.yksikkohinta)
       FROM yksikkohintainen_tyo yht
       WHERE yht.tehtava IN (SELECT id
                             FROM toimenpidekoodi
                             WHERE emo = tpk.id))
  ELSE 1
  END                      AS kustannussuunnitelma_summa,

  -- Maksuerän summa
  CASE

  WHEN m.tyyppi = 'kokonaishintainen'
    THEN
      (SELECT SUM(kht.summa)
       FROM kokonaishintainen_tyo kht
       WHERE kht.toimenpideinstanssi = tpi.id)

  WHEN m.tyyppi = 'yksikkohintainen'
    THEN
      (SELECT (sum(tt.maara * yt.yksikkohinta))
       FROM toteuma t
         JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
         JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                     (tpk.emo IN (SELECT id
                                                  FROM toimenpidekoodi emo
                                                  WHERE emo.id = tpi.toimenpide))
         JOIN yksikkohintainen_tyo yt
           ON u.id = yt.urakka AND yt.tehtava = tpk.id AND t.alkanut >= yt.alkupvm AND t.alkanut <= yt.loppupvm
       WHERE t.tyyppi = 'yksikkohintainen')

  WHEN m.tyyppi = 'lisatyo'
    THEN
      (SELECT (sum(tt.maara * yt.yksikkohinta))
       FROM toteuma t
         JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
         JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                     (tpk.emo IN (SELECT id
                                                  FROM toimenpidekoodi emo
                                                  WHERE emo.id = tpi.toimenpide))
         JOIN yksikkohintainen_tyo yt
           ON u.id = yt.urakka AND yt.tehtava = tpk.id AND t.alkanut >= yt.alkupvm AND t.alkanut <= yt.loppupvm
       WHERE t.tyyppi = 'lisatyo')

  WHEN m.tyyppi = 'akillinen-hoitotyo'
    THEN
      (SELECT (sum(ek.rahasumma))
       FROM erilliskustannus ek
       WHERE ek.toimenpideinstanssi = tpi.id AND
             ek.tyyppi = 'akillinen-hoitotyo')

  WHEN m.tyyppi = 'sakko'
    THEN
      -- Normaalisakot
      coalesce((SELECT (sum(sa.maara))
                FROM sanktio sa
                WHERE sa.toimenpideinstanssi = tpi.id),
               0)
      +
      -- Suolasakot
      coalesce((SELECT *
                FROM urakan_suolasakot(CAST(:urakkaid AS INTEGER))
                WHERE tpk.koodi = '23104'),
               0)

  WHEN m.tyyppi = 'muu'
    THEN

      (SELECT (
        -- Muutostyöt
        coalesce((SELECT (sum(tt.maara * yt.yksikkohinta))
                  FROM toteuma t
                    JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.poistettu IS NOT TRUE
                    JOIN toimenpidekoodi tpk ON tpk.id = tt.toimenpidekoodi AND
                                                (tpk.emo IN (SELECT id
                                                             FROM toimenpidekoodi emo
                                                             WHERE emo.id = tpi.toimenpide))
                    JOIN yksikkohintainen_tyo yt
                      ON u.id = yt.urakka AND yt.tehtava = tpk.id AND t.alkanut >= yt.alkupvm AND
                         t.alkanut <= yt.loppupvm
                  WHERE t.tyyppi = 'muutostyo'),
                 0)
        +
        -- Erilliskustannukset
        coalesce((SELECT (sum(ek.rahasumma))
                  FROM erilliskustannus ek
                  WHERE ek.toimenpideinstanssi = tpi.id AND
                        ek.tyyppi != 'akillinen-hoitotyo')
        , 0)))

  -- TODO: Lisättävä bonusten, sakkojen & indeksien maksuerien summien haku
  ELSE 0

  END                      AS maksuera_summa

FROM maksuera m
  JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi
  JOIN urakka u ON u.id = tpi.urakka
  JOIN sopimus s ON s.urakka = u.id AND s.paasopimus IS NULL
  JOIN kustannussuunnitelma k ON m.numero = k.maksuera
  JOIN toimenpidekoodi tpk ON tpi.toimenpide = tpk.id
WHERE m.numero = :numero;


-- name: hae-maksueran-ja-kustannussuunnitelman-tilat
-- Hakee maksueran ja kustannussuunnitelman tilat
SELECT
  m.tila      AS maksuera_tila,
  m.lahetetty AS maksuera_lahetetty,
  k.tila      AS kustannussuunnitelma_tila,
  k.lahetetty AS kustannussuunnitelma_lahetetty
FROM maksuera m
  JOIN kustannussuunnitelma k ON k.maksuera = m.numero
WHERE m.numero = :numero;


-- name: hae-maksueranumero-lahetys-idlla
-- Hakee maksueranumeron lahetys-id:llä
SELECT numero
FROM maksuera
WHERE lahetysid = :lahetysid;

-- name: hae-likaiset-maksuerat
-- Hakee maksuerät, jotka täytyy lähettää
SELECT numero
FROM maksuera
WHERE likainen = TRUE;

-- name: lukitse-maksuera!
-- Lukitsee maksuerän lähetyksen ajaksi
UPDATE maksuera
SET lukko = :lukko, lukittu = current_timestamp
WHERE numero = :numero AND (lukko IS NULL OR
                            (EXTRACT(EPOCH FROM (current_timestamp - lukittu)) > 300));

-- name: merkitse-maksuera-odottamaan-vastausta!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n, avaa lukon ja merkitsee puhtaaksi
UPDATE maksuera
SET lahetysid = :lahetysid, lukko = NULL, tila = 'odottaa_vastausta', likainen = FALSE
WHERE numero = :numero;

-- name: merkitse-maksuera-lahetetyksi!
-- Merkitsee maksuerän lähetetyksi
UPDATE maksuera
SET lahetetty = current_timestamp, tila = 'lahetetty'
WHERE numero = :numero;

-- name: merkitse-maksueralle-lahetysvirhe!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE maksuera
SET tila = 'virhe'
WHERE numero = :numero;

-- name: merkitse-tyypin-maksuerat-likaisiksi!
-- Merkitsee kaikki annetun tyypin mukaiset maksuerät likaisi
UPDATE maksuera
SET likainen = TRUE
WHERE tyyppi = :tyyppi :: maksueratyyppi;

-- name: luo-maksuera<!
-- Luo uuden maksuerän.
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi, likainen, luotu)
VALUES (:toimenpideinstanssi, :tyyppi :: maksueratyyppi, :nimi, TRUE, current_timestamp);
