-- name: hae-urakan-toimenpiteet-ja-tehtavat
-- Hakee kaikki urakan 3. ja 4. tason toimenpiteet 
SELECT
  t4.id                AS t4_id,
  t4.koodi             AS t4_koodi,
  t4.nimi              AS t4_nimi,
  t4.yksikko           AS t4_yksikko,
  t4.hinnoittelu       AS t4_hinnoittelu,
  t4.jarjestys         AS t4_jarjestys,
  t3.id                AS t3_id,
  t3.koodi             AS t3_koodi,
  t3.nimi              AS t3_nimi,
  t2.id                AS t2_id,
  t2.koodi             AS t2_koodi,
  t2.nimi              AS t2_nimi,
  t1.id                AS t1_id,
  t1.koodi             AS t1_koodi,
  t1.nimi              AS t1_nimi
FROM toimenpidekoodi t4
  LEFT JOIN toimenpidekoodi t3 ON t3.id = t4.emo
  LEFT JOIN toimenpidekoodi t2 ON t2.id = t3.emo
  LEFT JOIN toimenpidekoodi t1 ON t1.id = t2.emo
WHERE t4.taso = 4 AND
      t3.id IN (SELECT toimenpide
                FROM toimenpideinstanssi
                WHERE urakka = :urakka);


-- name: hae-urakan-toimenpiteet
-- Hakee kaikki urakan 3. tason toimenpiteet
SELECT
  tpi.toimenpide AS id,
  tpi.nimi       AS tpi_nimi,
  tpi.id         AS tpi_id,
  t3.nimi        AS t3_nimi,
  t3.koodi       AS t3_koodi,
  t3.emo         AS t3_emo,
  t2.nimi        AS t2_nimi,
  t2.koodi       AS t2_koodi,
  t2.emo         AS t2_emo,
  t1.nimi        AS t1_nimi,
  t1.koodi       AS t1_koodi
FROM toimenpideinstanssi tpi
  LEFT JOIN toimenpidekoodi t3 ON tpi.toimenpide = t3.id
  LEFT JOIN toimenpidekoodi t2 ON t2.id = t3.emo
  LEFT JOIN toimenpidekoodi t1 ON t1.id = t2.emo
WHERE urakka = :urakka;


-- name: hae-urakan-muutoshintaiset-tehtavat
-- Hakee kaikki urakan 4. tason muutoshintaiset toimenpiteet
SELECT
  t4.id                AS t4_id,
  t4.koodi             AS t4_koodi,
  t4.nimi              AS t4_nimi,
  t4.yksikko           AS t4_yksikko,
  t4.jarjestys         AS t4_jarjestys,
  t3.id                AS t3_id,
  t3.koodi             AS t3_koodi,
  t3.nimi              AS t3_nimi,
  t2.id                AS t2_id,
  t2.koodi             AS t2_koodi,
  t2.nimi              AS t2_nimi,
  t1.id                AS t1_id,
  t1.koodi             AS t1_koodi,
  t1.nimi              AS t1_nimi
FROM toimenpidekoodi t4
  LEFT JOIN toimenpidekoodi t3 ON t3.id = t4.emo
  LEFT JOIN toimenpidekoodi t2 ON t2.id = t3.emo
  LEFT JOIN toimenpidekoodi t1 ON t1.id = t2.emo
WHERE t4.taso = 4 AND
      t4.hinnoittelu @> ARRAY['muutoshintainen'::hinnoittelutyyppi] AND
      t3.id IN (SELECT toimenpide
                FROM toimenpideinstanssi
                WHERE urakka = :urakka);

--name: hae-urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat
-- Hakee kaikki urakan 3. ja 4. tason toimenpiteet jotka ovat kokonaishintaisia
SELECT
    tpi.id               AS tpi_id,
    tpi.nimi             AS tpi_nimi,
    t4.id                AS t4_id,
    t4.koodi             AS t4_koodi,
    t4.nimi              AS t4_nimi,
    t4.yksikko           AS t4_yksikko,
    t4.hinnoittelu       AS t4_hinnoittelu,
    t4.voimassaolo_alkuvuosi AS t4_voimassaolo_alkuvuosi,
    t4.voimassaolo_loppuvuosi AS t4_voimassaolo_loppuvuosi,
    t4.jarjestys         AS t4_jarjestys,
    t3.id                AS t3_id,
    t3.koodi             AS t3_koodi,
    t3.nimi              AS t3_nimi,
    t2.id                AS t2_id,
    t2.koodi             AS t2_koodi,
    t2.nimi              AS t2_nimi,
    t1.id                AS t1_id,
    t1.koodi             AS t1_koodi,
    t1.nimi              AS t1_nimi
