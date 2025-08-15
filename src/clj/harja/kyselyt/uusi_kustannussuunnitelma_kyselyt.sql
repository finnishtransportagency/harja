-- name: hae-urakan-toimenpiteet
SELECT t.id, t.nimi, t.koodi, tpi.id as "toimenpideinstanssi-id"
FROM toimenpideinstanssi tpi
         JOIN toimenpide t ON tpi.toimenpide = t.id
WHERE tpi.urakka = :urakkaid
  -- Ei haeta kaikkia toimenpiteitä
  AND (t.koodi = '23104' -- talvihoito
    OR t.koodi = '23116' -- liikenneympariston-hoito
    OR t.koodi = '23124' -- sorateiden-hoito
    OR t.koodi = '20107' -- paallystepaikkaukset
    OR t.koodi = '20191' -- mhu-yllapito
    OR t.koodi = '14301' -- mhu-korvausinvestointi
    );

-- name: hae-kiintea-kustannus-kuukausittain
SELECT id, vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi,
       tehtavaryhma, tehtava, sopimus
FROM kiinteahintainen_tyo
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
      OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND toimenpideinstanssi = :toimenpideinstanssi-id;

-- name: hae-viimeisin-muokkaaja-kiinteahintaiselle-kustannukselle
SELECT greatest(kt.muokattu, kt.luotu) as viimeisin_muokkaus, concat(k.etunimi, ' ', k.sukunimi) as viimeisin_muokkaaja
FROM kiinteahintainen_tyo kt
JOIN kayttaja k ON k.id = greatest(kt.muokkaaja, kt.luoja),
    toimenpideinstanssi tpi
JOIN toimenpide t ON tpi.toimenpide = t.id
WHERE kt.sopimus = :sopimus-id
  AND tpi.urakka = :urakkaid
  AND ((kt.vuosi = :vuosi AND kt.kuukausi IN (10, 11, 12))
    OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND (t.koodi = '23104' -- talvihoito
    OR t.koodi = '23116' -- liikenneympariston-hoito
    OR t.koodi = '23124' -- sorateiden-hoito
    OR t.koodi = '20107' -- paallystepaikkaukset
    OR t.koodi = '20191' -- mhu-yllapito
    OR t.koodi = '14301' -- mhu-korvausinvestointi
    );

-- name: poista-kiinteat-kustannukset-kuukausittain!
UPDATE kiinteahintainen_tyo
 SET summa = null,
     summa_indeksikorjattu = null,
     muokattu = NOW(),
     muokkaaja = :muokkaaja
 WHERE sopimus = :sopimus-id
   AND vuosi = :vuosi
   AND toimenpideinstanssi = :toimenpideinstanssi-id
   AND kuukausi in (:kuukaudet);

-- name: hae-kiintea-kustannus-toimenpiteelle-kuukaudelta
SELECT id, vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi,
       tehtavaryhma, tehtava, sopimus
  FROM kiinteahintainen_tyo
 WHERE vuosi = :vuosi
   AND kuukausi = :kuukausi
   AND toimenpideinstanssi = :toimenpideinstanssi-id
   AND sopimus = :sopimus-id;

-- name: tallenna-kiinteat-kustannukset-kuukaudelta<!
INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi,
                                   tehtavaryhma, tehtava, sopimus, luotu, luoja)
VALUES (:vuosi, :kuukausi, :summa, :summa_indeksikorjattu, :toimenpideinstanssi-id,
        :tehtavaryhma, :tehtava, :sopimus-id, NOW(), :luoja);

-- name: paivita-kiinteat-kustannukset-kuukausittain<!
UPDATE kiinteahintainen_tyo
SET vuosi = :vuosi,
    kuukausi = :kuukausi,
    summa = :summa,
    summa_indeksikorjattu = :summa_indeksikorjattu,
    toimenpideinstanssi = :toimenpideinstanssi-id,
    tehtavaryhma = :tehtavaryhma,
    tehtava = :tehtava,
    muokattu = NOW(),
    muokkaaja = :muokkaaja
WHERE id = :id;

-- name: hae-erillishankinta-kuukausittain
SELECT id,
       kuukausi,
       vuosi,
       summa,
       summa_indeksikorjattu,
       toimenpideinstanssi,
       tehtavaryhma,
       tehtava,
       sopimus
FROM kustannusarvioitu_tyo
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
    OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND toimenpideinstanssi = :toimenpideinstanssi-id
  AND tehtavaryhma = :tehtavaryhma-id;

-- name: hae-kuukauden-erillishankinta
SELECT id,
       kuukausi,
       vuosi,
       summa,
       summa_indeksikorjattu,
       toimenpideinstanssi,
       tehtavaryhma,
       tehtava,
       sopimus
