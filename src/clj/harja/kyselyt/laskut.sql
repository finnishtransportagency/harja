-- name: hae-urakan-laskut
-- Hakee urakan laskut annetulta aikaväliltä
SELECT l.id            as "laskun-id",
       l.viite         as "viite",
       l.kokonaissumma as "kokonaissumma",
       l.erapaiva      as "erapaiva",
       l.tyyppi        as "tyyppi",
       l.luotu         as "luontipvm",
       l.muokattu      as "muokkauspvm",
       l.koontilaskun_kuukausi as "koontilaskun-kuukausi"
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
       l.laskun_numero        as "laskun-numero",
       l.koontilaskun_kuukausi as "koontilaskun-kuukausi",
       lk.rivi                as "rivi",
       lk.summa               as "summa",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.tehtava             as "tehtava",
       l.suorittaja           as "suorittaja-id",
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
       JOIN aliurakoitsija a on l.suorittaja = a.id
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
       l.suorittaja           as "suorittaja-id",
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
       JOIN aliurakoitsija a on l.suorittaja = a.id
WHERE l.urakka = :urakka
  AND lk.suoritus_alku BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
  AND l.poistettu IS NOT TRUE;

-- name: hae-lasku
SELECT l.id            as "id",
       l.viite         as "viite",
       l.urakka        as "urakka",
       l.kokonaissumma as "kokonaissumma",
       l.erapaiva      as "erapaiva",
       l.laskun_numero as "laskun-numero",
       l.tyyppi        as "tyyppi",
       l.koontilaskun_kuukausi as "koontilaskun-kuukausi",
       l.suorittaja    as "suorittaja-id",
       a.nimi          as "suorittaja-nimi"

FROM lasku l
  left join aliurakoitsija a on l.suorittaja = a.id
where l.urakka = :urakka
  AND l.viite = :viite
  AND l.poistettu IS NOT TRUE;

-- name: hae-laskun-kohdistukset
SELECT lk.id                  as "kohdistus-id",
       lk.rivi                as "rivi",
       lk.summa               as "summa",
       lk.tehtava             as "tehtava",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       lk.luotu               as "luontiaika",
       lk.muokattu            as "muokkausaika",
       a.id                   as "suorittaja-id",
       a.nimi                 as "suorittaja-nimi"
  FROM lasku_kohdistus lk
  LEFT JOIN aliurakoitsija a ON (select suorittaja from lasku where id = :lasku) = a.id
 WHERE lk.lasku = :lasku
   AND lk.poistettu IS NOT TRUE
 ORDER by lk.id;

-- name: luo-tai-paivita-lasku<!
INSERT
  INTO lasku
       (viite, erapaiva, kokonaissumma, suorittaja, urakka, tyyppi, luotu, luoja, lisatieto,
        laskun_numero, koontilaskun_kuukausi)
VALUES (:viite, :erapaiva, :kokonaissumma, :suorittaja, :urakka, :tyyppi ::LASKUTYYPPI,
        current_timestamp, :kayttaja, :lisatieto, :numero, :koontilaskun-kuukausi)
ON CONFLICT (urakka, viite) DO
UPDATE SET erapaiva = :erapaiva,
           lisatieto = :lisatieto,
           laskun_numero = :numero,
           kokonaissumma = :kokonaissumma,
           suorittaja = :suorittaja,
           tyyppi = :tyyppi ::LASKUTYYPPI,
           muokattu = current_timestamp,
           muokkaaja = :kayttaja,
           koontilaskun_kuukausi = :koontilaskun-kuukausi;

-- name: luo-tai-paivita-laskun-kohdistus<!
INSERT
INTO lasku_kohdistus (lasku, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, suoritus_alku,
                      suoritus_loppu, luotu, luoja)
VALUES (:lasku, :rivi, :summa, :toimenpideinstanssi, :tehtavaryhma, :maksueratyyppi ::MAKSUERATYYPPI, :alkupvm, :loppupvm,
        current_timestamp, :kayttaja)
ON CONFLICT (lasku, rivi) DO UPDATE
  SET summa = :summa,
    toimenpideinstanssi = :toimenpideinstanssi,
    tehtavaryhma = :tehtavaryhma,
    maksueratyyppi = :maksueratyyppi ::MAKSUERATYYPPI,
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
