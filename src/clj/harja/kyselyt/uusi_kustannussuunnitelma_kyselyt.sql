-- name: hae-urakan-toimenpiteet
SELECT t.id, t.nimi, t.koodi, tpi.id AS "toimenpideinstanssi-id", t.jarjestys
  FROM toimenpideinstanssi tpi
         JOIN toimenpide t ON tpi.toimenpide = t.id
 WHERE tpi.urakka = :urakkaid
   AND true = onko_mhu_hankintatoimenpide(t.koodi)
 ORDER BY t.jarjestys;

-- name: hae-kiintea-kustannus-kuukausittain
SELECT id, vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi,
       tehtavaryhma, tehtava, sopimus
FROM kiinteahintainen_tyo
WHERE sopimus = :sopimus-id
  AND ((vuosi = :vuosi AND kuukausi IN (10, 11, 12))
      OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND toimenpideinstanssi = :toimenpideinstanssi-id;

-- name: hae-pysyvat-hankintakus-muutokset 
SELECT
    mmk.muutos,
    :vuosi::INTEGER              AS vuosi,
    NULL::INTEGER                AS kuukausi,
    mmk.summa,
    mmk.toimenpideinstanssi
FROM mhu_muutos_kustannusvaikutus mmk
         JOIN mhu_muutos m ON m.id = mmk.muutos
         JOIN toimenpideinstanssi tpi ON tpi.id = mmk.toimenpideinstanssi
         JOIN toimenpide tp ON tp.id = tpi.toimenpide
WHERE m.urakka = :urakka
  AND mmk.toimenpideinstanssi = :toimenpideinstanssi-id
  AND m.poistettu IS NOT TRUE
  AND m.tyyppi = 'pysyva'
  -- TALVIHOITO, LIIKENNEYMPÄRISTÖN HOITO 
  -- SORATEIDENHOITO, PÄÄLLYSTEIDEN PAIKKAUS
  -- YLLÄPITO, KORVAUSINVESTOINTI
  AND tp.koodi IN ('23104', '23116', '23124', '20107', '20191', '14301')
  AND (extract(YEAR FROM m.voimassa_alkaen) < :vuosi
  AND mmk.hoitokauden_alkuvuosi = :vuosi::INTEGER);

-- name: hae-viimeisin-muokkaaja-kiinteahintaiselle-kustannukselle
SELECT GREATEST(kt.muokattu, kt.luotu) AS viimeisin_muokkaus,
       CASE WHEN k.piilota_nimi IS TRUE THEN 'Järjestelmän ylläpito'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
       END AS viimeisin_muokkaaja
  FROM kiinteahintainen_tyo kt
       LEFT JOIN kayttaja k ON COALESCE(kt.muokkaaja, kt.luoja) = k.id
       JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
       JOIN toimenpide t ON tpi.toimenpide = t.id
WHERE kt.sopimus = :sopimus-id
  AND tpi.urakka = :urakkaid
  AND ((kt.vuosi = :vuosi AND kt.kuukausi IN (10, 11, 12))
      OR (kt.vuosi = :vuosi + 1 AND kt.kuukausi >= 1 AND kt.kuukausi <= 9))
  AND true = onko_mhu_hankintatoimenpide(t.koodi)
ORDER BY viimeisin_muokkaus DESC
LIMIT 1;

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


-- name: hae-tallennetun-kuukauden-erillishankinta
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
  AND vuosi = :vuosi
  AND kuukausi = :kuukausi
  AND toimenpideinstanssi = :toimenpideinstanssi-id
  AND tehtavaryhma = :tehtavaryhma-id;


-- name: hae-viimeisin-muokkaaja-erillishankinnoille
SELECT GREATEST(kt.muokattu, kt.luotu) AS viimeisin_muokkaus,
       CASE WHEN k.piilota_nimi IS TRUE THEN 'Järjestelmän ylläpito'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
           END AS viimeisin_muokkaaja
FROM kustannusarvioitu_tyo kt
         LEFT JOIN kayttaja k ON COALESCE(kt.muokkaaja, kt.luoja) = k.id
WHERE kt.sopimus = :sopimus-id
  AND ((kt.vuosi = :vuosi AND kt.kuukausi IN (10, 11, 12))
      OR (kt.vuosi = :vuosi + 1 AND kt.kuukausi >= 1 AND kt.kuukausi <= 9))
  AND kt.toimenpideinstanssi = :toimenpideinstanssi-id
  AND kt.tehtavaryhma = :tehtavaryhma-id
ORDER BY viimeisin_muokkaus DESC
LIMIT 1;

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
SELECT MIN(id) AS id,
       kuukausi,
       vuosi,
       SUM((tunnit * tuntipalkka))                 AS summa,
       SUM((tunnit * tuntipalkka_indeksikorjattu)) AS summa_indeksikorjattu
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


-- name: hae-tallennettu-kuukauden-johto-ja-hallintokorvaus
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
WHERE "urakka-id" = :urakka-id
  AND kuukausi = :kuukausi
  AND vuosi = :vuosi
  AND "toimenkuva-id" = :toimenkuva-id;


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
SELECT GREATEST(jjh.muokattu, jjh.luotu) AS viimeisin_muokkaus,
       CASE WHEN k.piilota_nimi IS TRUE THEN 'Järjestelmän ylläpito'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
           END AS viimeisin_muokkaaja
FROM johto_ja_hallintokorvaus jjh
     LEFT JOIN kayttaja k ON COALESCE(jjh.muokkaaja, jjh.luoja) = k.id
WHERE jjh."urakka-id" = :urakka-id
  AND ((jjh.vuosi = :vuosi AND jjh.kuukausi IN (10, 11, 12))
      OR (jjh.vuosi = :vuosi + 1 AND jjh.kuukausi >= 1 AND jjh.kuukausi <= 9))
ORDER BY viimeisin_muokkaus DESC
LIMIT 1;

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

-- name: hae-olemassa-oleva-hoidonjohtopalkkio
SELECT id,
       kuukausi,
       vuosi,
       summa,
       summa_indeksikorjattu,
       toimenpideinstanssi,
       tehtava,
       sopimus
 FROM kustannusarvioitu_tyo
WHERE sopimus = :sopimus-id 
  AND vuosi = :vuosi 
  AND kuukausi = :kuukausi
  AND toimenpideinstanssi = :toimenpideinstanssi-id
  AND tehtava = :tehtava-id;

-- name: hae-viimeisin-muokkaaja-hoidonjohtopalkkiolle
SELECT GREATEST(kt.muokattu, kt.luotu) AS viimeisin_muokkaus,
       CASE WHEN k.piilota_nimi IS TRUE THEN 'Järjestelmän ylläpito'
            ELSE CONCAT(k.etunimi, ' ', k.sukunimi)
           END AS viimeisin_muokkaaja
FROM kustannusarvioitu_tyo kt
         JOIN kayttaja k ON COALESCE(kt.muokkaaja, kt.luoja) = k.id
WHERE kt.sopimus = :sopimus-id
  AND ((kt.vuosi = :vuosi AND kt.kuukausi IN (10, 11, 12))
      OR (vuosi = :vuosi + 1 AND kuukausi >= 1 AND kuukausi <= 9))
  AND kt.toimenpideinstanssi = :toimenpideinstanssi-id
  AND kt.tehtava = :tehtava-id
ORDER BY viimeisin_muokkaus DESC
LIMIT 10;

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
SELECT ru.rahavaraus_id                          as rahavaraus_id,
       COALESCE(ru.urakkakohtainen_nimi, r.nimi) AS nimi,
       SUM(kt.summa)                             AS "suunniteltu-summa",
       SUM(kt.summa_indeksikorjattu)             AS "suunniteltu-summa-indeksikorjattu"
  FROM rahavaraus_urakka ru
           LEFT JOIN kustannusarvioitu_tyo kt ON ru.rahavaraus_id = kt.rahavaraus_id
                 AND kt.sopimus = :sopimusid
                 AND ((kt.vuosi = :vuosi AND kt.kuukausi IN (10, 11, 12))
                       OR (kt.vuosi = :vuosi + 1 AND kt.kuukausi >= 1 AND kt.kuukausi <= 9))
           LEFT JOIN rahavaraus r on r.id = ru.rahavaraus_id
 WHERE ru.urakka_id = :urakkaid
GROUP BY ru.rahavaraus_id, COALESCE(ru.urakkakohtainen_nimi, r.nimi);

--name: vahvista-tai-kumoa-indeksikorjaukset-kiinteahintaisille-toille!
UPDATE kiinteahintainen_tyo kt
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP ELSE NULL END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja ELSE NULL END,
    summa_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(kt.summa::NUMERIC, kt.vuosi::INTEGER, kt.kuukausi::INTEGER, :urakka-id::INTEGER) ELSE kt.summa_indeksikorjattu END
FROM toimenpideinstanssi tpi
     JOIN toimenpide t ON tpi.toimenpide = t.id
WHERE kt.toimenpideinstanssi = tpi.id
  AND tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND true = onko_mhu_hankintatoimenpide(t.koodi);

--name: vahvista-tai-kumoa-indeksikorjaukset-kustannusarvioiduille-toille!
-- Vahvistaa käytännössä rahavaraukset, hoidonjohtopalkkiot ja erillishankinnat
UPDATE kustannusarvioitu_tyo kt
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP ELSE NULL END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja ELSE NULL END,
    summa_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(kt.summa::NUMERIC, kt.vuosi::INTEGER, kt.kuukausi::INTEGER, :urakka-id::INTEGER) ELSE kt.summa_indeksikorjattu END
FROM toimenpideinstanssi tpi
WHERE kt.toimenpideinstanssi = tpi.id
  AND tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE);