FROM kustannusarvioitu_tyo
WHERE id = :id;

-- name: hae-viimeisin-muokkaaja-erillishankinnoille
SELECT greatest(kt.muokattu, kt.luotu) as viimeisin_muokkaus, concat(k.etunimi, ' ', k.sukunimi) as viimeisin_muokkaaja
FROM kustannusarvioitu_tyo kt
         JOIN kayttaja k ON k.id = greatest(kt.muokkaaja, kt.luoja)
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
    OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND toimenpideinstanssi = :toimenpideinstanssi-id
  AND tehtavaryhma = :tehtavaryhma-id;

-- name: paivita-kuukauden-erillishankinta<!
UPDATE kustannusarvioitu_tyo
SET summa                 = :summa,
    summa_indeksikorjattu = :summa_indeksikorjattu,
    muokkaaja             = :muokkaaja,
    muokattu              = NOW()
WHERE id = :id;

-- name: tallenna-kuukauden-erillishankinta<!
INSERT INTO kustannusarvioitu_tyo (kuukausi, vuosi, summa, summa_indeksikorjattu,
                                    toimenpideinstanssi, tehtavaryhma, sopimus, tyyppi, osio, luoja, luotu)
VALUES (:kuukausi, :vuosi, :summa, :summa_indeksikorjattu,
        :toimenpideinstanssi-id, :tehtavaryhma-id, :sopimus-id,
        'laskutettava-tyo', 'erillishankinnat', :luoja, NOW());

-- name: hae-johto-ja-hallintokorvaukset-kuukausittain
-- Käytetään -25 ja myöhemmin alkaville urakoille, kun yksittäisellä toimenkuvalla ei ole merkitystä
SELECT MIN(id) as id,
       kuukausi,
       vuosi,
       SUM((tunnit * tuntipalkka))                 as summa,
       SUM((tunnit * tuntipalkka_indeksikorjattu)) as summa_indeksikorjattu
FROM johto_ja_hallintokorvaus
WHERE "urakka-id" = :urakka-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
    OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
GROUP BY vuosi, kuukausi
ORDER BY vuosi, kuukausi;

-- name: hae-kuukauden-johto-ja-hallintokorvaus
-- Käytetään -25 ja myöhemmin alkaville urakoille, kun yksittäisellä toimenkuvalla ei ole merkitystä
SELECT id,
       kuukausi,
       vuosi,
       tunnit,
       tuntipalkka,
       tuntipalkka_indeksikorjattu,
       "urakka-id",
       luotu,
       luoja,
       muokattu,
       muokkaaja
FROM johto_ja_hallintokorvaus
WHERE id = :id;

-- name: hae-toimenkuvan-kuukauden-johto-ja-hallintokorvaus
-- Käytetään -24 ja aiemmin alkaville urakoille, kun yksittäisellä toimenkuvalla on kaikki merkitys
SELECT id,
       kuukausi,
       vuosi,
       tunnit,
       tuntipalkka,
       tuntipalkka_indeksikorjattu,
       "urakka-id",
       "toimenkuva-id",
       luotu,
       luoja,
       muokattu,
       muokkaaja
FROM johto_ja_hallintokorvaus
WHERE "toimenkuva-id" = :toimenkuva-id
  AND kuukausi = :kuukausi
  AND vuosi = :vuosi
  AND "urakka-id" = :urakka-id;


-- name: hae-viimeisin-muokkaaja-jjh
SELECT greatest(jjh.muokattu, jjh.luotu) as viimeisin_muokkaus, concat(k.etunimi, ' ', k.sukunimi) as viimeisin_muokkaaja
FROM johto_ja_hallintokorvaus jjh
         JOIN kayttaja k ON k.id = greatest(jjh.muokkaaja, jjh.luoja)
WHERE "urakka-id" = :urakka-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
    OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9));

-- name: paivita-kuukauden-johto-ja-hallintokorvaus<!
-- Käytetään -25 ja myöhemmin alkaville urakoille, kun yksittäisellä toimenkuvalla ei ole merkitystä
UPDATE johto_ja_hallintokorvaus
SET tuntipalkka                 = :tuntipalkka,
    tunnit                      = :tunnit,
    tuntipalkka_indeksikorjattu = :tuntipalkka_indeksikorjattu,
    muokkaaja                   = :muokkaaja,
    muokattu                    = NOW()
WHERE id = :id;

