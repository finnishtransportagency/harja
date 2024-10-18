-- name: hae-urakan-rahavaraukset-ja-tehtavaryhmat
-- Palautetaan ensisijaisesti urakkakohtainen nimi, mutta jos sitä ei ole, niin defaultataan normaaliin nimeen.
SELECT rv.id,
       COALESCE(NULLIF(rvu.urakkakohtainen_nimi, ''), rv.nimi)                          AS nimi,
       TO_JSON(ARRAY_AGG(DISTINCT (ROW (tr.id, tr.nimi, tp.id, tpi.id, tr.jarjestys)))) AS tehtavaryhmat
  FROM rahavaraus rv
           JOIN rahavaraus_urakka rvu ON rvu.rahavaraus_id = rv.id AND rvu.urakka_id = :id
           JOIN rahavaraus_tehtava rvt ON rvt.rahavaraus_id = rv.id
           JOIN tehtava t ON t.id = rvt.tehtava_id
           JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma
           JOIN toimenpide tp ON t.emo = tp.id
           JOIN toimenpideinstanssi tpi ON tpi.toimenpide = tp.id AND tpi.urakka = :id
 GROUP BY rv.id, rvu.urakkakohtainen_nimi, rv.nimi, rv.jarjestys
 ORDER BY rv.jarjestys ASC;

-- name: hae-urakoiden-rahavaraukset
SELECT u.id                     AS "urakka-id",
       u.nimi                   AS "urakka-nimi",
       rvu.urakkakohtainen_nimi AS "urakkakohtainen-nimi",
       rv.id                    AS "id",
       rv.nimi
  FROM urakka u
           LEFT JOIN rahavaraus_urakka rvu ON rvu.urakka_id = u.id
           LEFT JOIN rahavaraus rv ON rv.id = rvu.rahavaraus_id
 WHERE u.tyyppi = 'teiden-hoito'
 ORDER BY rv.jarjestys ASC;

-- name: hae-rahavaraukset
SELECT r.id, r.nimi
  FROM rahavaraus r
 ORDER BY r.jarjestys ASC;

-- name: hae-rahavaraukset-tehtavineen
-- Haetaan kaikki rahavaraukset ja niihin liittyvät tehtävät
SELECT rv.id,
       rv.nimi,
       (SELECT ARRAY_AGG(ROW (t.id, t.nimi))
          FROM rahavaraus_tehtava rvt
                   JOIN tehtava t ON t.id = rvt.tehtava_id AND rvt.rahavaraus_id = rv.id) AS tehtavat
  FROM rahavaraus rv
 ORDER BY rv.jarjestys ASC;

-- name: hae-rahavaraukselle-mahdolliset-tehtavat
SELECT t.id, t.nimi, tro.otsikko
  FROM tehtava t
           JOIN tehtavaryhma tr ON t.tehtavaryhma = tr.id
           JOIN tehtavaryhmaotsikko tro ON tro.id = tr.tehtavaryhmaotsikko_id
 WHERE t.poistettu IS FALSE
   AND t.tehtavaryhma IS NOT NULL
 ORDER BY tr.jarjestys, t.jarjestys ASC;

-- name: hae-urakan-rahavaraus
SELECT ru.id, ru.urakkakohtainen_nimi, ru.rahavaraus_id
  FROM rahavaraus_urakka ru
 WHERE ru.urakka_id = :urakka-id
AND ru.rahavaraus_id = :rahavaraus-id;

--name: lisaa-uusi-rahavaraus<!
INSERT INTO rahavaraus (nimi, luoja, luotu) VALUES (:nimi, :kayttajaid, CURRENT_TIMESTAMP);

-- name: lisaa-urakan-rahavaraus<!
INSERT INTO rahavaraus_urakka (urakka_id, rahavaraus_id, urakkakohtainen_nimi, luoja, luotu)
VALUES (:urakkaid, :rahavarausid, :urakkakohtainen-nimi, :kayttaja, CURRENT_TIMESTAMP);

-- name: paivita-urakan-rahavaraus<!
UPDATE rahavaraus_urakka
   SET urakkakohtainen_nimi = :urakkakohtainen-nimi,
        muokkaaja = :kayttajaid,
        muokattu = CURRENT_TIMESTAMP
 WHERE urakka_id = :urakkaid
   AND rahavaraus_id = :rahavarausid;

-- name: poista-urakan-rahavaraus<!
DELETE
  FROM rahavaraus_urakka
 WHERE urakka_id = :urakkaid
   AND rahavaraus_id = :rahavarausid;

-- name: lisaa-rahavaraukselle-tehtava<!
INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja, luotu)
VALUES (:rahavaraus-id, :tehtava-id, :kayttaja, CURRENT_TIMESTAMP);

-- name: poista-rahavaraukselta-tehtava!
DELETE
  FROM rahavaraus_tehtava
 WHERE rahavaraus_id = :rahavaraus-id
   AND tehtava_id = :tehtava-id;

-- name: onko-rahavaraus-olemassa?
-- single?: true
SELECT exists(SELECT id FROM rahavaraus WHERE id = :rahavaraus-id :: BIGINT);

-- name: kuuluuko-tehtava-rahavaraukselle?
-- single?: true
SELECT EXISTS(SELECT id
                FROM rahavaraus_tehtava
               WHERE tehtava_id = :tehtava-id :: BIGINT
                 AND rahavaraus_id = :rahavaraus-id :: BIGINT);

