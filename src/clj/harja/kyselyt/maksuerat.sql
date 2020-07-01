-- name: hae-hoitourakan-maksuerien-summat
-- Hakee id:n perusteella maksuerien lähettämiseen tarvittavat tiedot.
-- Jokaiselle toimenpideinstanssille palautetaan id sekä sarakkeet kaikille
-- eri maksuerätyypeille.
SELECT
  tpi_id,
  :urakka_id as urakka_id,
  SUM(kokonaishintaisten_summa)  AS kokonaishintainen,
  SUM(yksikkohintaisten_summa)   AS yksikkohintainen,
  SUM(sakot_summa)               AS sakko,
  SUM(akilliset_hoitotyot_summa) AS "akillinen-hoitotyo",
  SUM(lisatyot_summa)            AS lisatyo,
  SUM(bonukset_summa)            AS bonus,
  SUM(indeksit_summa)            AS indeksi,
  SUM(muut_summa)                AS muu
FROM (SELECT
        SUM((laskutusyhteenveto).kht_laskutettu +
            (laskutusyhteenveto).kht_laskutetaan)                 AS kokonaishintaisten_summa,
        SUM((laskutusyhteenveto).yht_laskutettu +
            (laskutusyhteenveto).yht_laskutetaan)                 AS yksikkohintaisten_summa,
        SUM((laskutusyhteenveto).sakot_laskutettu +
            (laskutusyhteenveto).sakot_laskutetaan +
            (laskutusyhteenveto).suolasakot_laskutettu +
            (laskutusyhteenveto).suolasakot_laskutetaan)          AS sakot_summa,
        SUM((laskutusyhteenveto).akilliset_hoitotyot_laskutettu +
            (laskutusyhteenveto).akilliset_hoitotyot_laskutetaan) AS akilliset_hoitotyot_summa,
        SUM((laskutusyhteenveto).muutostyot_laskutettu +
            (laskutusyhteenveto).muutostyot_laskutetaan)          AS lisatyot_summa,
        SUM((laskutusyhteenveto).bonukset_laskutettu +
            (laskutusyhteenveto).bonukset_laskutetaan)            AS bonukset_summa,
        SUM((laskutusyhteenveto).kaikki_laskutettu_ind_korotus +
            (laskutusyhteenveto).kaikki_laskutetaan_ind_korotus)  AS indeksit_summa,
        SUM((laskutusyhteenveto).erilliskustannukset_laskutettu +
            (laskutusyhteenveto).erilliskustannukset_laskutetaan +
            (laskutusyhteenveto).vahinkojen_korjaukset_laskutettu +
            (laskutusyhteenveto).vahinkojen_korjaukset_laskutetaan) AS muut_summa,
        (laskutusyhteenveto).tpi                                  AS tpi_id,
        lyht.alkupvm,
        lyht.loppupvm
      FROM (-- laskutusyhteenvedot menneiden hoitokausien viimeisille kuukausille
             SELECT
               hk.alkupvm,
               hk.loppupvm,
               laskutusyhteenveto(hk.alkupvm, hk.loppupvm,
                                  date_trunc('month', hk.loppupvm) :: DATE,
                                  (date_trunc('month', hk.loppupvm) + INTERVAL '1 month') :: DATE,
                                  :urakka_id :: INTEGER)
             FROM (SELECT *
                   FROM urakan_hoitokaudet(:urakka_id :: INTEGER)
                   WHERE loppupvm < now()) AS hk
             UNION ALL -- laskutusyhteenvedot menneiden hoitokausien viimeisille kuukausille
             SELECT
               hk.alkupvm,
               hk.loppupvm,
               laskutusyhteenveto(hk.alkupvm, hk.loppupvm,
                                  date_trunc('month', now()) :: DATE,
                                  (date_trunc('month', now()) + INTERVAL '1 month') :: DATE, :urakka_id :: INTEGER)
             FROM (SELECT *
                   FROM urakan_hoitokaudet(:urakka_id :: INTEGER)
                   WHERE alkupvm < now() AND loppupvm > now()) AS hk
           ) AS lyht
      GROUP BY tpi_id, lyht.alkupvm, lyht.loppupvm) AS maksuerat
GROUP BY tpi_id;