-- name: lisaa-kuukauden-johto-ja-hallintokorvaus<!
INSERT INTO johto_ja_hallintokorvaus
    (kuukausi, vuosi, "toimenkuva-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, "urakka-id", luoja, luotu)
VALUES (:kuukausi, :vuosi, :toimenkuva-id, :tunnit, :tuntipalkka, :tuntipalkka_indeksikorjattu, :urakka-id, :luoja, NOW());

-- name: hae-hoidonjohtopalkkiot-kuukausittain
SELECT id,
       kuukausi,
       vuosi,
       summa,
       summa_indeksikorjattu,
       toimenpideinstanssi,
       tehtavaryhma,
       tehtava,
       sopimus
FROM kustannusarvioitu_tyo
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
    OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND toimenpideinstanssi = :toimenpideinstanssi-id
  AND tehtava = :tehtava-id;

-- name: hae-kuukauden-hoidonjohtopalkkio
SELECT id,
       kuukausi,
       vuosi,
       summa,
       summa_indeksikorjattu,
       toimenpideinstanssi,
       tehtava,
       sopimus
FROM kustannusarvioitu_tyo
WHERE id = :id;

-- name: hae-viimeisin-muokkaaja-hoidonjohtopalkkiolle
SELECT greatest(kt.muokattu, kt.luotu) as viimeisin_muokkaus, concat(k.etunimi, ' ', k.sukunimi) as viimeisin_muokkaaja
FROM kustannusarvioitu_tyo kt
            JOIN kayttaja k ON k.id = greatest(kt.muokkaaja, kt.luoja)
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
    OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND toimenpideinstanssi = :toimenpideinstanssi-id
  AND tehtava = :tehtava-id;

-- name: paivita-kuukauden-hoidonjohtopalkkio<!
UPDATE kustannusarvioitu_tyo
SET summa                 = :summa,
    summa_indeksikorjattu = :summa_indeksikorjattu,
    muokkaaja             = :muokkaaja,
    muokattu              = NOW()
WHERE id = :id;

-- name: tallenna-kuukauden-hoidonjohtopalkkio<!
INSERT INTO kustannusarvioitu_tyo (kuukausi, vuosi, summa, summa_indeksikorjattu,
                                   toimenpideinstanssi, tehtava, sopimus, tyyppi, osio, luoja, luotu)
VALUES (:kuukausi, :vuosi, :summa, :summa_indeksikorjattu,
        :toimenpideinstanssi-id, :tehtava-id, :sopimus-id,
        'laskutettava-tyo', 'hoidonjohtopalkkio', :luoja, NOW());

-- name: hae-rahavaraus-vuodelta
SELECT r.nimi,
       SUM(summa) as "suunniteltu-summa",
       SUM(summa_indeksikorjattu) as "suunniteltu-summa-indeksikorjattu"
FROM kustannusarvioitu_tyo kt
     join rahavaraus r on kt.rahavaraus_id = r.id
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
   OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
GROUP BY r.id;

--name: vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille!
UPDATE kiinteahintainen_tyo kt
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP ELSE NULL END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja ELSE NULL END,
    summa_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(kt.summa::NUMERIC, kt.vuosi::INTEGER, kt.kuukausi::INTEGER, :urakka-id::INTEGER) ELSE NULL END
FROM toimenpideinstanssi tpi
WHERE kt.toimenpideinstanssi = tpi.id
  AND tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.versio = 0;

--name: vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille!
UPDATE kustannusarvioitu_tyo kt
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP ELSE NULL END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja ELSE NULL END,
    summa_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(kt.summa::NUMERIC, kt.vuosi::INTEGER, kt.kuukausi::INTEGER, :urakka-id::INTEGER) ELSE NULL END
FROM toimenpideinstanssi tpi
WHERE kt.toimenpideinstanssi = tpi.id
  AND tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.versio = 0;

--name: vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille!
UPDATE johto_ja_hallintokorvaus jh
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END,
    tuntipalkka_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(jh.tuntipalkka::NUMERIC, jh.vuosi::INTEGER, jh.kuukausi::INTEGER, :urakka-id::INTEGER) ELSE NULL END
WHERE jh."urakka-id" = :urakka-id
  AND (CONCAT(jh.vuosi, '-', jh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND jh.versio = 0;

--name: vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille!
UPDATE urakka_tavoite ut
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END,
    tavoitehinta_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(ut.tavoitehinta::NUMERIC, :vuosi::INTEGER, :urakka-id::INTEGER, :urakka-id::INTEGER) ELSE NULL END,
    kattohinta_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(ut.kattohinta::NUMERIC, :vuosi::INTEGER, :urakka-id::INTEGER, :urakka-id::INTEGER) ELSE NULL END
WHERE ut.urakka = :urakka-id
  -- hoitokausi ei ole hoitovuosi e.g. 2020, vaan hoitovuoden järjestysnumero e.g. 1
  AND ut.hoitokausi = :hoitovuosi-nro
  AND ut.versio = 0;

-- name: paivita-tavoite-ja-kattohinta<!
UPDATE urakka_tavoite
SET tavoitehinta = :tavoitehinta,
    kattohinta = :kattohinta,
    muokattu = NOW(),
    muokkaaja = :muokkaaja
WHERE urakka = :urakka-id
  AND hoitokausi = :hoitokausinumero;

-- name: lisaa-tavoite-ja-kattohinta<!
INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, kattohinta, luotu, luoja)
VALUES (:urakka-id, :hoitokausinumero, :tavoitehinta, :kattohinta, NOW(), :luoja);

-- name: indeksikorjaukset-vahvistettu?
SELECT COUNT(*) > 0 AS "kiinteat-vahvistettu?"
FROM kiinteahintainen_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
WHERE tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.indeksikorjaus_vahvistettu IS NOT NULL
  AND kt.versio = 0

UNION ALL
SELECT COUNT(*) > 0 AS "arvioidut-vahvistettu?"
FROM kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
WHERE tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.indeksikorjaus_vahvistettu IS NOT NULL
  AND kt.versio = 0;

-- name: hae-urakan-hoitovuoden-tavoitetiedot
SELECT id, tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta, kattohinta_indeksikorjattu, tarjous_tavoitehinta
    FROM urakka_tavoite
WHERE urakka = :urakka-id
  AND hoitokausi = :hoitokausinumero;

-- name: hae-urakan-toimenkuvat
-- Hae urakkakohtaiset toimenkuvat
WITH urakka_toimenkuvat AS (SELECT nimike
                            FROM unnest(
                                     CASE
                                         WHEN (:urakan-alkuvuosi >= 2019 AND :urakan-alkuvuosi <= 2021)
                                             THEN ARRAY ['sopimusvastaava', 'vastuunalainen työnjohtaja', 'päätoiminen apulainen', 'apulainen/työnjohtaja','viherhoidosta vastaava henkilö','hankintavastaava','harjoittelija']
                                         WHEN (:urakan-alkuvuosi >= 2022 AND :urakan-alkuvuosi <= 2023)
                                             THEN ARRAY ['valmistelukausi ennen urakka-ajan alkua','vastuunalainen työnjohtaja', 'päätoiminen apulainen','apulainen/työnjohtaja', 'viherhoidosta vastaava henkilö', 'hankintavastaava', 'harjoittelija']
                                         WHEN (:urakan-alkuvuosi = 2024)
                                             THEN ARRAY ['valmistelukausi ennen urakka-ajan alkua','vastuunalainen työnjohtaja','2. työnjohtaja', '3. työnjohtaja', 'viherhoidosta vastaava henkilö', 'harjoittelija']
                                         ELSE ARRAY ['valmistelukausi ennen urakka-ajan alkua','vastuunalainen työnjohtaja','2. työnjohtaja', '3. työnjohtaja', 'viherhoidosta vastaava henkilö', 'harjoittelija']
                                         END
                                 ) AS nimike)
SELECT id, toimenkuva
FROM johto_ja_hallintokorvaus_toimenkuva jht
WHERE jht."urakka-id" = :urakka-id
AND jht.toimenkuva is not null
UNION
SELECT (select MIN(id) from johto_ja_hallintokorvaus_toimenkuva where toimenkuva = ut.nimike) AS id,
       nimike                                                                                 AS toimenkuva
from urakka_toimenkuvat ut
ORDER BY ID;

-- name: hae-toimenkuvan-johto-ja-hallintokorvaukset-kuukausittain
-- Uudessa kustannusten suunnittelussa suunnitellaan edelleen tunnit ja tuntipalkat 19-22 alkaville urakoille
SELECT jh.tunnit,
       jh.tuntipalkka,
       jh.tuntipalkka_indeksikorjattu AS "tuntipalkka-indeksikorjattu",
       jh.indeksikorjaus_vahvistettu AS "indeksikorjaus-vahvistettu",
       jh.vuosi,
       jh.kuukausi,
       jht.toimenkuva,
       jh."toimenkuva-id"
FROM johto_ja_hallintokorvaus jh
    JOIN johto_ja_hallintokorvaus_toimenkuva jht ON jh."toimenkuva-id" = jht.id
WHERE jh."urakka-id" = :urakka-id
  AND ((jh.vuosi = :vuosi AND jh.kuukausi IN (10, 11, 12))
    OR (jh.vuosi = :vuosi + 1 AND jh.kuukausi >= 1 AND jh.kuukausi <= 9))
AND jh."toimenkuva-id" = :toimenkuva-id;
