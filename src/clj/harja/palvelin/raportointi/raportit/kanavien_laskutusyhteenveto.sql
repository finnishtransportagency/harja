-- name: hae-kokonaishintaiset-toimenpiteet
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

-- name: hae-sanktiot
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
SELECT tpi.id as "tpi-id", tpi.nimi as "tpi-nimi",
       COALESCE(SUM(rahasumma), 0) AS "toteutunut-maara"
  FROM erilliskustannus ek
      LEFT JOIN toimenpideinstanssi tpi ON ek.toimenpideinstanssi = tpi.id
 WHERE ek.poistettu IS NOT TRUE
      AND ek.pvm <= :loppupvm
      AND ek.pvm >= :alkupvm
      AND tpi.urakka = :urakkaid
 GROUP BY tpi.id;