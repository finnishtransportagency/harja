-- name: muodosta-tiemerkinnan-kustannusyhteenveto
SELECT
  -- FIXME AIKARAJAT (mutta ei voi kaikkiin laittaa?)
  COALESCE((SELECT SUM(summa)
   FROM kokonaishintainen_tyo kt
   WHERE toimenpideinstanssi IN
         (SELECT id
          FROM toimenpideinstanssi
          WHERE urakka = :urakkaid)
          AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') >= :alkupvm
          AND to_date((kt.vuosi || '-' || kt.kuukausi || '-01'), 'YYYY-MM-DD') <= :loppupvm), 0)
    AS "kokonaishintainen-osa",
  COALESCE((SELECT SUM(hinta)
   FROM tiemerkinnan_yksikkohintainen_toteuma
   WHERE urakka = :urakkaid
         AND poistettu IS NOT TRUE
         AND hintatyyppi = 'toteuma'
         AND (yllapitokohde IS NULL OR (yllapitokohde IS NOT NULL AND (SELECT poistettu
                                                                       FROM yllapitokohde
                                                                       WHERE id = yllapitokohde) IS NOT
                                                                      TRUE))), 0)
    AS "yksikkohintainen-osa",
  COALESCE((SELECT SUM(hinta)
   FROM yllapito_muu_toteuma
   WHERE urakka = :urakkaid
   AND pvm >= :alkupvm AND pvm <= :loppupvm
   AND poistettu IS NOT TRUE), 0)
    AS "muut-tyot",
  COALESCE((SELECT SUM(maara)
   FROM sanktio
   WHERE poistettu IS NOT TRUE
         AND maara < 0
         AND laatupoikkeama IN (SELECT id
                                FROM laatupoikkeama lp
                                WHERE urakka = :urakkaid AND poistettu IS NOT TRUE
                                      AND aika >= :alkupvm AND aika <= :loppupvm
                                      AND (lp.yllapitokohde IS NULL OR
                                           (lp.yllapitokohde IS NOT NULL AND (SELECT poistettu
                                                                              FROM yllapitokohde
                                                                              WHERE id = lp.yllapitokohde
                                                                                         IS NOT TRUE))))), 0)
    AS "sakot",
  COALESCE((SELECT SUM(maara)
   FROM sanktio
   WHERE poistettu IS NOT TRUE
         AND maara > 0
         AND laatupoikkeama IN (SELECT id
                                FROM laatupoikkeama lp
                                WHERE urakka = :urakkaid AND poistettu IS NOT TRUE
                                      AND aika >= :alkupvm AND aika <= :loppupvm
                                      AND (lp.yllapitokohde IS NULL OR
                                           (lp.yllapitokohde IS NOT NULL AND (SELECT poistettu
                                                                              FROM yllapitokohde
                                                                              WHERE id = lp.yllapitokohde
                                                                                         IS NOT TRUE))))), 0)
    AS "bonukset"
FROM urakka
WHERE id = :urakkaid;
