-- name: listaa-urakan-yksikkohintaiset-tyot
-- Hakee kaikki yksikkohintaiset-tyot
SELECT
  yt.id,
  yt.alkupvm,
  yt.loppupvm,
  yt.maara,
  yt.yksikko,
  yt.yksikkohinta,
  yt.indeksi_kaytossa,
  yt.indeksikorjattu_yksikkohinta,
  yt.tehtava,
  yt.urakka,
  yt.sopimus,
  yt.arvioitu_kustannus,
  yt.kuukausi,
  yt.vuosi,
  viimeisin.viimeisin_muokkaus,
  viimeisin.viimeisin_muokkaaja,
  tk.id   AS tehtavan_id,
  tk.nimi AS tehtavan_nimi
FROM yksikkohintainen_tyo yt
  LEFT JOIN tehtava tk ON yt.tehtava = tk.id
  LEFT JOIN LATERAL (
    SELECT
        GREATEST(COALESCE(yt2.muokattu, yt2.luotu), yt2.luotu)
                AS viimeisin_muokkaus,
        CASE
            WHEN k.piilota_nimi IS TRUE THEN 'Järjestelmän ylläpito'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
            END AS viimeisin_muokkaaja
    FROM yksikkohintainen_tyo yt2
             LEFT JOIN kayttaja k
                       ON COALESCE(
                              NULLIF(TRIM(yt2.muokkaaja), ''),
                              yt2.luoja::text
                          ) = k.id::text
    WHERE yt2.urakka = yt.urakka AND (yt2.muokattu IS NOT NULL OR yt2.luotu IS NOT NULL)
    ORDER BY viimeisin_muokkaus DESC NULLS LAST, yt2.id DESC
        LIMIT 1
) viimeisin ON TRUE
WHERE urakka = :urakka
ORDER BY tk.nimi;

-- name: hae-urakan-sopimuksen-yksikkohintaiset-tehtavat
-- Urakan sopimuksen yksikköhintaiset tehtävät
SELECT
  tpk.id,
  tpk.nimi,
  tpk.yksikko
FROM tehtava tpk
WHERE
  NOT poistettu AND
  id IN (
    SELECT DISTINCT (tehtava)
    FROM yksikkohintainen_tyo
    WHERE urakka = :urakkaid AND sopimus = :sopimusid) AND
  hinnoittelu @> '{yksikkohintainen}'
ORDER BY tpk.nimi;

-- name: paivita-urakan-yksikkohintainen-tyo!
-- Päivittää urakan hoitokauden yksikkohintaiset tyot
UPDATE yksikkohintainen_tyo
SET maara                           = :maara,
    yksikko                         = :yksikko,
    yksikkohinta                    = :yksikkohinta,
    indeksi_kaytossa                = :indeksi_kaytossa,
    indeksikorjattu_yksikkohinta    = :indeksikorjattu_yksikkohinta,
    arvioitu_kustannus              = :arvioitu_kustannus,
    muokkaaja                       = :kayttaja,
    muokattu                        = current_timestamp
WHERE urakka = :urakka
  AND sopimus = :sopimus
  AND tehtava = :tehtava
  AND (((:alkupvm::DATE IS NULL AND alkupvm IS NULL) OR alkupvm = :alkupvm) AND ((:loppupvm::DATE IS NULL AND loppupvm IS NULL) OR loppupvm = :loppupvm))
  AND (((:kuukausi::INTEGER IS NULL AND kuukausi IS NULL) OR kuukausi = :kuukausi) AND ((:vuosi::INTEGER IS NULL AND vuosi IS NULL) OR vuosi = :vuosi));

-- name: lisaa-urakan-yksikkohintainen-tyo<!
INSERT INTO yksikkohintainen_tyo
(maara, yksikko, yksikkohinta,
 urakka, sopimus, tehtava,
 alkupvm, loppupvm, luoja, arvioitu_kustannus, luotu, kuukausi, vuosi,
 indeksi_kaytossa, indeksikorjattu_yksikkohinta)
VALUES (:maara, :yksikko, :yksikkohinta,
        :urakka, :sopimus, :tehtava,
        :alkupvm, :loppupvm, :kayttaja, :arvioitu_kustannus, current_timestamp,
        :kuukausi, :vuosi, :indeksi_kaytossa, :indeksikorjattu_yksikkohinta);

