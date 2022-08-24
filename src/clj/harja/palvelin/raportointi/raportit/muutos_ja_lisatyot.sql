-- name: hae-muutos-ja-lisatyot-raportille
-- Hakee muutos-, lisä- ja äkilliset hoitotyötoteumat raportille
SELECT
  t.tyyppi,
  t.alkanut,
  t.lisatieto,
  tt.id                                                  AS tehtava_id,
  tt.toteuma                                             AS toteuma_id,
  tt.toimenpidekoodi                                     AS tehtava_toimenpidekoodi,
  tt.maara                                               AS tehtava_maara,
  tt.lisatieto                                           AS tehtava_lisatieto,
  tt.paivan_hinta                                        AS tehtava_paivanhinta,
  tt.indeksi                                             AS tehtava_indeksi,
  mht.yksikkohinta                                       AS tehtava_yksikkohinta,
  COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta) AS tehtava_summa,
  (SELECT korotus
   FROM laske_kuukauden_indeksikorotus(
       (SELECT EXTRACT(YEAR FROM t.alkanut) :: INTEGER),
       (SELECT EXTRACT(MONTH FROM t.alkanut) :: INTEGER),
       CASE WHEN tt.indeksi IS TRUE
         THEN (SELECT indeksi
               FROM urakka
               WHERE id = u.id)
       ELSE
         NULL
       END,
       COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta),
       indeksilaskennan_perusluku(u.id)))    AS korotus,
  u.id                                                   AS urakka_id,
  u.nimi                                                 AS urakka_nimi,
  hy.id                                                  AS hallintayksikko_id,
  hy.nimi                                                AS hallintayksikko_nimi,
  lpad(cast(hy.elynumero as varchar), 2, '0')            AS hallintayksikko_elynumero,
  tpi.id                                                 AS tpi_id,
  tpi.nimi                                               AS tpi_nimi,
  t.sopimus                                              AS sopimus_id,
  s.sampoid                                              AS sopimus_sampoid,
  tpk4.emo                                               AS tehtava_emo,
  tpk4.nimi                                              AS tehtava_nimi
FROM toteuma_tehtava tt
  JOIN toteuma t ON (tt.toteuma = t.id AND
                     t.tyyppi::TEXT IN (:tyotyypit) AND
                     t.poistettu IS NOT TRUE)
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tt.toimenpidekoodi
  JOIN toimenpideinstanssi tpi
    ON (tpi.toimenpide = tpk4.emo AND tpi.urakka = t.urakka)
  LEFT JOIN muutoshintainen_tyo mht
    ON (mht.tehtava = tt.toimenpidekoodi AND mht.urakka = t.urakka AND
        mht.sopimus = t.sopimus)
  JOIN sopimus s ON t.sopimus = s.id
  JOIN urakka u ON t.urakka = u.id
  JOIN organisaatio hy ON hy.id = u.hallintayksikko
WHERE
  tt.poistettu IS NOT TRUE AND
  mht.poistettu IS NOT TRUE AND
  ((:urakka_annettu IS FALSE AND u.urakkanro IS NOT NULL) OR u.id = :urakka)
  AND (:urakka_annettu IS TRUE OR (:urakka_annettu IS FALSE AND
                                   (:urakkatyyppi :: urakkatyyppi IS NULL OR
                                    u.tyyppi =
                                    :urakkatyyppi :: urakkatyyppi)))
  AND (:hallintayksikko_annettu IS FALSE OR
       u.id IN (SELECT id
                FROM urakka
                WHERE hallintayksikko = :hallintayksikko))
  AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                    FROM toimenpidekoodi tpk
                                                    WHERE tpk.emo = :tpi))
  AND t.alkanut BETWEEN :alku AND :loppu;


-- name: hae-tyypin-ja-hyn-mukaan-ryhmitellyt-muutos-ja-lisatyot-raportille
-- Hakee tyypin mukaan ryhmitellyt muutos ja lisatyöt raportille
SELECT
  t.tyyppi,
  sum(COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta)) AS tehtava_summa,
  sum((SELECT korotus
   FROM laske_kuukauden_indeksikorotus(
       (SELECT EXTRACT(YEAR FROM t.alkanut) :: INTEGER),
       (SELECT EXTRACT(MONTH FROM t.alkanut) :: INTEGER),
       CASE WHEN tt.indeksi IS TRUE
         THEN (SELECT indeksi
               FROM urakka
               WHERE id = u.id)
       ELSE
         NULL
       END,
       COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta),
       indeksilaskennan_perusluku(u.id))))   AS korotus,
  hy.id                                                  AS hallintayksikko_id,
  hy.nimi                                                AS hallintayksikko_nimi,
  lpad(cast(hy.elynumero as varchar), 2, '0')            AS hallintayksikko_elynumero
