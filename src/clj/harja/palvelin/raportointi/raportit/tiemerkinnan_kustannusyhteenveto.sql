-- name: muodosta-tiemerkinnan-kustannusyhteenveto
SELECT
  -- FIXME AIKARAJAT (mutta ei voi kaikkiin laittaa?)
  (SELECT SUM(summa)
   FROM kokonaishintainen_tyo
   WHERE toimenpideinstanssi IN
         (SELECT id
          FROM toimenpideinstanssi
          WHERE urakka = :urakkaid))
    AS "kokonaishintainen-osa",
  (SELECT SUM(hinta)
   FROM tiemerkinnan_yksikkohintainen_toteuma
   WHERE urakka = :urakkaid
         AND poistettu IS NOT TRUE
         AND hintatyyppi = 'toteuma'
         AND (yllapitokohde IS NULL OR (yllapitokohde IS NOT NULL AND (SELECT poistettu
                                                                       FROM yllapitokohde
                                                                       WHERE id = yllapitokohde) IS NOT
                                                                      TRUE)))
    AS "yksikkohintainen-osa",
  (SELECT SUM(hinta)
   FROM yllapito_muu_toteuma
   WHERE urakka = :urakkaid AND poistettu IS NOT
                                TRUE)
    AS "muut-tyot",
  (SELECT SUM(maara)
   FROM sanktio
   WHERE poistettu IS NOT TRUE
         AND maara < 0
         AND laatupoikkeama IN (SELECT id
                                FROM laatupoikkeama lp
                                WHERE urakka = :urakkaid AND poistettu IS NOT TRUE
                                      AND (lp.yllapitokohde IS NULL OR
                                           (lp.yllapitokohde IS NOT NULL AND (SELECT poistettu
                                                                              FROM yllapitokohde
                                                                              WHERE id = lp.yllapitokohde
                                                                                         IS NOT TRUE)))))
    AS "sakot",
  (SELECT SUM(maara)
   FROM sanktio
   WHERE poistettu IS NOT TRUE
         AND maara > 0
         AND laatupoikkeama IN (SELECT id
                                FROM laatupoikkeama lp
                                WHERE urakka = :urakkaid AND poistettu IS NOT TRUE
                                      AND (lp.yllapitokohde IS NULL OR
                                           (lp.yllapitokohde IS NOT NULL AND (SELECT poistettu
                                                                              FROM yllapitokohde
                                                                              WHERE id = lp.yllapitokohde
                                                                                         IS NOT TRUE)))))
    AS "bonukset"
FROM urakka
WHERE id = :urakkaid;
