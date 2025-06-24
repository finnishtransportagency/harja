-- name: hae-urakan-toimenpiteet
SELECT t.id, t.nimi, t.koodi, tpi.id as "toimenpideinstanssi-id"
FROM toimenpideinstanssi tpi
         JOIN toimenpide t ON tpi.toimenpide = t.id
WHERE tpi.urakka = :urakkaid;

-- name: hae-kiintea-kustannus-kuukausittain
SELECT id, vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi,
       tehtavaryhma, tehtava, sopimus
FROM kiinteahintainen_tyo
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
      OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  and toimenpideinstanssi = :toimenpideinstanssi-id;

-- name: poista-kiinteat-kustannukset-kuukausittain!
UPDATE kiinteahintainen_tyo
 SET summa = null,
     summa_indeksikorjattu = null,
     muokattu = NOW(),
     muokkaaja = :muokkaaja
 WHERE sopimus = :sopimus-id
   AND vuosi = :vuosi
   and toimenpideinstanssi = :toimenpideinstanssi-id
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
  and toimenpideinstanssi = :toimenpideinstanssi-id
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
  and toimenpideinstanssi = :toimenpideinstanssi-id
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
       SUM(summa) as summa,
       SUM(summa_indeksikorjattu) as "summa-indeksikorjattu"
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
