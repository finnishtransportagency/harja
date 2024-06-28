-- name: hae-urakan-kulut
-- Hakee urakan kulut annetulta aikaväliltä
SELECT k.id            AS "id",
       k.kokonaissumma AS "kokonaissumma",
       k.erapaiva      AS "erapaiva",
       k.luotu         AS "luontipvm",
       k.muokattu      AS "muokkauspvm",
       k.koontilaskun_kuukausi AS "koontilaskun-kuukausi"
FROM kulu k
WHERE k.urakka = :urakka
  AND k.erapaiva BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
  AND k.poistettu IS NOT TRUE;

-- name: hae-urakan-johto-ja-hallintokorvaus-raporttiin-aikavalilla
SELECT tr.nimi AS "nimi",
       tr.id AS "tehtavaryhma",
       tr.jarjestys AS "jarjestys",
       jhk."toimenkuva-id",
       sum(jhk.tuntipalkka * jhk.tunnit) AS "summa"
FROM johto_ja_hallintokorvaus jhk
  JOIN tehtavaryhma tr ON tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54' -- johto- ja hallintokorvaus-tehtäväryhmän yksilöivä tunniste, näitä on muutamilla tehtäväryhmillä ja toimenpidekoodeilla, niin jos nimet muuttuu niin näillä ne löytyy luotettavasti
WHERE jhk."urakka-id" = :urakka
  AND format('%s-%s-%s', jhk.vuosi, jhk.kuukausi, 1)::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE
GROUP BY jhk."toimenkuva-id", tr.nimi, tr.id, tr.jarjestys;

-- name: hae-urakan-hj-kulut-raporttiin-aikavalilla
SELECT rs.nimi AS "nimi",
       rs.jarjestys AS "jarjestys",
       rs.tehtavaryhma AS "tehtavaryhma",
       sum(rs.summa) AS "summa"