-- name: hae-teiden-hoidon-urakan-maksuerien-summat
-- Hakee urakan id:n perusteella maksuerien lähettämiseen tarvittavat tiedot.
-- Jokaiselle toimenpideinstanssille palautetaan id sekä maksuerän summa.
-- Teidenhoidon urakoissa (MHU) maksuerätyyppejä on vain yksi (kokonaishintainen).
SELECT tpi_id,
       :urakka_id                       as urakka_id,
       SUM(kokonaishintaisten_summa)    AS kokonaishintainen -- Kaikki Sampon maksuerään ajankohtaan mennessä kuuluvat kulut. Suunnitellut HJ-kustannukset siirtyvät kuukauden viimeisenä päivänä.
FROM (SELECT
          SUM((mhu_laskutusyhteenveto_teiden_hoito).kaikki_laskutettu) AS kokonaishintaisten_summa,
          lyht.alkupvm,
          lyht.loppupvm,
          (mhu_laskutusyhteenveto_teiden_hoito).tpi                                  AS tpi_id
      FROM (-- laskutusyhteenvedot menneiden hoitokausien viimeisille kuukausille
               SELECT
                   hk.alkupvm,
                   hk.loppupvm,
                   mhu_laskutusyhteenveto_teiden_hoito(hk.alkupvm, hk.loppupvm,
                                                       date_trunc('month', hk.loppupvm) :: DATE,
                       -- luvut halutaan maksuerälle vasta kuukauden viimeisenä päivänä
                                                       (SELECT CASE
                                                                   WHEN
                                                                       (now()::DATE =
                                                                        (SELECT (date_trunc('MONTH', now()::DATE) +
                                                                                 INTERVAL '1 MONTH - 1 day')::DATE))
                                                                       THEN
                                                                       now()::DATE
                                                                   ELSE
                                                                       (date_trunc('month', now()::DATE) - INTERVAL '1 day')::DATE
                                                                   END)                       ,
                                                       :urakka_id :: INTEGER)
               FROM (SELECT *
                     FROM urakan_hoitokaudet(:urakka_id :: INTEGER)
                     WHERE loppupvm < now()::DATE) AS hk
               UNION ALL -- laskutusyhteenvedot menneiden hoitokausien viimeisille kuukausille
               SELECT
                   hk.alkupvm,
                   hk.loppupvm,
                   mhu_laskutusyhteenveto_teiden_hoito(hk.alkupvm, hk.loppupvm,
                                                       date_trunc('month', now()::DATE) ::DATE,
                                                       (SELECT CASE
                                                                   WHEN
                                                                       (now()::DATE =
                                                                        (SELECT (date_trunc('MONTH', now()::DATE) +
                                                                                 INTERVAL '1 MONTH - 1 day')::DATE))
                                                                       THEN
                                                                       now()::DATE
                                                                   ELSE
                                                                       (date_trunc('month', now()::DATE) - INTERVAL '1 day')::DATE
                                                                   END)                       ,
                                                       :urakka_id :: INTEGER)
               FROM (SELECT *
                     FROM urakan_hoitokaudet(:urakka_id :: INTEGER)
                     WHERE alkupvm <= now()::DATE AND loppupvm >= now()::DATE) AS hk
           ) AS lyht
      GROUP BY tpi_id, lyht.alkupvm, lyht.loppupvm) AS maksuerat
GROUP BY tpi_id;