--name: vahvista-tai-kumoa-indeksikorjaukset-jh-korvauksille!
UPDATE johto_ja_hallintokorvaus jh
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END,
    tuntipalkka_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(jh.tuntipalkka::NUMERIC, jh.vuosi::INTEGER, jh.kuukausi::INTEGER, :urakka-id::INTEGER) ELSE jh.tuntipalkka_indeksikorjattu END
WHERE jh."urakka-id" = :urakka-id
  AND (CONCAT(jh.vuosi, '-', jh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE);

--name: vahvista-tai-kumoa-indeksikorjaukset-urakan-tavoitteille!
UPDATE urakka_tavoite ut
SET indeksikorjaus_vahvistettu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistus-pvm::TIMESTAMP END,
    vahvistaja                 = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN :vahvistaja END,
    tavoitehinta_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(ut.tavoitehinta::NUMERIC, :vuosi::INTEGER, :urakka-id::INTEGER, :urakka-id::INTEGER) ELSE ut.tavoitehinta_indeksikorjattu END,
    kattohinta_indeksikorjattu = CASE WHEN :vahvista?::BOOLEAN = TRUE THEN indeksikorjaa(ut.kattohinta::NUMERIC, :vuosi::INTEGER, :urakka-id::INTEGER, :urakka-id::INTEGER) ELSE ut.kattohinta_indeksikorjattu END,
    laskutusraja = CASE
                       WHEN EXISTS (
                             SELECT 1
                               FROM urakka_parametrit up
                              WHERE up.urakkaid = ut.urakka
                                AND up.laskutusraja_kaytossa = TRUE)
                       THEN ut.tavoitehinta_indeksikorjattu + :laskutusrajan-tarkistus-summa::NUMERIC
                       ELSE ut.laskutusraja
                   END,
    laskutusraja_alkuperainen = CASE
                                    WHEN :vahvista?::BOOLEAN = TRUE
                                     AND EXISTS (
                                          SELECT 1
                                            FROM urakka_parametrit up
                                           WHERE up.urakkaid = ut.urakka
                                             AND up.laskutusraja_kaytossa = TRUE)
                                    THEN ut.tavoitehinta_indeksikorjattu
                                    ELSE ut.laskutusraja_alkuperainen
                                END
WHERE ut.urakka = :urakka-id
  -- hoitokausi ei ole hoitovuosi e.g. 2020, vaan hoitovuoden järjestysnumero e.g. 1
  AND ut.hoitokausi = :hoitovuosi-nro;

-- name: aseta-kasin-syotetty-kattohinta<!
UPDATE urakka_tavoite ut
   SET kattohinta = :kattohinta,
       kattohinta_indeksikorjattu = :kattohinta-indeksikorjattu,
       luotu = NOW(),
       luoja = :luoja
 WHERE ut.urakka = :urakka-id
       -- hoitokausi ei ole hoitovuosi e.g. 2020, vaan hoitovuoden järjestysnumero e.g. 1
   AND ut.hoitokausi = :hoitovuosinro;

-- name: paivita-kasin-syotetty-kattohinta!
UPDATE urakka_tavoite ut
   SET kattohinta = :kattohinta,
       kattohinta_indeksikorjattu = :kattohinta-indeksikorjattu,
       muokattu = NOW(),
       muokkaaja = :muokkaaja
 WHERE ut.urakka = :urakka-id
       -- hoitokausi ei ole hoitovuosi e.g. 2020, vaan hoitovuoden järjestysnumero e.g. 1
   AND ut.hoitokausi = :hoitovuosinro;

-- name: hae-kustannussuunnitelman-osiot
SELECT *
  FROM suunnittelu_kustannussuunnitelman_tila skt
 WHERE skt.urakka = :urakkaid
   AND skt.hoitovuosi = :hoitovuosinro;

-- name: lisaa-kustannussuunnitelma-osio
INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, luoja, vahvistaja, vahvistettu, vahvistus_pvm)
VALUES (:urakkaid, :osio::SUUNNITTELU_OSIO, :hoitovuosi, :luoja, :vahvistaja, :vahvistettu, :vahvistus_pvm)
ON CONFLICT DO NOTHING
RETURNING id;

