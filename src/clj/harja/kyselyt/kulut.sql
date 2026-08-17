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
WITH kohdistukset_ajalla AS (
    SELECT kk.summa, kk.tehtavaryhma
     FROM kulu_kohdistus kk
     JOIN kulu k ON kk.kulu = k.id
      AND k.urakka = :urakka
      AND k.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
    WHERE k.id = kk.kulu
      AND kk.poistettu IS NOT TRUE
)   SELECT tr.id      AS "tehtavaryhma",
        SUM(kohd.summa) AS "summa",
        tr.jarjestys    AS "jarjestys",
        tr.nimi         AS "nimi"
    FROM tehtavaryhma tr
        LEFT JOIN kohdistukset_ajalla kohd ON tr.id = kohd.tehtavaryhma,
        urakka u
    -- Ei ole tarkoituksenmukaista listata tehtäväryhmiä, jotka on poistettu tai jotka eivät ole voimassa, jos niihin ei ole raportoitu kuluja
    -- Mutta ei myöskään filtteröidä niitä ulos, jos sinne on ehditty raportoida kuluja
    WHERE u.id = :urakka
    -- Jos ei ole kuluja, niin tehtäväryhmää, joka on poistettu, ei oteta mukaan
    AND (tr.poistettu = false AND kohd.summa IS NULL OR kohd.summa IS NOT NULL)
    AND (tr.yksiloiva_tunniste IS NULL OR tr.yksiloiva_tunniste NOT IN(
        -- Urakoitsija maksaa alituksesta, ei kuulu raporttiin
        'be34116b-2264-43e0-8ac8-3762b27a9557',
        -- Urakoitsija maksaa ylityksestä, ei kuulu raporttiin
        '19907c24-dd26-460f-9cb4-2ed974b891aa',
        -- Tavoitepalkkio, ei myöskään kuulu raporttiin
        '55c920e7-5656-4bb0-8437-1999add714a3',
        -- Alataso,  listätyöt
        'c7d9be7c-7bea-49a4-bd30-a432041cf6dd')
    )
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
SELECT m.numero                  AS "maksuera-numero",
       m.maksuera_alias          AS "maksuera-alias",
       k.id                      AS "id",
       k.kokonaissumma           AS "kokonaissumma",
       k.erapaiva                AS "erapaiva",
       k.laskun_numero           AS "laskun-numero",
       k.koontilaskun_kuukausi   AS "koontilaskun-kuukausi",
       k.lisatieto               AS "lisatieto",
       kk.id                     AS "kohdistus-id",
       kk.rivi                   AS "rivi",
       kk.summa                  AS "summa",
       kk.toimenpideinstanssi    AS "toimenpideinstanssi",
       kk.tehtavaryhma           AS "tehtavaryhma",
       kk.lisatyon_lisatieto     AS "lisatyon-lisatieto",
       kk.maksueratyyppi         AS "maksueratyyppi",
       kk.rahavaraus_id          AS rahavaraus,
       kk.tyyppi                 AS tyyppi,
       kk.tehtava                AS "tehtava_id",
       t.nimi                    AS "tehtava_nimi",
       kk."muu-tehtava-kaytossa" AS "muu-tehtava-kaytossa",
       kk.muutos                 AS "muutos-id",
       muutos.voimassa_alkaen    AS "muutos-voimassa-alkaen",
       muutos.nimi               AS "muutos-nimi"
