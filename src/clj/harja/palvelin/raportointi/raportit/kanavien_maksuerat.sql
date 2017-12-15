-- name: hae-kanavaurakan-maksuerien-summat
SELECT
  tpi.id,

  -- kokonaishintaisten töiden summat
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo
   WHERE toimenpideinstanssi = tpi.id) AS "kokonaishintainen",

  -- lisatyo
  (SELECT COALESCE(sum(tyo.maara * yht.yksikkohinta), 0)
   FROM kan_toimenpide ktp
     JOIN kan_tyo tyo ON (tyo.toimenpide = ktp.id AND tyo.poistettu IS NOT TRUE)
     JOIN yksikkohintainen_tyo yht ON yht.tehtava = tyo."toimenpidekoodi-id" AND
                                      ktp.pvm BETWEEN yht.alkupvm AND yht.loppupvm
   WHERE ktp.tyyppi = 'muutos-lisatyo' :: KAN_TOIMENPIDETYYPPI AND
         tyo.toimenpide = ktp.id AND
         ktp.poistettu IS NOT TRUE AND
         ktp.toimenpideinstanssi = tpi.id)
  +
  (SELECT COALESCE(SUM(k_hinta.summa * (1.0 + (yleiskustannuslisa / 100))), 0) +
          COALESCE(SUM((k_hinta.maara * k_hinta.yksikkohinta) * (1.0 + (yleiskustannuslisa / 100))), 0)
   FROM kan_toimenpide ktp
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
