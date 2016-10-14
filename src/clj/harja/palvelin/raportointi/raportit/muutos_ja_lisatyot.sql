-- name: hae-muutos-ja-lisatyot-raportille
-- Hakee muutos-, lisä- ja äkilliset hoitotyötoteumat raportille
SELECT
  x.*,
  (x.ind).korotettuna,
  (x.ind).korotus
FROM    (SELECT
           t.tyyppi,
           t.alkanut,
           tt.id              AS tehtava_id,
           tt.toteuma         AS toteuma_id,
           tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
           tt.maara           AS tehtava_maara,
           tt.lisatieto       AS tehtava_lisatieto,
           tt.paivan_hinta    AS tehtava_paivanhinta,
           tt.indeksi         AS tehtava_indeksi,
           mht.yksikkohinta   AS tehtava_yksikkohinta,
           COALESCE(tt.paivan_hinta, tt.maara * mht.yksikkohinta) AS tehtava_summa,
           laske_kuukauden_indeksikorotus(
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
               hoitourakan_indeksilaskennan_perusluku(u.id)) AS ind,
           u.id               AS urakka_id,
           u.nimi             AS urakka_nimi,
           tpi.id             AS tpi_id,
           tpi.nimi           AS tpi_nimi,
           t.sopimus          AS sopimus_id,
           s.sampoid          AS sopimus_sampoid,
           tpk4.emo           AS tehtava_emo,
           tpk4.nimi          AS tehtava_nimi
         FROM toteuma_tehtava tt
           JOIN toteuma t ON (tt.toteuma = t.id AND
                              t.tyyppi IN ('akillinen-hoitotyo' :: toteumatyyppi,
                                           'lisatyo' :: toteumatyyppi,
                                           'muutostyo' :: toteumatyyppi,
                                           'vahinkojen-korjaukset' :: toteumatyyppi) AND
                              t.poistettu IS NOT TRUE)
           JOIN toimenpidekoodi tpk4 ON tpk4.id = tt.toimenpidekoodi
           JOIN toimenpideinstanssi tpi
             ON (tpi.toimenpide = tpk4.emo AND tpi.urakka = t.urakka)
           LEFT JOIN muutoshintainen_tyo mht
             ON (mht.tehtava = tt.toimenpidekoodi AND mht.urakka = t.urakka AND
                 mht.sopimus = t.sopimus)
           JOIN sopimus s ON t.sopimus = s.id
           JOIN urakka u ON t.urakka = u.id
         WHERE
           tt.poistettu IS NOT TRUE AND
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
           AND t.alkanut :: DATE BETWEEN :alku AND :loppu) x;