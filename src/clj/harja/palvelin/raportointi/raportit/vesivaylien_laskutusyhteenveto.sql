-- name: hae-kokonaishintaiset-toimenpiteet
SELECT
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo kt
     LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
     LEFT JOIN toimenpideinstanssi_vesivaylat tpi_vv ON kt.toimenpideinstanssi = tpi_vv."toimenpideinstanssi-id"
   WHERE tpi.urakka = :urakkaid
         AND tpi_vv.vaylatyyppi = :vaylatyyppi :: VV_VAYLATYYPPI
         -- Kok. hint. suunnittelu osuu aikavälille jos eka päivä osuu (välin tulisi aina olla kuukausiväli)
         AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') >= :alkupvm
         AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') <= :loppupvm) AS "suunniteltu-maara",
  (SELECT COALESCE(SUM(summa), 0)
   FROM kokonaishintainen_tyo kt
     LEFT JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
     LEFT JOIN toimenpideinstanssi_vesivaylat tpi_vv ON kt.toimenpideinstanssi = tpi_vv."toimenpideinstanssi-id"
   WHERE tpi.urakka = :urakkaid
         AND tpi_vv.vaylatyyppi = :vaylatyyppi :: VV_VAYLATYYPPI
         -- Työ on toteutunut, jos sen maksupvm on aikavälillä
         AND maksupvm >= :alkupvm
         AND maksupvm <= :loppupvm)                                                         AS "toteutunut-maara";

-- name: hae-sanktiot
SELECT COALESCE(SUM(maara), 0) AS summa
FROM sanktio s
  LEFT JOIN toimenpideinstanssi tpi ON s.toimenpideinstanssi = tpi.id
WHERE s.poistettu IS NOT TRUE
      AND s.perintapvm <= :loppupvm
      AND s.perintapvm >= :alkupvm
      AND tpi.urakka = :urakkaid;

-- name: hae-erilliskustannukset
SELECT COALESCE(SUM(rahasumma), 0) AS summa
FROM erilliskustannus ek
  LEFT JOIN toimenpideinstanssi tpi ON ek.toimenpideinstanssi = tpi.id
WHERE ek.poistettu IS NOT TRUE
      AND ek.laskutuskuukausi <= :loppupvm
      AND ek.laskutuskuukausi >= :alkupvm
      AND tpi.urakka = :urakkaid;
