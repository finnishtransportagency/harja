-- name: hae-urakan-laskut
-- Hakee urakan laskut annetulta aikaväliltä
SELECT l.id            as "laskun-id",
       l.viite         as "viite",
       l.kokonaissumma as "kokonaissumma",
       l.erapaiva      as "erapaiva",
       l.tyyppi        as "tyyppi",
       l.luotu         as "luontipvm",
       l.muokattu      as "muokkauspvm"
from lasku l
WHERE l.urakka = :urakka
  AND l.erapaiva BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
  AND l.poistettu IS NOT TRUE;

-- name: hae-kaikki-urakan-laskuerittelyt
-- Hakee kaikki urakan laskut ja niihin liittyvät kohdistukset
SELECT l.id                   as "laskun-id",
       l.viite                as "viite",
       l.kokonaissumma        as "kokonaissumma",
       l.erapaiva             as "erapaiva",
       l.tyyppi               as "tyyppi",
       lk.rivi                as "rivi",
       lk.summa               as "summa",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.tehtava             as "tehtava",
       lk.suorittaja          as "suorittaja-id",
       a.nimi                 as "suorittaja-nimi",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       liite.id               AS "liite-id",
       liite.nimi             AS "liite-nimi",
       liite.tyyppi           AS "liite-tyyppi",
       liite.koko             AS "liite-koko",
       liite.liite_oid        AS "liite-oid"
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
       LEFT JOIN lasku_liite ll on l.id = ll.lasku
       LEFT JOIN liite liite on ll.liite = liite.id
       JOIN aliurakoitsija a on lk.suorittaja = a.id
WHERE l.urakka = :urakka
  AND l.poistettu IS NOT TRUE;

-- name: hae-urakan-laskuerittelyt
-- Hakee urakan laskut ja niihin liittyvät kohdistukset annetulta aikaväliltä
SELECT l.id                   as "laskun-id",
       l.viite                as "viite",
       l.kokonaissumma        as "kokonaissumma",
       l.erapaiva             as "erapaiva",
       l.tyyppi               as "tyyppi",
       lk.rivi                as "rivi",
       lk.summa               as "summa",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.tehtava             as "tehtava",
       lk.suorittaja          as "suorittaja-id",
       a.nimi                 as "suorittaja-nimi",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       liite.id               AS "liite-id",
       liite.nimi             AS "liite-nimi",
       liite.tyyppi           AS "liite-tyyppi",
       liite.koko             AS "liite-koko",
       liite.liite_oid        AS "liite-oid"
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
       LEFT JOIN lasku_liite ll on l.id = ll.lasku
       LEFT JOIN liite liite on ll.liite = liite.id
       JOIN aliurakoitsija a on lk.suorittaja = a.id
WHERE l.urakka = :urakka
  AND lk.suoritus_alku BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
  AND l.poistettu IS NOT TRUE;

-- name: hae-lasku
SELECT id            as "id",
       viite         as "viite",
       urakka        as "urakka",
       kokonaissumma as "kokonaissumma",
       erapaiva      as "erapaiva",
       tyyppi        as "tyyppi"
FROM lasku
where urakka = :urakka
  AND viite = :viite
  AND poistettu IS NOT TRUE;

-- name: hae-laskun-kohdistukset
SELECT lk.id                  as "kohdistus-id",
       lk.rivi                as "rivi",
       lk.summa               as "summa",
       lk.tehtava             as "tehtava",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       lk.suorittaja          as "suorittaja-id",
       a.nimi                 as "suorittaja-nimi",
       lk.luotu               as "luontiaika",
       lk.muokattu            as "muokkausaika"
from lasku_kohdistus lk
       LEFT JOIN aliurakoitsija a ON lk.suorittaja = a.id
WHERE lk.lasku = :lasku
  AND lk.poistettu IS NOT TRUE
ORDER by lk.id;

-- name: luo-tai-paivita-lasku<!
INSERT
INTO lasku (viite, erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja, lisatieto, laskun_numero)
VALUES (:viite, :erapaiva, :kokonaissumma, :urakka, :tyyppi ::LASKUTYYPPI, current_timestamp, :kayttaja, :lisatieto, :numero)
ON CONFLICT (viite) DO UPDATE
  SET erapaiva = :erapaiva,
    lisatieto = :lisatieto,
    laskun_numero = :numero,
    kokonaissumma = :kokonaissumma,

    tyyppi = :tyyppi ::LASKUTYYPPI,
    muokattu = current_timestamp,
    muokkaaja = :kayttaja;

-- name: luo-tai-paivita-laskun-kohdistus<!
INSERT
INTO lasku_kohdistus (lasku, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, suorittaja, suoritus_alku,
                      suoritus_loppu, luotu, luoja)
VALUES (:lasku, :rivi, :summa, :toimenpideinstanssi, :tehtavaryhma, :maksueratyyppi ::MAKSUERATYYPPI, :suorittaja, :alkupvm, :loppupvm,
        current_timestamp, :kayttaja)
ON CONFLICT (lasku, rivi) DO UPDATE
  SET summa = :summa,
    toimenpideinstanssi = :toimenpideinstanssi,
    tehtavaryhma = :tehtavaryhma,
    maksueratyyppi = :maksueratyyppi ::MAKSUERATYYPPI,
    suorittaja = :suorittaja,
    suoritus_alku = :alkupvm,
    suoritus_loppu = :loppupvm,
    muokattu = current_timestamp,
    muokkaaja = :kayttaja;

-- name: poista-lasku!
UPDATE lasku
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE urakka = :urakka
  AND viite = :viite;

-- name: poista-laskun-kohdistukset!
UPDATE lasku_kohdistus
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE lasku = (select id from lasku where viite = :viite and urakka = :urakka);

-- name: poista-laskun-kohdistus!
UPDATE lasku_kohdistus
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE lasku = (select id from lasku where viite = :viite and urakka = :urakka)
  AND rivi = :rivi;

-- name: hae-tehtavan-nimi
SELECT nimi FROM toimenpidekoodi
WHERE id = :id AND poistettu IS NOT TRUE;

-- name: hae-tehtavaryhman-nimi
SELECT nimi FROM tehtavaryhma
WHERE id = :id AND poistettu IS NOT TRUE;



