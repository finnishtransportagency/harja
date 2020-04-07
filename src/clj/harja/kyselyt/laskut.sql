-- name: hae-urakan-laskut
-- Hakee urakan laskut annetulta aikaväliltä
SELECT l.id            as "id",
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

-- name: hae-liitteet
-- Haetaan liitteet laskulle
select liite.id               AS "liite-id",
       liite.nimi             AS "liite-nimi",
       liite.tyyppi           AS "liite-tyyppi",
       liite.koko             AS "liite-koko",
       liite.liite_oid        AS "liite-oid"
       from lasku l
       join lasku_liite ll on l.id = ll.lasku and ll.poistettu is not true
       join liite liite on ll.liite = liite.id
       where l.id = :lasku-id;

-- name: hae-kaikki-urakan-laskuerittelyt
-- Hakee kaikki urakan laskut ja niihin liittyvät kohdistukset
SELECT l.id                   as "id",
       l.kokonaissumma        as "kokonaissumma",
       l.erapaiva             as "erapaiva",
       l.tyyppi               as "tyyppi",
       l.laskun_numero        as "laskun-numero",
       l.koontilaskun_kuukausi as "koontilaskun-kuukausi",
       l.lisatieto            as "lisatieto",
       lk.id                  as "kohdistus-id",
       lk.rivi                as "rivi",
       lk.summa               as "summa",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.tehtava             as "tehtava",
       l.suorittaja           as "suorittaja-id",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu"
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
       left JOIN aliurakoitsija a on l.suorittaja = a.id
WHERE l.urakka = :urakka
  AND l.poistettu IS NOT TRUE;

-- name: linkita-lasku-ja-liite<!
-- Linkittää liitteen ja laskun
insert into lasku_liite (lasku, liite, luotu, luoja, poistettu)
values (:lasku-id, :liite-id, current_timestamp, :kayttaja, false)
on conflict do nothing;

-- name: poista-laskun-ja-liitteen-linkitys!
-- Merkkaa liitteen poistetuksi
update lasku_liite ll
set poistettu = true,
 muokkaaja = :kayttaja,
 muokattu = current_timestamp
 where ll.lasku = :lasku-id and ll.liite = :liite-id;

-- name: hae-urakan-laskuerittelyt
-- Hakee urakan laskut ja niihin liittyvät kohdistukset annetulta aikaväliltä
SELECT l.id                   as "id",
       l.kokonaissumma        as "kokonaissumma",
       l.erapaiva             as "erapaiva",
       l.tyyppi               as "tyyppi",
       l.lisatieto            as "lisatieto",
       lk.rivi                as "rivi",
       lk.summa               as "summa",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.tehtava             as "tehtava",
       l.suorittaja           as "suorittaja-id",
       a.nimi                 as "suorittaja-nimi",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu"
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
       JOIN aliurakoitsija a on l.suorittaja = a.id
WHERE l.urakka = :urakka
  AND lk.suoritus_alku BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
  AND l.poistettu IS NOT TRUE;

-- name: hae-lasku
SELECT l.id            as "id",
       l.urakka        as "urakka",
       l.kokonaissumma as "kokonaissumma",
       l.erapaiva      as "erapaiva",
       l.laskun_numero as "laskun-numero",
       l.tyyppi        as "tyyppi",
       l.koontilaskun_kuukausi as "koontilaskun-kuukausi",
       l.suorittaja    as "suorittaja",
       l.lisatieto     as "lisatieto",
       a.nimi          as "suorittaja-nimi"
FROM lasku l
  left join aliurakoitsija a on l.suorittaja = a.id
where l.id = :id
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

-- name: luo-lasku<!
INSERT
  INTO lasku
       (erapaiva, kokonaissumma, suorittaja, urakka, tyyppi, luotu, luoja, lisatieto,
        laskun_numero, koontilaskun_kuukausi)
VALUES (:erapaiva, :kokonaissumma, :suorittaja, :urakka, :tyyppi ::LASKUTYYPPI,
        current_timestamp, :kayttaja, :lisatieto, :numero, :koontilaskun-kuukausi);

-- name: paivita-lasku<!
update
  lasku
      SET  erapaiva = :erapaiva,
           lisatieto = :lisatieto,
           laskun_numero = :numero,
           kokonaissumma = :kokonaissumma,
           suorittaja = :suorittaja,
           tyyppi = :tyyppi ::LASKUTYYPPI,
           muokattu = current_timestamp,
           muokkaaja = :kayttaja,
           koontilaskun_kuukausi = :koontilaskun-kuukausi
          where id = :id;

-- name: luo-laskun-kohdistus<!
INSERT
INTO lasku_kohdistus (lasku, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, suoritus_alku,
                      suoritus_loppu, luotu, luoja)
VALUES (:lasku, :rivi, :summa, :toimenpideinstanssi, :tehtavaryhma, :maksueratyyppi ::MAKSUERATYYPPI, :alkupvm, :loppupvm,
        current_timestamp, :kayttaja);

-- name: paivita-laskun-kohdistus<!
update lasku_kohdistus
set summa = :summa,
    toimenpideinstanssi = :toimenpideinstanssi,
    tehtavaryhma = :tehtavaryhma,
    maksueratyyppi = :maksueratyyppi ::MAKSUERATYYPPI,
    suoritus_alku = :alkupvm,
    suoritus_loppu = :loppupvm,
    muokattu = current_timestamp,
    muokkaaja = :kayttaja
where id = :id;

-- name: poista-lasku!
UPDATE lasku
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE id = :id;

-- name: poista-laskun-kohdistukset!
UPDATE lasku_kohdistus
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE lasku = :id;

-- name: poista-laskun-kohdistus!
UPDATE lasku_kohdistus
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE lasku = :id
  AND id = :kohdistuksen-id;

-- name: hae-tehtavan-nimi
SELECT nimi FROM toimenpidekoodi
WHERE id = :id AND poistettu IS NOT TRUE;

-- name: hae-tehtavaryhman-nimi
SELECT nimi FROM tehtavaryhma
WHERE id = :id AND poistettu IS NOT TRUE;