FROM (SELECT tr.nimi      AS "nimi",
             tr.id        AS "tehtavaryhma",
             tr.jarjestys AS "jarjestys",
             kt.summa     AS "summa"
      FROM kustannusarvioitu_tyo kt
             JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id AND tpi.urakka = :urakka
             JOIN tehtava tpk
             JOIN tehtavaryhma tr ON tr.id = tpk.tehtavaryhma
                  ON tpk.id = kt.tehtava AND tpk.nimi = 'Hoidonjohtopalkkio'
      WHERE format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE
      UNION ALL
      SELECT tr.nimi      AS "nimi",
             tr.id        AS "tehtavaryhma",
             tr.jarjestys AS "jarjestys",
             kt.summa     AS "summa"
      FROM kustannusarvioitu_tyo kt
             JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id AND tpi.urakka = :urakka
             JOIN tehtava tpk
             JOIN tehtavaryhma tr
                  ON tpk.tehtavaryhma = tr.id AND tr.yksiloiva_tunniste in ('a6614475-1950-4a61-82c6-fda0fd19bb54') -- Tilaajan varaukset -tehtäväryhmän yksilöivä tunniste
                  ON tpk.id = kt.tehtava
      WHERE format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  UNION ALL
      SELECT tr.nimi      AS "nimi",
             tr.id        AS "tehtavaryhma",
             tr.jarjestys AS "jarjestys",
             kt.summa     AS "summa"
      FROM kustannusarvioitu_tyo kt
             JOIN toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id AND tpi.urakka = :urakka
             JOIN tehtavaryhma tr
                  ON kt.tehtavaryhma = tr.id AND tr.yksiloiva_tunniste in ('37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat -tehtäväryhmän yksilöivä tunniste
      WHERE format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE) rs
GROUP BY rs.nimi, rs.tehtavaryhma, rs.jarjestys
ORDER BY rs.jarjestys;

-- name: hae-urakan-kulut-raporttiin-aikavalilla
-- Annetulla aikavälillä haetaan urakan kaikki kulut tehtäväryhmittäin
  WITH kohdistukset_ajalla AS (SELECT kk.summa, kk.tehtavaryhma
                                 FROM kulu_kohdistus kk
                                          JOIN kulu k ON kk.kulu = k.id
                                     AND k.urakka = :urakka
                                     AND k.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
                                WHERE k.id = kk.kulu
                                  AND kk.poistettu IS NOT TRUE)
SELECT tr.id          AS "tehtavaryhma",
       SUM(kohd.summa) AS "summa",
       tr.jarjestys   AS "jarjestys",
       tr.nimi        AS "nimi"
  FROM tehtavaryhma tr
           LEFT JOIN kohdistukset_ajalla kohd ON tr.id = kohd.tehtavaryhma,
       urakka u
  -- Ei ole tarkoituksenmukaista listata tehtäväryhmiä, jotka on poistettu tai jotka eivät ole voimassa, jos niihin ei ole raportoitu kuluja
  -- Mutta ei myöskään filtteröidä niitä ulos, jos sinne on ehditty raportoida kuluja
 WHERE u.id = :urakka
   -- Jos ei ole kuluja, niin tehtäväryhmää, joka on poistettu, ei oteta mukaan
   AND (tr.poistettu = false AND kohd.summa IS NULL OR kohd.summa IS NOT NULL)
   -- Jos ei ole kuluja, niin tehtävärymän on oltava voimassa
   AND (kohd.summa IS NULL
        AND (tr.voimassaolo_alkuvuosi IS NULL OR tr.voimassaolo_alkuvuosi <= EXTRACT(YEAR from u.alkupvm)::INTEGER)
        AND (tr.voimassaolo_loppuvuosi IS NULL OR tr.voimassaolo_loppuvuosi >= EXTRACT(YEAR from u.alkupvm)::INTEGER)
     OR kohd.summa IS NOT NULL)
 GROUP BY tr.nimi, tr.id, tr.jarjestys
 ORDER BY tr.jarjestys;

-- name: hae-liitteet
-- Haetaan liitteet kululle
SELECT liite.id               AS "liite-id",
       liite.nimi             AS "liite-nimi",
       liite.tyyppi           AS "liite-tyyppi",
       liite.koko             AS "liite-koko",
       liite.liite_oid        AS "liite-oid"
       FROM kulu k
       JOIN kulu_liite kl ON k.id = kl.kulu AND kl.poistettu IS NOT true
       JOIN liite liite ON kl.liite = liite.id
       WHERE k.id = :kulu-id;

-- name: hae-kulut-kohdistuksineen-tietoineen-vientiin
-- Hakee PDF/Excel -generointiin tarvittavien tietojen kanssa urakan kulut
SELECT
       tpi.nimi AS "toimenpide",
       tr.nimi AS "tehtavaryhma",
       kk.summa,
       me.numero AS "maksuera",
       k.erapaiva,
       u.nimi AS "urakka"
FROM   kulu k
       JOIN urakka u ON k.urakka = u.id
       JOIN kulu_kohdistus kk ON k.id = kk.kulu
       JOIN toimenpideinstanssi tpi ON kk.toimenpideinstanssi = tpi.id
       JOIN maksuera me ON kk.toimenpideinstanssi = me.toimenpideinstanssi
       LEFT JOIN tehtavaryhma tr ON kk.tehtavaryhma = tr.id
WHERE  k.urakka = :urakka
       AND k.erapaiva BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
       AND k.poistettu IS NOT true
       AND kk.poistettu IS NOT true;

-- name: hae-pvm-laskun-numerolla
-- Hakee laskun päivämäärän urakalle käyttäen laskun numeroa.  Yhtä laskun numeroa voi käyttää yhden pvm:n yhteydessä, mutta useampi lasku voi käyttää samaa numeroa samalla pvm:llä.
SELECT
  k.erapaiva AS "erapaiva"
FROM kulu k
WHERE k.urakka = :urakka
  AND k.laskun_numero = :laskun-numero
  AND k.poistettu IS NOT true;

-- name: hae-urakan-kulut-kohdistuksineen
-- Hakee urakan kulut ja niihin liittyvät kohdistukset annetulta aikaväliltä
SELECT m.numero                AS "maksuera-numero",
 	   k.id                    AS "id",
       k.kokonaissumma         AS "kokonaissumma",
       k.erapaiva              AS "erapaiva",
       k.laskun_numero         AS "laskun-numero",
       k.koontilaskun_kuukausi AS "koontilaskun-kuukausi",
       k.lisatieto             AS "lisatieto",
       kk.id                   AS "kohdistus-id",
       kk.rivi                 AS "rivi",
       kk.summa                AS "summa",
       kk.toimenpideinstanssi  AS "toimenpideinstanssi",
       kk.tehtavaryhma         AS "tehtavaryhma",
       kk.lisatyon_lisatieto   AS "lisatyon-lisatieto",
       kk.maksueratyyppi       AS "maksueratyyppi",
       kk.rahavaraus_id        AS rahavaraus,
       kk.tyyppi               AS tyyppi
FROM   kulu k
       JOIN kulu_kohdistus kk ON k.id = kk.kulu 
       AND kk.poistettu IS NOT TRUE
       LEFT JOIN maksuera m ON kk.toimenpideinstanssi = m.toimenpideinstanssi
WHERE  k.urakka = :urakka
AND    (:alkupvm::DATE IS NULL OR :alkupvm::DATE <= k.erapaiva)
AND    (:loppupvm::DATE IS NULL OR k.erapaiva <= :loppupvm::DATE)
AND    k.poistettu IS NOT TRUE;

-- name: linkita-kulu-ja-liite<!
-- Linkittää liitteen ja kulun
insert into kulu_liite (kulu, liite, luotu, luoja, poistettu)
values (:kulu-id, :liite-id, current_timestamp, :kayttaja, false)
ON conflict do nothing;

-- name: poista-kulun-ja-liitteen-linkitys!
-- Merkkaa liitteen poistetuksi
UPDATE kulu_liite kl
SET poistettu = true,
 muokkaaja = :kayttaja,
 muokattu = current_timestamp
 WHERE kl.kulu = :kulu-id AND kl.liite = :liite-id;

-- name: hae-kulu
SELECT k.id            AS "id",
       k.urakka        AS "urakka",
       k.kokonaissumma AS "kokonaissumma",
       k.erapaiva      AS "erapaiva",
       k.laskun_numero AS "laskun-numero",
       k.koontilaskun_kuukausi AS "koontilaskun-kuukausi",
       k.lisatieto     AS "lisatieto"
FROM kulu k
WHERE k.id = :id
  AND k.poistettu IS NOT TRUE;

-- name: hae-kulun-kohdistukset
SELECT kk.id                  AS "kohdistus-id",
       kk.rivi                AS "rivi",
       kk.summa               AS "summa",
       kk.tehtavaryhma        AS "tehtavaryhma",
       kk.toimenpideinstanssi AS "toimenpideinstanssi",
       kk.luotu               AS "luontiaika",
       kk.muokattu            AS "muokkausaika",
       kk.lisatyon_lisatieto  AS "lisatyon-lisatieto",
       kk.maksueratyyppi      AS "maksueratyyppi",
       kk.rahavaraus_id       AS rahavaraus_id,
       rv.nimi                AS rahavaraus_nimi,
       kk.tyyppi              AS tyyppi,
       kk.tavoitehintainen    AS tavoitehintainen
  FROM kulu_kohdistus kk
        LEFT JOIN rahavaraus rv ON kk.rahavaraus_id = rv.id
 WHERE kk.kulu = :kulu
   AND kk.poistettu IS NOT TRUE
 ORDER BY kk.id;

-- name: luo-kulu<!
INSERT
  INTO kulu
       (erapaiva, kokonaissumma, urakka, luotu, luoja, lisatieto, laskun_numero, koontilaskun_kuukausi)
VALUES (:erapaiva, :kokonaissumma, :urakka, current_timestamp, :kayttaja, :lisatieto, :numero, :koontilaskun-kuukausi);

-- name: paivita-kulu<!
UPDATE
  kulu
      SET  erapaiva = :erapaiva,
           lisatieto = :lisatieto,
           laskun_numero = :numero,
           kokonaissumma = :kokonaissumma,
           muokattu = current_timestamp,
           muokkaaja = :kayttaja,
           koontilaskun_kuukausi = :koontilaskun-kuukausi
          WHERE id = :id;

-- name: luo-kulun-kohdistus<!
INSERT
INTO kulu_kohdistus (kulu, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, tyyppi, luotu, luoja,
                     lisatyon_lisatieto, rahavaraus_id, tavoitehintainen)
VALUES (:kulu, :rivi, :summa, :toimenpideinstanssi, :tehtavaryhma, :maksueratyyppi ::MAKSUERATYYPPI,
        :tyyppi::KOHDISTUSTYYPPI, current_timestamp, :kayttaja, :lisatyon-lisatieto,
        :rahavarausid, :tavoitehintainen::BOOLEAN);

-- name: paivita-kulun-kohdistus<!
UPDATE kulu_kohdistus
SET summa = :summa,
    toimenpideinstanssi = :toimenpideinstanssi,
    tehtavaryhma = :tehtavaryhma,
    maksueratyyppi = :maksueratyyppi ::MAKSUERATYYPPI,
    tyyppi = :tyyppi ::KOHDISTUSTYYPPI,
    muokattu = current_timestamp,
    muokkaaja = :kayttaja,
    lisatyon_lisatieto = :lisatyon-lisatieto,
    rahavaraus_id = :rahavarausid,
    tavoitehintainen = :tavoitehintainen::BOOLEAN
WHERE id = :id;

-- name: poista-kulu!
UPDATE kulu
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE id = :id;

-- name: poista-kulun-kohdistukset!
UPDATE kulu_kohdistus
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE kulu = :id;

-- name: poista-kulun-kohdistus!
UPDATE kulu_kohdistus
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE kulu = :id
  AND id = :kohdistuksen-id;

-- name: hae-tehtavan-nimi
SELECT nimi FROM tehtava
WHERE id = :id AND poistettu IS NOT TRUE;

-- name: hae-tehtavaryhman-nimi
SELECT nimi FROM tehtavaryhma
WHERE id = :id AND poistettu IS NOT TRUE;

-- name: hae-tehtavaryhman-tiedot-tunnisteella
SELECT tr.id, nimi, o.otsikko, tr.jarjestys, tr.luotu, tr.luoja, tr.muokattu, tr.muokkaaja
  FROM tehtavaryhma tr
  JOIN tehtavaryhmaotsikko o ON tr.tehtavaryhmaotsikko_id = o.id
 WHERE yksiloiva_tunniste = :tunniste::UUID;

-- name: hae-urakan-hoidon-johdon-toimenpideinstanssi
SELECT tpi.id, tpi.toimenpide, tpi.nimi, tpi.alkupvm, tpi.loppupvm, tpi.sampoid
FROM toimenpideinstanssi tpi
         JOIN toimenpide tp1 ON tp1.id = tpi.toimenpide
         JOIN toimenpide tp2 ON tp1.emo = tp2.id
WHERE tpi.urakka = :urakka
  AND tp2.koodi = '23150'
LIMIT 1;

-- name: tarkista-kohdistuksen-yhteensopivuus
SELECT * FROM tarkista_t_tr_ti_yhteensopivuus(:tehtava-id::INTEGER, :tehtavaryhma-id::INTEGER, :toimenpideinstanssi-id::INTEGER);