FROM   kulu k
       JOIN kulu_kohdistus kk ON k.id = kk.kulu
       AND kk.poistettu IS NOT TRUE
       LEFT JOIN maksuera m ON kk.toimenpideinstanssi = m.toimenpideinstanssi
       LEFT JOIN tehtava t ON kk.tehtava = t.id
       LEFT JOIN mhu_muutos muutos ON muutos.id = kk.muutos
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
SELECT kk.id                                      AS "kohdistus-id",
       kk.rivi                                    AS "rivi",
       kk.summa                                   AS "summa",
       kk.tehtavaryhma                            AS "tehtavaryhma",
       kk.toimenpideinstanssi                     AS "toimenpideinstanssi",
       kk.luotu                                   AS "luontiaika",
       kk.muokattu                                AS "muokkausaika",
       kk.lisatyon_lisatieto                      AS "lisatyon-lisatieto",
       kk.maksueratyyppi                          AS "maksueratyyppi",
       kk.rahavaraus_id                           AS rahavaraus_id,
       COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), rv.nimi) AS rahavaraus_nimi,
       kk.tyyppi                                  AS tyyppi,
       kk.tavoitehintainen                        AS tavoitehintainen,
       CASE WHEN kk.tehtava IS NOT NULL THEN
         jsonb_build_object(
           'id', t.id,
           'nimi', t.nimi,
           'jarjestys', t.jarjestys,
           'emo', t.emo,
           'maaramitattava?', t."maaramitattava?",
           'toimenpideinstanssi', kk.toimenpideinstanssi
         )
       ELSE NULL END                              AS "tehtava",
       kk."muu-tehtava-kaytossa"                  AS "muu-tehtava-kaytossa",
       kk.muutos                                  AS "muutos-id",
       muutos.voimassa_alkaen                     AS "muutos-voimassa-alkaen",
       muutos.nimi                                AS "muutos-nimi"
  FROM kulu_kohdistus kk
           LEFT JOIN rahavaraus rv ON kk.rahavaraus_id = rv.id
           LEFT JOIN rahavaraus_urakka ru ON rv.id = ru.rahavaraus_id AND ru.urakka_id = :urakka_id
           LEFT JOIN tehtava t ON kk.tehtava = t.id
           LEFT JOIN mhu_muutos muutos ON muutos.id = kk.muutos
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
                     lisatyon_lisatieto, rahavaraus_id, tavoitehintainen, tehtava, "muu-tehtava-kaytossa")
VALUES (:kulu, :rivi, :summa, :toimenpideinstanssi, :tehtavaryhma, :maksueratyyppi ::MAKSUERATYYPPI,
        :tyyppi::KOHDISTUSTYYPPI, current_timestamp, :kayttaja, :lisatyon-lisatieto,
        :rahavarausid, :tavoitehintainen::BOOLEAN, :tehtava-id, :muu-tehtava-kaytossa);

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
    tavoitehintainen = :tavoitehintainen::BOOLEAN,
    tehtava = :tehtava-id,
    "muu-tehtava-kaytossa" = :muu-tehtava-kaytossa
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

-- name: hae-toteutuneet-kustannukset-analytiikalle
SELECT k.id                        AS "kulu-id",
       k.laskun_numero             AS "laskun-tunniste",
       k.lisatieto                 AS "kulun-kuvaus",
       k.poistettu                 AS "poistettu",
       k.koontilaskun_kuukausi     AS "koontilaskun-kuukausi",
       k.erapaiva                  AS "kulun-ajankohta_laskun-paivamaara",
       k.kokonaissumma             AS "kulun-kokonaissumma",
       jsonb_agg(row_to_json(row (
                                 kk.id, -- AS "kulukohdistus_kulukohdistus-id",
                                 kk.rivi, -- AS "kulukohdistus_rivinumero",
                                 kk.maksueratyyppi, -- AS "kulukohdistus_tyyppi",
                                 kk.tavoitehintainen, -- AS "kulukohdistus_tavoitehintainen"
                                 kk.lisatyon_lisatieto, -- AS "kulukohdistus_lisatieto",
                                 kk.poistettu, -- AS "kulukohdistus_poistettu",
                                 kk.summa, -- AS "kulukohdistus_summa",
                                 tp.id, -- AS kohdistus_toimenpide,
                                 tr.id, -- AS kohdistus_tehtavaryhma,
                                 rv.id, -- AS kohdistus_rahavaraus,
                                 te.id -- AS kohdistus_tehtava
                                 ))) as kulukohdistukset
  FROM kulu k
           JOIN kulu_kohdistus kk ON k.id = kk.kulu
           JOIN urakka u ON k.urakka = u.id
           JOIN toimenpideinstanssi tpi ON kk.toimenpideinstanssi = tpi.id
           JOIN toimenpide tp ON tpi.toimenpide = tp.id
           LEFT JOIN tehtavaryhma tr ON kk.tehtavaryhma = tr.id
           LEFT JOIN rahavaraus rv ON kk.rahavaraus_id = rv.id
           LEFT JOIN tehtava te ON te.id = kk.tehtava
 WHERE u.id = :urakka-id
 GROUP BY k.id, k.laskun_numero, k.lisatieto, k.poistettu, k.koontilaskun_kuukausi, k.erapaiva, k.kokonaissumma
 ORDER BY k.erapaiva;

