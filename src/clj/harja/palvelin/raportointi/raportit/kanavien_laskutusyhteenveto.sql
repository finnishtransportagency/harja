-- name: hae-kokonaishintaiset-toimenpiteet
-- Jos muokkaat tätä, joudut todennäköisesti muokkaamaan myös maksuerat.sql / hae-kanavaurakan-maksuerien-summat
SELECT tpi.id as "tpi-id", tpi.nimi as "tpi-nimi",
       (SELECT COALESCE(SUM(summa), 0)
        FROM kokonaishintainen_tyo kt
        WHERE tpi.urakka = :urakkaid AND tpi.id = kt.toimenpideinstanssi
              -- Kok. hint. suunnittelu osuu aikavälille jos eka päivä osuu (välin tulisi aina olla kuukausiväli)
              AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') >= :alkupvm
              AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') <= :loppupvm) AS "suunniteltu-maara",
       (SELECT COALESCE(SUM(summa), 0)
        FROM kokonaishintainen_tyo kt
        WHERE tpi.urakka = :urakkaid AND tpi.id = kt.toimenpideinstanssi
              -- Työ on toteutunut, jos sen maksupvm on aikavälillä
              AND maksupvm >= :alkupvm
              AND maksupvm <= :loppupvm)                                                         AS "toteutunut-maara"
FROM toimenpideinstanssi tpi WHERE tpi.urakka = :urakkaid
GROUP BY tpi.id;


-- name: hae-muutos-ja-lisatyot
-- Jos muokkaat tätä, joudut todennäköisesti muokkaamaan myös maksuerat.sql / hae-kanavaurakan-maksuerien-summat
-- Tarkista myös kanavien_muutos_ja_liastyot.sql
SELECT tpi.id as "tpi-id", tpi.nimi as "tpi-nimi",
  COALESCE(SUM((hinta.summa * (1.0 + (hinta.yleiskustannuslisa / 100)))), 0) as summat,
  COALESCE(SUM((hinta.maara * hinta.yksikkohinta) * (1.0 + (hinta.yleiskustannuslisa / 100))), 0) as summat_kan_hinta_yksikkohinnalla,

  (SELECT COALESCE(sum(tyo.maara * yht.yksikkohinta))
   FROM kan_toimenpide tp
     JOIN kan_laskutettavat_hinnoittelut laskutettavat ON tp.id = laskutettavat."toimenpide-id"
     JOIN kan_tyo tyo ON (tyo.toimenpide = tp.id AND tyo.poistettu IS NOT TRUE)
     JOIN yksikkohintainen_tyo yht ON yht.tehtava = tyo."toimenpidekoodi-id" AND
                                      tp.pvm BETWEEN yht.alkupvm AND yht.loppupvm
   WHERE tp.tyyppi = 'muutos-lisatyo' :: KAN_TOIMENPIDETYYPPI AND
         tp.pvm BETWEEN :alkupvm AND :loppupvm AND
         tyo.toimenpide = tp.id AND
         tp.poistettu IS NOT TRUE) as summat_yht_yksikkohinnalla

FROM kan_toimenpide ktp
  JOIN kan_laskutettavat_hinnoittelut laskutettavat ON ktp.id = laskutettavat."toimenpide-id"
  JOIN kan_hinta hinta ON (hinta.toimenpide = ktp.id AND hinta.poistettu IS NOT TRUE)
  JOIN toimenpideinstanssi tpi ON tpi.id = ktp.toimenpideinstanssi
WHERE ktp.tyyppi = 'muutos-lisatyo' :: KAN_TOIMENPIDETYYPPI
      AND ktp.pvm >= :alkupvm
      AND ktp.pvm <= :loppupvm
      AND ktp.urakka = :urakkaid
      AND ktp.poistettu IS NOT TRUE
GROUP BY tpi.id;

-- name: hae-sanktiot
-- Jos muokkaat tätä, joudut todennäköisesti muokkaamaan myös maksuerat.sql / hae-kanavaurakan-maksuerien-summat
SELECT tpi.id as "tpi-id", tpi.nimi as "tpi-nimi",
       COALESCE(SUM(maara), 0) AS "toteutunut-maara"
  FROM sanktio s
       LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
 WHERE s.poistettu IS NOT TRUE
       AND s.perintapvm <= :loppupvm
       AND s.perintapvm >= :alkupvm
       AND tpi.urakka = :urakkaid
 GROUP BY tpi.id;

-- name: hae-erilliskustannukset
-- Jos muokkaat tätä, joudut todennäköisesti muokkaamaan myös maksuerat.sql / hae-kanavaurakan-maksuerien-summat
SELECT tpi.id as "tpi-id", tpi.nimi as "tpi-nimi",
       COALESCE(SUM(rahasumma), 0) AS "toteutunut-maara"
  FROM erilliskustannus ek
      LEFT JOIN toimenpideinstanssi tpi ON ek.toimenpideinstanssi = tpi.id
 WHERE ek.poistettu IS NOT TRUE
      AND ek.pvm <= :loppupvm
      AND ek.pvm >= :alkupvm
      AND tpi.urakka = :urakkaid
 GROUP BY tpi.id;