-- name: merkitse-kustannussuunnitelmat-likaisiksi!
-- Merkitsee yksikköhintaisia töitä vastaavat kustannussuunnitelmat likaisiksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE kustannussuunnitelma
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE maksuera IN (SELECT m.numero
                   FROM maksuera m
                     JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi AND tpi. loppupvm > current_timestamp - INTERVAL '3 months'
                     JOIN toimenpide emo ON emo.id = tpi.toimenpide
                     JOIN tehtava tpk ON tpk.emo = emo.id
                   WHERE m.tyyppi = 'yksikkohintainen' AND tpi.urakka = :urakka AND tpk.id IN (:tehtavat));

-- name: hae-yksikkohintaiset-tyot-kuukausittain-urakalle
-- Hakee yksikköhintaiset työt annetulle urakalle ja aikavälille summattuna kuukausittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  t4.id as tehtava_id,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi=t4.id
WHERE tot.urakka = :urakka
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM tehtava tpk WHERE tpk.emo=:tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, t4.yksikko, vuosi, kuukausi, t4.id
ORDER BY t4.nimi, t4.yksikko;

-- name: hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle
-- Hakee yksikköhintaiset työt annetulle hallintayksikolle ja aikavälille summattuna kuukausittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi=t4.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE elinvoimakeskus_id = :elinvoimakeskus
                           AND (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                           AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM tehtava tpk WHERE tpk.emo=:tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, yksikko, vuosi, kuukausi
ORDER BY t4.nimi, yksikko;

-- name: hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle-urakoittain
-- Hakee yksikköhintaiset työt annetulle hallintayksikolle ja aikavälille summattuna kuukausittain, eriteltynä urakoittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi=t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE elinvoimakeskus_id = :elinvoimakeskus
                           AND (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                           AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM tehtava tpk WHERE tpk.emo=:tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, yksikko, vuosi, kuukausi, u.id
ORDER BY t4.nimi, yksikko;

-- name: hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle
-- Hakee yksikköhintaiset työt koko maalle ja aikavälille summattuna kuukausittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi = t4.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                           WHERE (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                           AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM tehtava tpk WHERE tpk.emo=:tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, yksikko, vuosi, kuukausi
ORDER BY t4.nimi, yksikko;

-- name: hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle-urakoittain
-- Hakee yksikköhintaiset työt koko maalle ja aikavälille summattuna kuukausittain, eriteltynä urakoittain
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT
  extract(YEAR FROM tot.alkanut)::INT as vuosi,
  extract(MONTH FROM tot.alkanut)::INT as kuukausi,
  u.id AS urakka_id,
  u.nimi AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara) as toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi=t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                           WHERE (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                           AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM tehtava tpk WHERE tpk.emo=:tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, yksikko, vuosi, kuukausi, u.id
ORDER BY t4.nimi, yksikko;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-urakalle
-- Hakee yksikköhintaiset työt annetulle urakalle ja aikavälille, summattuna tehtävittäin
SELECT
  t4.nimi,
  t4.id                      AS tehtava_id,
  SUM(tt.maara)              AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi = t4.id
WHERE tot.urakka = :urakka
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM tehtava tpk
                                                        WHERE tpk.emo = :tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, t4.id
ORDER BY t4.nimi;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle
-- Hakee yksikköhintaiset työt annetulle hallintayksikölle aikavälille, summattuna tehtävittäin
SELECT
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi = t4.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE elinvoimakeskus_id = :elinvoimakeskus
                           AND (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                           AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM tehtava tpk
                                                        WHERE tpk.emo = :tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, t4.yksikko;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle-urakoittain
-- Hakee yksikköhintaiset työt hallintayksikstä aikavälille, summattuna tehtävittäin ja eriteltynä urakoittain
SELECT
  u.id                           AS urakka_id,
  u.nimi                         AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi = t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE elinvoimakeskus_id = :elinvoimakeskus
                           AND (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                           AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM tehtava tpk
                                                        WHERE tpk.emo = :tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, t4.yksikko, u.id
ORDER BY urakka_nimi, t4.nimi, t4.yksikko;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle
-- Hakee yksikköhintaiset työt koko maasta aikavälille, summattuna tehtävittäin
SELECT
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi = t4.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                          AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM tehtava tpk
                                                        WHERE tpk.emo = :tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, t4.yksikko
ORDER BY t4.nimi, t4.yksikko;

-- name: hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle-urakoittain
-- Hakee yksikköhintaiset työt koko maasta aikavälille, summattuna tehtävittäin ja eriteltynä urakoittain
SELECT
  u.id                           AS urakka_id,
  u.nimi                         AS urakka_nimi,
  t4.nimi,
  t4.yksikko,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma = tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi = t4.id
  JOIN urakka u ON tot.urakka = u.id
WHERE tot.urakka IN (SELECT id
                     FROM urakka
                     WHERE (TRUE IN (SELECT UNNEST(ARRAY[:urakkatyyppi]::urakkatyyppi[]) IS NULL) OR tyyppi = ANY(ARRAY[:urakkatyyppi]::urakkatyyppi[]))
                           AND urakkanro IS NOT NULL)
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = FALSE OR tt.toimenpidekoodi IN (SELECT tpk.id
                                                        FROM tehtava tpk
                                                        WHERE tpk.emo = :tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY t4.nimi, t4.yksikko, u.id
ORDER BY urakka_nimi, t4.nimi, t4.yksikko;

-- name: hae-yksikkohintaiset-tyot-per-paiva
-- Hakee yksikköhintaiset työt annetulle urakalle ja aikavälille summattuna päivittäin.
-- Optionaalisesti voidaan antaa vain tietty toimenpide, jonka työt haetaan.
SELECT date_trunc('day', tot.alkanut) AS pvm,
  t4.nimi,
  t4.id                          AS tehtava_id,
  tpi.nimi                       AS toimenpide,
  SUM(tt.maara)                  AS toteutunut_maara
FROM toteuma tot
  JOIN toteuma_tehtava tt ON tt.toteuma=tot.id AND tt.poistettu IS NOT TRUE
  JOIN tehtava t4 ON tt.toimenpidekoodi=t4.id
  JOIN toimenpideinstanssi tpi ON (tpi.toimenpide = t4.emo AND tpi.urakka = :urakka)
WHERE tot.urakka = :urakka
      AND (tot.alkanut >= :alkupvm AND tot.alkanut <= :loppupvm)
      AND (:rajaa_tpi = false OR tt.toimenpidekoodi IN (SELECT tpk.id FROM tehtava tpk WHERE tpk.emo=:tpi))
      AND tot.tyyppi = 'yksikkohintainen'::toteumatyyppi AND tot.poistettu IS NOT TRUE
GROUP BY pvm, t4.nimi, tpi.nimi, tehtava_id
ORDER BY pvm ASC, t4.nimi;

-- name: hae-vhu-urakan-hinnoittelemattomat-tehtavat
-- Hakee urakan tehtävät, joilla ei vielä ole valitun sopimuksen yksikköhintaa hoitokaudella.
-- Käytetään generoidessa lokaalisti yksikköhintoja testauksen nopeuttamiseksi.
SELECT DISTINCT
    t.id AS tehtava,
    t.yksikko AS "tehtavan-yksikko",
    yth.yksikko AS "yksikkohintaisen-tyon-yksikko",
    yth.maara,
    yth.arvioitu_kustannus
FROM tehtavaryhmaotsikko tro
         JOIN tehtavaryhma tr ON tr.tehtavaryhmaotsikko_id = tro.id
         JOIN tehtava t ON t.tehtavaryhma = tr.id
         JOIN toimenpideinstanssi tpi ON tpi.toimenpide = t.emo
    AND tpi.urakka = :urakkaid
         LEFT JOIN yksikkohintainen_tyo yth ON yth.urakka = :urakkaid
    AND yth.tehtava = t.id
    AND yth.sopimus = :sopimusid
    AND yth.alkupvm = :alkupvm::DATE
    AND yth.loppupvm = :loppupvm::DATE
WHERE tro.urakkatyyppi = 'vesivayla-hoito'
  AND t."vhu-tehtava?" IS TRUE
  AND t.poistettu IS NOT TRUE
  AND t.piilota IS NOT TRUE
  AND yth.yksikkohinta IS NULL
ORDER BY t.id;