-- name: hae-urakan-laskutusraja
SELECT ut.laskutusraja,
       up.laskutusraja_kaytossa AS "laskutusraja-kaytossa"
  FROM urakka_tavoite ut
       LEFT JOIN urakka_parametrit up ON up.urakkaid = ut.urakka
 WHERE ut.urakka     = :urakka-id
   AND ut.hoitokausi = :hoitokausinro;

-- name: hae-urakan-alkuperainen-laskutusraja
SELECT ut.laskutusraja_alkuperainen,
       up.laskutusraja_kaytossa AS "laskutusraja-kaytossa"
FROM urakka_tavoite ut
         LEFT JOIN urakka_parametrit up ON up.urakkaid = ut.urakka
WHERE ut.urakka     = :urakka-id
  AND ut.hoitokausi = :hoitokausinro;

-- name: paivita-urakan-laskutusraja!
UPDATE urakka_tavoite
SET laskutusraja = :laskutusraja,
    muokattu     = CURRENT_TIMESTAMP,
    muokkaaja    = :kayttaja
WHERE urakka     = :urakka-id
  AND hoitokausi = :hoitokausinro;

-- name: hae-urakan-toteutuneet-kustannukset
-- Hakee urakan toteutuneet kustannukset annetulta aikaväliltä
-- Tehtäväryhmä haetaan tehtava-taulun kautta, jos sitä ei ole toteutuneet_kustannukset-taulussa
SELECT NULL                       AS "maksuera-numero",
       NULL                       AS "maksuera-alias",
       tk.id                      AS "id",
       tk.summa                   AS "kokonaissumma",
       make_date(tk.vuosi, tk.kuukausi, 1) AS "erapaiva",
       NULL                       AS "laskun-numero",
       tk.kuukausi                AS "koontilaskun-kuukausi",
       'Harjan automaattisesti luoma kulu.'     AS "lisatieto",
       tk.id                      AS "kohdistus-id",
       1                          AS "rivi",
       tk.summa_indeksikorjattu   AS "summa",
       tk.toimenpideinstanssi     AS "toimenpideinstanssi",
       tr.id                      AS "tehtavaryhma",
       NULL                       AS "lisatyon-lisatieto",
       'kokonaishintainen'::maksueratyyppi AS "maksueratyyppi",
       NULL                       AS rahavaraus,
       tk.tyyppi                  AS tyyppi,
       tk.tehtava                 AS "tehtava_id",
       t.nimi                     AS "tehtava_nimi",
       false                      AS "muu-tehtava-kaytossa",
       NULL                       AS "muutos-id",
       NULL                       AS "muutos-voimassa-alkaen",
       NULL                       AS "muutos-nimi",
       true                       AS "harjan-generoima"
  FROM toteutuneet_kustannukset tk
       LEFT JOIN tehtava t ON tk.tehtava = t.id
       LEFT JOIN tehtavaryhma tr ON COALESCE(tk.tehtavaryhma, t.tehtavaryhma) = tr.id
 WHERE tk.urakka_id = :urakka
   AND (:alkupvm::DATE IS NULL OR :alkupvm::DATE <= make_date(tk.vuosi, tk.kuukausi, 1))
   AND (:loppupvm::DATE IS NULL OR make_date(tk.vuosi, tk.kuukausi, 1) <= :loppupvm::DATE);

-- name: hae-kaikkien-tehtavaryhmien-nimet
-- Tarvitaan Kulujen kohdistus -näkymän generoitujen kulujen tehtäväryhmien nimien näyttämistä varten,
-- jotta saadaan myös Johto- ja hallintokorvaus -tehtäväryhmän nimi näkyviin
SELECT tr.id                      AS "tehtavaryhma",
       tr.nimi                    AS "tehtavaryhma_nimi",
       tr.toimenpide_id           AS "tehtavaryhma_toimenpide_id"
FROM tehtavaryhma tr;