-- name: onko-tehtava-olemassa?
-- single?: true
SELECT EXISTS(SELECT t.id
                FROM tehtava t
                         JOIN tehtavaryhma tr ON t.tehtavaryhma = tr.id
                         JOIN tehtavaryhmaotsikko tro ON tro.id = tr.tehtavaryhmaotsikko_id
               WHERE t.poistettu IS FALSE
                 AND t.tehtavaryhma IS NOT NULL
                 AND t.id = :tehtava-id :: BIGINT);

-- name: onko-rahavaraus-kaytossa?
-- single?: true
SELECT EXISTS(SELECT id
                FROM kustannusarvioitu_tyo kt
               WHERE kt.rahavaraus_id = :id :: BIGINT
               UNION ALL
              SELECT id
                FROM kulu_kohdistus kk
               WHERE kk.rahavaraus_id = :id :: BIGINT);

-- name: poista-rahavaraus-urakoilta!
DELETE
  FROM rahavaraus_urakka
 WHERE rahavaraus_id = :id :: BIGINT;

-- name: poista-rahavarauksen-tehtavat!
DELETE
  FROM rahavaraus_tehtava
 WHERE rahavaraus_id = :id :: BIGINT;

-- name: poista-rahavaraus!
DELETE
  FROM rahavaraus
 WHERE id = :id :: BIGINT;

-- name: listaa-rahavaraukset-analytiikalle
SELECT r.id                     AS id,
       r.nimi                   AS nimi,
       ARRAY_AGG(rt.tehtava_id) AS tehtavat,
       r.luotu,
       r.muokattu
  FROM rahavaraus r
           JOIN rahavaraus_tehtava rt ON r.id = rt.rahavaraus_id
 GROUP BY r.id, r.nimi, r.jarjestys
 ORDER BY r.jarjestys ASC;

-- name: hae-rahavarauksen-tehtavaryhmat
SELECT DISTINCT ON (tr.id) tr.id,
       tr.nimi AS tehtavaryhma,
       tp.id   AS toimenpide,
       tpi.id  AS "toimenpideinstanssi"
  FROM rahavaraus_tehtava rt
           JOIN tehtava t ON t.id = rt.tehtava_id
           JOIN tehtavaryhma tr ON tr.id = t.tehtavaryhma
           JOIN toimenpide tp ON t.emo = tp.id
           JOIN toimenpideinstanssi tpi ON tpi.toimenpide = tp.id AND tpi.urakka = :urakkaid
 WHERE rt.rahavaraus_id = :rahavarausid :: BIGINT;

-- name: hae-urakan-suunnitellut-rahavarausten-kustannukset
-- Haetaan rahavaraukset ja tarkistetaan onko kustannusarvioitu_tyo taulussa niille suunniteltu kustannuksia.
-- Haussa ei ole vuosikohtaisuutta parametrisoitu, mutta tulokset summaroidaan vuosikohtaisesti.
  WITH urakan_alkuvuodet AS (SELECT GENERATE_SERIES(:alkuvuosi::INT, :loppuvuosi::INT - 1) AS year)
SELECT rv.id,
       COALESCE(NULLIF(rvu.urakkakohtainen_nimi, ''), rv.nimi) AS nimi,
       SUM(kt.summa)                                           AS summa,
       SUM(kt.summa_indeksikorjattu)                           AS "summa-indeksikorjattu",
       kt.indeksikorjaus_vahvistettu                           AS "indeksikorjaus-vahvistettu",
       CASE
           WHEN kt.vuosi = y.year AND kt.kuukausi >= 10 THEN kt.vuosi
           WHEN kt.vuosi - 1 = y.year AND kt.kuukausi <= 9 THEN kt.vuosi - 1
           ELSE y.year
           END                                                 AS hoitokauden_alkuvuosi,
       rv.jarjestys                                            AS jarjestys
  FROM urakan_alkuvuodet y
           CROSS JOIN rahavaraus rv
           JOIN rahavaraus_urakka rvu ON rvu.rahavaraus_id = rv.id AND rvu.urakka_id = :urakka_id
           JOIN sopimus s ON s.urakka = rvu.urakka_id AND s.paasopimus IS NULL
           LEFT JOIN kustannusarvioitu_tyo kt ON kt.rahavaraus_id = rv.id AND kt.sopimus = s.id
      AND ((kt.vuosi = y.year AND kt.kuukausi >= 10) OR
           (kt.vuosi = y.year + 1 AND kt.kuukausi <= 9))
 GROUP BY rv.id, rvu.urakkakohtainen_nimi, rv.nimi, rv.jarjestys, kt.indeksikorjaus_vahvistettu, hoitokauden_alkuvuosi
 ORDER BY hoitokauden_alkuvuosi, rv.jarjestys, nimi;

-- name: hae-rahavarauksen-toimenpideinstanssi
-- Kustannusarvoitu_tyo vaatii jonkun toimenpideinstanssin. Se ei ole vielä tiedossa. Niin haetaan urakkakohtaisesti vain ensimmäinen
SELECT id
  FROM toimenpideinstanssi
 WHERE urakka = :urakka_id
 ORDER BY id ASC
 LIMIT 1;