-- name: hae-urakan-maksuerat
-- Hakee id:n perusteella maksueran lähettämiseen tarvittavat tiedot.
-- Huom! Maksuerän summat haetaan hae-urakan-maksueratiedot kyselyllä, joka
-- muodostaa ne laskutusyhteenvetoa kutsumalla.
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
  s.sampoid                AS sopimus_sampoid,
  k.tila                   AS kustannussuunnitelma_tila,
  k.lahetetty              AS kustannussuunnitelma_lahetetty,
  -- Tuotenumero
  (SELECT emo.tuotenumero
   FROM toimenpidekoodi emo
   WHERE emo.id = tpk.emo) AS tuotenumero,

  -- Kustannussuunnitelman summa
  COALESCE(
      CASE

      -- Kokonaishintaiset maksuerät kaikille urakoille
      WHEN m.tyyppi = 'kokonaishintainen'
        THEN (SELECT SUM(kht.summa)
              FROM kokonaishintainen_tyo kht
              WHERE kht.toimenpideinstanssi = tpi.id)

      -- Yksikköhintaiset muiden kuin kanavaurakoiden
      WHEN m.tyyppi = 'yksikkohintainen' AND
           NOT u.tyyppi = 'vesivayla-kanavien-hoito' AND
           NOT u.tyyppi = 'vesivayla-kanavien-korjaus'

        THEN (SELECT SUM(yht.maara * yht.yksikkohinta)
              FROM yksikkohintainen_tyo yht
              WHERE
                yht.urakka = :urakkaid AND
                yht.tehtava IN (SELECT id
                                FROM toimenpidekoodi
                                WHERE emo = tpk.id))

      --  Kanavaurakoiden muutos ja lisätyöt
      WHEN m.tyyppi = 'lisatyo' AND
           (u.tyyppi = 'vesivayla-kanavien-hoito' OR
            u.tyyppi = 'vesivayla-kanavien-korjaus')
        THEN
          (SELECT SUM(yht.arvioitu_kustannus)
           FROM yksikkohintainen_tyo yht
           WHERE
             yht.urakka = :urakkaid AND
             yht.tehtava IN (SELECT id
                             FROM toimenpidekoodi
                             WHERE emo = tpk.id))

      -- Kaikki muut kustannussuunnitelmat
      ELSE 1
      END, 0)         AS kustannussuunnitelma_summa
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
  tpk.koodi                AS toimenpidekoodi,
  s.sampoid                AS sopimus_sampoid,
  u.sampoid                AS urakka_sampoid,
  u.tyyppi                 AS urakka_tyyppi,
  k.tila                   AS kustannussuunnitelma_tila,
  k.lahetetty              AS kustannussuunnitelma_lahetetty,
  tpi.urakka               AS "urakka-id",

  -- Tuotenumero
  (SELECT emo.tuotenumero
   FROM toimenpidekoodi emo
   WHERE emo.id = tpk.emo) AS tuotenumero,

  -- Kustannussuunnitelman summa
  CASE WHEN m.tyyppi = 'kokonaishintainen'
    THEN (SELECT SUM(kht.summa)
          FROM kokonaishintainen_tyo kht
          WHERE kht.sopimus = s.id AND kht.toimenpideinstanssi = tpi.id)
  WHEN m.tyyppi = 'yksikkohintainen'
    THEN (SELECT SUM(yht.maara * yht.yksikkohinta)
          FROM yksikkohintainen_tyo yht
          WHERE yht.urakka = u.id AND yht.tehtava IN (SELECT id
                                                      FROM toimenpidekoodi
                                                      WHERE emo = tpk.id))
  ELSE 1
  END                      AS kustannussuunnitelma_summa
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
SELECT
  m.numero,
  u.id   AS urakkaid,
  tpi.id AS tpi_id
FROM maksuera m
  JOIN toimenpideinstanssi tpi ON m.toimenpideinstanssi = tpi.id
  JOIN urakka u ON tpi.urakka = u.id
WHERE m.likainen IS TRUE;

-- name: lukitse-maksuera!
-- Lukitsee maksuerän lähetyksen ajaksi
UPDATE maksuera
SET lukko = :lukko, lukittu = current_timestamp
WHERE numero = :numero AND (lukko IS NULL OR
                            (EXTRACT(EPOCH FROM (current_timestamp - lukittu)) > 300));

-- name: merkitse-maksuera-odottamaan-vastausta!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n, avaa lukon ja merkitsee puhtaaksi
UPDATE maksuera
SET lahetysid = :lahetysid, tila = 'odottaa_vastausta', likainen = FALSE, lahetetty = CURRENT_TIMESTAMP
WHERE numero = :numero;

-- name: merkitse-maksuera-lahetetyksi!
-- Merkitsee maksuerän lähetetyksi
UPDATE maksuera
SET lukko = NULL, tila = 'lahetetty'
WHERE numero = :numero;

-- name: merkitse-maksueralle-lahetysvirhe!
-- Merkitsee maksuerän lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE maksuera
SET tila = 'virhe', lukko = NULL, lukittu = NULL
WHERE numero = :numero;

-- name: merkitse-tyypin-maksuerat-likaisiksi!
-- Merkitsee kaikki annetun tyypin mukaiset maksuerät likaisi. Vain voimassaolevat tai ne joiden vanhenemisesta on alle 3 kk.
UPDATE maksuera
SET likainen = TRUE
WHERE tyyppi = :tyyppi :: maksueratyyppi AND
      toimenpideinstanssi IN (select id from toimenpideinstanssi where loppupvm > current_timestamp - INTERVAL '3 months');