-- name: paivita-kustannussuunnitelma-osio
UPDATE suunnittelu_kustannussuunnitelman_tila
SET vahvistettu   = :vahvistettu,
    muokattu      = CURRENT_TIMESTAMP,
    muokkaaja     = :muokkaaja,
    vahvistaja    = :vahvistaja,
    vahvistus_pvm = :vahvistus_pvm
WHERE id = :id
RETURNING id;

-- name: paivita-tavoite-ja-kattohinta<!
UPDATE urakka_tavoite
SET tavoitehinta = :tavoitehinta,
    tavoitehinta_indeksikorjattu = :tavoitehinta_indeksikorjattu,
    kattohinta = :kattohinta,
    kattohinta_indeksikorjattu = :kattohinta_indeksikorjattu,
    muokattu = NOW(),
    muokkaaja = :muokkaaja
WHERE urakka = :urakka-id
  AND hoitokausi = :hoitokausinumero;

-- name: lisaa-tavoite-ja-kattohinta<!
INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta, kattohinta_indeksikorjattu, luotu, luoja)
VALUES (:urakka-id, :hoitokausinumero, :tavoitehinta, :tavoitehinta_indeksikorjattu, :kattohinta, :kattohinta_indeksikorjattu, NOW(), :luoja);

-- name: hae-urakan-hoitovuoden-tarjous
SELECT * FROM tarjous WHERE urakka_id = :urakka_id AND hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi;