FROM toimenpidekoodi t4
         LEFT JOIN toimenpidekoodi t3 ON t3.id = t4.emo
         LEFT JOIN toimenpidekoodi t2 ON t2.id = t3.emo
         LEFT JOIN toimenpidekoodi t1 ON t1.id = t2.emo
         LEFT JOIN toimenpideinstanssi tpi ON t3.id = tpi.toimenpide
         JOIN urakka u on tpi.urakka = u.id
WHERE t4.taso = 4 AND
        t4.hinnoittelu @> ARRAY['kokonaishintainen'::hinnoittelutyyppi] AND
        t4.poistettu = FALSE AND
    (t4.voimassaolo_alkuvuosi IS NULL OR t4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER) AND
    (t4.voimassaolo_loppuvuosi IS NULL OR t4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER) AND
        tpi.urakka = :urakka;

-- name: hae-urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat
-- Hakee kaikki urakan 3. ja 4. tason toimenpiteet jotka eivät ole kokonaishintaisia
SELECT
  t4.id                AS t4_id,
  t4.koodi             AS t4_koodi,
  t4.nimi              AS t4_nimi,
  t4.yksikko           AS t4_yksikko,
  t4.hinnoittelu       AS t4_hinnoittelu,
  t4.voimassaolo_alkuvuosi AS t4_voimassaolo_alkuvuosi,
  t4.voimassaolo_loppuvuosi AS t4_voimassaolo_loppuvuosi,
  t4.jarjestys         AS t4_jarjestys,
  t3.id                AS t3_id,
  t3.koodi             AS t3_koodi,
  t3.nimi              AS t3_nimi,
  t2.id                AS t2_id,
  t2.koodi             AS t2_koodi,
  t2.nimi              AS t2_nimi,
  t1.id                AS t1_id,
  t1.koodi             AS t1_koodi,
  t1.nimi              AS t1_nimi
FROM toimenpidekoodi t4
  LEFT JOIN toimenpidekoodi t3 ON t3.id = t4.emo
  LEFT JOIN toimenpidekoodi t2 ON t2.id = t3.emo
  LEFT JOIN toimenpidekoodi t1 ON t1.id = t2.emo
WHERE t4.taso = 4 AND
      t4.hinnoittelu @> ARRAY['yksikkohintainen'::hinnoittelutyyppi] AND
      t4.id NOT IN (SELECT DISTINCT tehtava
                    FROM muutoshintainen_tyo
                    WHERE urakka = :urakka AND yksikkohinta IS NOT NULL AND poistettu IS FALSE) AND
      (t4.poistettu IS NOT TRUE OR t4.id IN (SELECT DISTINCT tehtava FROM yksikkohintainen_tyo WHERE urakka = :urakka)) AND
      t3.id IN (SELECT toimenpide
                FROM toimenpideinstanssi
                WHERE urakka = :urakka);

-- name: hae-urakan-muutoshintaiset-toimenpiteet-ja-tehtavat
-- Hakee kaikki urakan 3. ja 4. tason toimenpiteet jotka eivät ole kokonaishintaisia
-- ja joille ei ole annettu urakassa hintaa yksikköhintaisena työnä
SELECT
  t4.id                AS t4_id,
  t4.koodi             AS t4_koodi,
  t4.nimi              AS t4_nimi,
  t4.yksikko           AS t4_yksikko,
  t4.hinnoittelu       AS t4_hinnoittelu,
  t4.jarjestys         AS t4_jarjestys,
  t3.id                AS t3_id,
  t3.koodi             AS t3_koodi,
  t3.nimi              AS t3_nimi,
  t2.id                AS t2_id,
  t2.koodi             AS t2_koodi,
  t2.nimi              AS t2_nimi,
  t1.id                AS t1_id,
  t1.koodi             AS t1_koodi,
  t1.nimi              AS t1_nimi
FROM toimenpidekoodi t4
  LEFT JOIN toimenpidekoodi t3 ON t3.id = t4.emo
  LEFT JOIN toimenpidekoodi t2 ON t2.id = t3.emo
  LEFT JOIN toimenpidekoodi t1 ON t1.id = t2.emo
WHERE t4.taso = 4 AND
      t4.hinnoittelu @> ARRAY['muutoshintainen'::hinnoittelutyyppi] AND
      t4.id NOT IN (SELECT DISTINCT tehtava
                    FROM yksikkohintainen_tyo
                    WHERE urakka = :urakka AND yksikkohinta IS NOT NULL) AND
      t3.id IN (SELECT toimenpide
                FROM toimenpideinstanssi
                WHERE urakka = :urakka) AND
      t4.poistettu = FALSE
ORDER BY t4.jarjestys;

--name: hae-tuote-kolmostason-toimenpidekoodilla
SELECT tpk2.id, tpk2.nimi
FROM toimenpidekoodi tpk3
  JOIN toimenpidekoodi tpk2 ON tpk2.id = tpk3.emo
WHERE tpk3.id = :id;