-- name: merkitse-toimenpiteen-maksuerat-likaisiksi!
-- Merkitsee kaikki annetun toimenpiteen mukaiset maksuerät likaisi, jos toimenpideinstanssi on voimassa tai sen vanhenemisesta on alle 3 kk.
UPDATE maksuera
SET likainen = TRUE,
muokattu = CURRENT_TIMESTAMP
WHERE toimenpideinstanssi = :tpi AND
    toimenpideinstanssi IN (select id from toimenpideinstanssi where loppupvm > current_timestamp - INTERVAL '3 months');;

-- name: luo-maksuera<!
-- Luo uuden maksuerän.
INSERT INTO maksuera (toimenpideinstanssi, tyyppi, nimi, likainen, luotu)
VALUES (:toimenpideinstanssi, :tyyppi :: maksueratyyppi, :nimi, TRUE, current_timestamp);

-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT numero
              FROM maksuera
              WHERE numero = :numero :: BIGINT);

-- name: hae-maksueran-urakka
-- single?: true
SELECT u.id
FROM urakka u
  JOIN toimenpideinstanssi tpi ON u.id = tpi.urakka
  JOIN maksuera m ON tpi.id = m.toimenpideinstanssi
WHERE m.numero = :numero;


--name: hae-urakan-maksueratiedot
SELECT numero,
       toimenpideinstanssi,
       nimi,
       tyyppi
  FROM maksuera
 WHERE toimenpideinstanssi in (SELECT id FROM toimenpideinstanssi WHERE urakka = :urakka_id);


-- name: hae-kanavaurakan-maksuerien-summat
-- Jos muokkaat tätä, joudut todennäköisesti muokkaamaan myös kanavien_laskutusyhteenveto.sql
SELECT
  tpi.id as "tpi_id",

  -- kokonaishintaisten töiden summat
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo
   WHERE toimenpideinstanssi = tpi.id) AS "kokonaishintainen",

  -- lisatyo
  (SELECT COALESCE(sum(tyo.maara * yht.yksikkohinta), 0)
   FROM kan_toimenpide ktp
     JOIN kan_laskutettavat_hinnoittelut laskutettavat ON ktp.id = laskutettavat."toimenpide-id"
     JOIN kan_tyo tyo ON (tyo.toimenpide = ktp.id AND tyo.poistettu IS NOT TRUE)
     JOIN yksikkohintainen_tyo yht ON yht.tehtava = tyo."toimenpidekoodi-id" AND
                                      ktp.pvm BETWEEN yht.alkupvm AND yht.loppupvm
   WHERE ktp.tyyppi = 'muutos-lisatyo' :: KAN_TOIMENPIDETYYPPI AND
         tyo.toimenpide = ktp.id AND
         ktp.poistettu IS NOT TRUE AND
         ktp.toimenpideinstanssi = tpi.id)
  +
  (SELECT COALESCE(SUM(k_hinta.summa * (1.0 + (k_hinta.yleiskustannuslisa / 100))), 0) +
          COALESCE(SUM((k_hinta.maara * k_hinta.yksikkohinta) * (1.0 + (k_hinta.yleiskustannuslisa / 100))), 0)
   FROM kan_toimenpide ktp
     JOIN kan_laskutettavat_hinnoittelut laskutettavat ON ktp.id = laskutettavat."toimenpide-id"
     JOIN kan_hinta k_hinta ON k_hinta.toimenpide = ktp.id AND k_hinta.poistettu IS NOT TRUE
   WHERE ktp.toimenpideinstanssi = tpi.id AND
         ktp.poistettu IS NOT TRUE)    AS "lisatyo",

  -- muut kustannukset
  (SELECT COALESCE(SUM(rahasumma), 0)
   FROM erilliskustannus
   WHERE tpi.id = toimenpideinstanssi) AS "muu",

  -- sakot
  (SELECT COALESCE(SUM(maara), 0)
   FROM sanktio
   WHERE tpi.id = toimenpideinstanssi) AS "sakko"

FROM toimenpideinstanssi tpi
WHERE tpi.urakka = :urakka
GROUP BY tpi.id