-- name: hae-vanhan-urakan-hoitovuoden-tarjous
SELECT * FROM urakka_tavoite WHERE urakka = :urakka_id  AND hoitokausi = :hoitokausi;

-- name: indeksikorjaukset-vahvistettu?
-- Tarkisetaan löytyykö kiinteähintainen_tyo, Kustannusarvioitu_tyo tai Johto_ja_hallintokorvaus tauluista rivejä,
-- joilla indeksikorjaus_vahvistettu ei ole null. Jos yhdellä rivillä annetulla aikavälillä on jotain muuta kuin null,
-- niin päätellään, että kaikilla on. Logiikka toimii niin.
SELECT COUNT(*) > 0 AS "vahvistettu?"
FROM kiinteahintainen_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
WHERE tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.indeksikorjaus_vahvistettu IS NOT NULL

UNION ALL
SELECT COUNT(*) > 0 AS "vahvistettu?"
FROM kustannusarvioitu_tyo kt
         JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id
WHERE tpi.urakka = :urakka-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.indeksikorjaus_vahvistettu IS NOT NULL

UNION ALL
SELECT COUNT(*) > 0 AS "vahvistettu?"
FROM johto_ja_hallintokorvaus jjh
WHERE jjh."urakka-id" = :urakka-id
  AND (CONCAT(jjh.vuosi, '-', jjh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND jjh.indeksikorjaus_vahvistettu IS NOT NULL;

-- name: hae-urakan-hoitovuoden-tavoitetiedot
SELECT id, tavoitehinta, tavoitehinta_indeksikorjattu, kattohinta, kattohinta_indeksikorjattu, tarjous_tavoitehinta
    FROM urakka_tavoite
WHERE urakka = :urakka-id
  AND hoitokausi = :hoitokausinumero;

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
AND jh."toimenkuva-id" = :toimenkuva-id
AND jh.kuukausi IN (:sallitut-kuukaudet);

-- name: hae-muut-kulut-toimenkuviin-kuukausittain
-- Muut kulut on vanhoille -24 ja ennen alkaneille urakoille. Muudemmat -25 ja myöhemmin alkaneet eivät enää käytä tätä.
SELECT kt.id,
       kt.kuukausi,
       kt.vuosi,
       1 as tunnit,
       kt.summa as "tuntipalkka",
       kt.summa_indeksikorjattu as "tuntipalkka-indeksikorjattu",
       'Muut kulut' as toimenkuva,
       'Muut kulut' as nimike
  FROM kustannusarvioitu_tyo kt
       JOIN tehtava t ON kt.tehtava = t.id AND t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- Muut kulut
 WHERE kt.sopimus = :sopimus-id
   AND ((kt.vuosi = :vuosi AND kt.kuukausi IN (10, 11, 12))
       OR (kt.vuosi = :vuosi + 1 AND kt.kuukausi >= 1 AND kt.kuukausi <= 9))
   AND kt.toimenpideinstanssi = :toimenpideinstanssi-id;

-- name: hae-muut-kulut-kuukaudelle
SELECT kt.id,
       kt.kuukausi,
       kt.vuosi,
       kt.summa,
       kt.summa_indeksikorjattu
  FROM kustannusarvioitu_tyo kt
       JOIN tehtava t ON kt.tehtava = t.id AND t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- Muut kulut
 WHERE kt.sopimus = :sopimus-id
   AND kt.vuosi = :vuosi
   AND kt.kuukausi = :kuukausi
   AND toimenpideinstanssi = :toimenpideinstanssi-id;

-- name: lisaa-kuukauden-muu-kulu<!
INSERT INTO kustannusarvioitu_tyo (kuukausi, vuosi, summa, summa_indeksikorjattu,
                                   toimenpideinstanssi, tehtava, sopimus, tyyppi, osio, luoja, luotu)
VALUES (:kuukausi, :vuosi, :summa, :summa_indeksikorjattu,
        :toimenpideinstanssi-id,  :tehtava-id, :sopimus-id,
        'laskutettava-tyo', 'johto-ja-hallintokorvaus', :luoja, NOW());

-- name: paivita-kuukauden-muu-kulu<!
UPDATE kustannusarvioitu_tyo
   SET summa                 = :summa,
       summa_indeksikorjattu = :summa_indeksikorjattu,
       muokkaaja             = :muokkaaja,
       muokattu              = NOW()
 WHERE id = :id;

-- name: hae-tehtava-tunnisteella
SELECT id, nimi, yksikko, suunnitteluyksikko, tehtavaryhma, luoja, luotu, muokkaaja, muokattu
  FROM tehtava
 WHERE yksiloiva_tunniste = :tunniste::UUID
   AND piilota IS NOT TRUE
   AND poistettu IS NOT TRUE;

-- name: tulevilla-hoitovuosilla-arvoja?
-------------------------------- Johto ja hallinto
SELECT 'jjh' AS tyyppi, 
       SUM(jjh.tuntipalkka) > 0 AS "arvoja?"
 FROM johto_ja_hallintokorvaus jjh
WHERE jjh."urakka-id" = :urakka-id
  AND (CONCAT(jjh.vuosi, '-', jjh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
GROUP BY tyyppi
UNION ALL
-------------------------------- Hoidonjohtopalkkiot
SELECT 'hoidonjohto' AS tyyppi,
       SUM(kt.summa) > 0 AS "arvoja?"
 FROM kustannusarvioitu_tyo kt
WHERE sopimus = :sopimus-id
  AND (CONCAT(vuosi, '-', kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND toimenpideinstanssi = :hoidon-johdon-tpi-id
  AND tehtava = :tehtava-id
GROUP BY tyyppi
UNION ALL
-------------------------------- Erillishankinnat
SELECT 'erillishankinnat' AS tyyppi, 
       SUM(kt.summa) > 0 AS "arvoja?"
 FROM kustannusarvioitu_tyo kt
WHERE kt.sopimus = :sopimus-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.toimenpideinstanssi = :hoidon-johdon-tpi-id
  AND kt.tehtavaryhma = :erillishankinnat-tehtavaryhma-id
GROUP BY tyyppi
UNION ALL
-------------------------------- Muut kulut
SELECT 'muut' AS tyyppi, 
       SUM(kt.summa) > 0 AS "arvoja?"
 FROM kustannusarvioitu_tyo kt
         JOIN tehtava t ON kt.tehtava = t.id AND t.yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' -- Muut kulut
WHERE kt.sopimus = :sopimus-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND kt.toimenpideinstanssi = :hoidon-johdon-tpi-id
GROUP BY tyyppi
UNION ALL
-------------------------------- Rahavaraukset
SELECT 'rahavaraukset' AS tyyppi, 
       SUM(kt.summa) > 0 AS "arvoja?"
 FROM kustannusarvioitu_tyo kt
WHERE kt.rahavaraus_id IS NOT NULL
  AND kt.sopimus = :sopimus-id
  AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
GROUP BY tyyppi
-------------------------------- Hankinnat
UNION ALL
SELECT 'hankinnat' AS tyyppi,
       SUM(kt.summa) > 0 AS "arvoja?"
 FROM kiinteahintainen_tyo kt
WHERE (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND toimenpideinstanssi IN (:hankinnan-toimenpideinstanssit)
  AND sopimus = :sopimus-id
GROUP BY tyyppi;

-- name: hae-laskutusrajan-tarkistukset
WITH hoitokausi AS (
    SELECT GREATEST((:hoitokauden_alkuvuosi + 1)
                        - EXTRACT(YEAR FROM u.alkupvm)::INTEGER, 1) AS hoitokausinro
      FROM urakka u
     WHERE u.id = :urakka
),
alkuperainen_laskutusraja AS (
    SELECT ut.laskutusraja_alkuperainen
      FROM urakka_tavoite ut
      JOIN hoitokausi hk ON hk.hoitokausinro = ut.hoitokausi
     WHERE ut.urakka = :urakka
     LIMIT 1
),
muutokset AS (
    SELECT m.id,
           m.versio,
           m.voimassa_alkaen,
           m.tyyppi,
           -- Tavoitehinnan muutos lasketaan summana kustannusvaikutuksista
           -- Laskutusrajaan tarvitaan pelkästään eurot
           COALESCE((SELECT SUM(kust.summa)
                       FROM ONLY mhu_muutos_kustannusvaikutus kust
                      WHERE kust.muutos = m.id
                        AND kust.hoitokauden_alkuvuosi = :hoitokauden_alkuvuosi
                    ), 0) AS tavoitehinnan_muutos
      -- Laskutusrajaan otetaan mukaan pelkästään pysyvät sekä muutostyöt
      FROM ONLY mhu_muutos m
     WHERE m.urakka = :urakka
       AND m.poistettu IS FALSE
       AND m.tyyppi IN ('pysyva', 'muutostyo')
       AND m.voimassa_alkaen BETWEEN TO_DATE(:hoitokauden_alkuvuosi || '-10-01', 'YYYY-MM-DD')
                                 AND TO_DATE((:hoitokauden_alkuvuosi + 1) || '-09-30', 'YYYY-MM-DD')
),
laskettavat AS (
    SELECT *
      FROM muutokset
     WHERE tyyppi = 'muutostyo'
        OR (tyyppi = 'pysyva' AND tavoitehinnan_muutos > 0)
),
kertymat AS (
    SELECT *,
           -- Kumulatiivinen summa, lasketaan mukaan kaikki aiemmat rivit ja nykyinen rivi
           SUM(tavoitehinnan_muutos) OVER (
               ORDER BY voimassa_alkaen, id, versio
               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
           ) AS yhteensa
      FROM laskettavat
),
prosentit AS (
    SELECT *,
           -- Prosenttiosuus lasketaan suhteessa indeksikorjattuun tavoitehintaan,
           -- pyöristettynä kahteen desimaaliin
           CASE
               WHEN :hoitovuoden_indeksikorjattu_tavoitehinta > 0
               THEN ROUND(100.0 * yhteensa / :hoitovuoden_indeksikorjattu_tavoitehinta, 2)
               ELSE NULL
           END AS prosenttiosuus
      FROM kertymat
),
tarkistukset AS (
    SELECT *,
           -- Laskutusrajan tarkistus jos prosenttiosuus on 3.00 tai enemmän, muuten 0
           CASE
               WHEN prosenttiosuus >= 3.00 THEN yhteensa
               ELSE 0
           END AS laskutusrajan_tarkistus
      FROM prosentit
)
SELECT t.tavoitehinnan_muutos                                          AS summa,
       t.yhteensa,
       t.prosenttiosuus,
       t.laskutusrajan_tarkistus                                       AS "laskutusrajan-tarkistus",
       CASE
           WHEN al.laskutusraja_alkuperainen IS NOT NULL
           THEN al.laskutusraja_alkuperainen + t.laskutusrajan_tarkistus
           ELSE NULL
       END                                                             AS "tarkistettu-laskutusraja",
       t.id,
       t.tyyppi,
       t.voimassa_alkaen
  FROM tarkistukset t
  LEFT JOIN alkuperainen_laskutusraja al ON TRUE
 ORDER BY t.voimassa_alkaen, t.id, t.versio;