FROM toteuma_tehtava tt
  JOIN toteuma t ON (tt.toteuma = t.id AND
                     t.tyyppi::TEXT  IN (:tyotyypit) AND
                     t.poistettu IS NOT TRUE)
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tt.toimenpidekoodi
  JOIN toimenpideinstanssi tpi
    ON (tpi.toimenpide = tpk4.emo AND tpi.urakka = t.urakka)
  LEFT JOIN muutoshintainen_tyo mht
    ON (mht.tehtava = tt.toimenpidekoodi AND mht.urakka = t.urakka AND
        mht.sopimus = t.sopimus)
  JOIN sopimus s ON t.sopimus = s.id
  JOIN urakka u ON t.urakka = u.id
  JOIN organisaatio hy ON hy.id = u.hallintayksikko
WHERE
  tt.poistettu IS NOT TRUE AND
  mht.poistettu IS NOT TRUE AND
  ((:urakka_annettu IS FALSE AND u.urakkanro IS NOT NULL) OR u.id = :urakka)
  AND (:urakka_annettu IS TRUE OR (:urakka_annettu IS FALSE AND
                                   (:urakkatyyppi :: urakkatyyppi IS NULL OR (
                                       CASE WHEN :urakkatyyppi = 'hoito' THEN
                                            u.tyyppi IN ('hoito', 'teiden-hoito')
                                       ELSE u.tyyppi = :urakkatyyppi :: urakkatyyppi
                                       END
                                       )
                                    )))
  AND (:hallintayksikko_annettu IS FALSE OR
       u.id IN (SELECT id
                FROM urakka
                WHERE hallintayksikko = :hallintayksikko))
  AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                    FROM toimenpidekoodi tpk
                                                    WHERE tpk.emo = :tpi))
  AND t.alkanut BETWEEN :alku AND :loppu
GROUP BY hy.id, t.tyyppi;

-- name: hae-tyypin-ja-urakan-mukaan-ryhmitellyt-hyn-muutos-ja-lisatyot-raportille
SELECT
  t.tyyppi,
  sum(COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta)) AS tehtava_summa,
  sum((SELECT korotus
       FROM laske_kuukauden_indeksikorotus(
           (SELECT EXTRACT(YEAR FROM t.alkanut) :: INTEGER),
           (SELECT EXTRACT(MONTH FROM t.alkanut) :: INTEGER),
           CASE WHEN tt.indeksi IS TRUE
             THEN (SELECT indeksi
                   FROM urakka
                   WHERE id = u.id)
           ELSE
             NULL
           END,
           COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta),
           indeksilaskennan_perusluku(u.id))))   AS korotus,
  u.id                                                  AS hallintayksikko_id, -- PARDON, kutsutaan hallintayksiköksi mutta on urakkaid ja nimi, koska raportti toimii sukkana mitään muuttamatta
  u.nimi                                                AS hallintayksikko_nimi
FROM toteuma_tehtava tt
  JOIN toteuma t ON (tt.toteuma = t.id AND
                     t.tyyppi::TEXT  IN (:tyotyypit) AND
                     t.poistettu IS NOT TRUE)
  JOIN toimenpidekoodi tpk4 ON tpk4.id = tt.toimenpidekoodi
  JOIN toimenpideinstanssi tpi
    ON (tpi.toimenpide = tpk4.emo AND tpi.urakka = t.urakka)
  LEFT JOIN muutoshintainen_tyo mht
    ON (mht.tehtava = tt.toimenpidekoodi AND mht.urakka = t.urakka AND
        mht.sopimus = t.sopimus)
  JOIN sopimus s ON t.sopimus = s.id
  JOIN urakka u ON t.urakka = u.id
  JOIN organisaatio hy ON hy.id = u.hallintayksikko
WHERE
  tt.poistettu IS NOT TRUE AND
  mht.poistettu IS NOT TRUE
  AND (:urakkatyyppi :: urakkatyyppi IS NULL OR (
    CASE WHEN :urakkatyyppi = 'hoito' THEN
         u.tyyppi IN ('hoito', 'teiden-hoito')
    ELSE
        u.tyyppi = :urakkatyyppi :: urakkatyyppi
    END))
  AND u.id IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko)
  AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                    FROM toimenpidekoodi tpk
                                                    WHERE tpk.emo = :tpi))
  AND t.alkanut BETWEEN :alku AND :loppu
GROUP BY u.id, t.tyyppi;
