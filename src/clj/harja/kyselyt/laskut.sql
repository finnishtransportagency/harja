-- name: hae-urakan-laskut
SELECT l.id                   as "laskun-id",
       l.viite                as "viite",
       l.kokonaissumma        as "kokonaissumma",
       l.erapaiva             as "erapaiva",
       lk.summa               as "summa",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.tehtava             as "tehtava",
       lk.suorittaja          as "suorittaja-id",
       a.nimi                 as "suorittaja-nimi",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       l.tyyppi               as "tyyppi",
       liite.id               AS liite_id,
       liite.nimi             AS liite_nimi,
       liite.tyyppi           AS liite_tyyppi,
       liite.koko             AS liite_koko,
       liite.liite_oid        AS liite_oid
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
       LEFT JOIN lasku_liite ll on l.id = ll.lasku
       LEFT JOIN liite liite on ll.liite = liite.id
       JOIN aliurakoitsija a on lk.suorittaja = a.id
WHERE l.urakka = :urakka
  AND lk.suoritus_alku BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
  AND l.poistettu IS NOT TRUE;


-- name: hae-lasku
SELECT l.id                   as "laskun-id",
       l.viite                as "viite",
       l.kokonaissumma        as "kokonaissumma",
       l.erapaiva             as "erapaiva",
       lk.id                  as "kohdistus-id",
       lk.summa               as "summa",
       lk.toimenpideinstanssi as "toimenpideinstanssi",
       lk.tehtavaryhma        as "tehtavaryhma",
       lk.tehtava             as "tehtava",
       lk.suorittaja          as "suorittaja-id",
       a.nimi                 as "suorittaja-nimi",
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       l.tyyppi               as "tyyppi",
       liite.id               AS liite_id,
       liite.nimi             AS liite_nimi,
       liite.tyyppi           AS liite_tyyppi,
       liite.koko             AS liite_koko,
       liite.liite_oid        AS liite_oid
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
       LEFT JOIN lasku_liite ll on l.id = ll.lasku
       LEFT JOIN liite liite on ll.liite = liite.id
       JOIN aliurakoitsija a on lk.suorittaja = a.id
WHERE l.urakka = :urakka
  AND l.viite = :viite
  AND l.poistettu IS NOT TRUE;


-- name: luo-lasku<!
INSERT
INTO lasku (viite, erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja)
VALUES (:viite, :erapaiva, :kokonaissumma, :urakka, :tyyppi, current_timestamp, :kayttaja);

-- name: paivita-lasku!
UPDATE lasku
SET erapaiva       = :erapaiva,
    summa          = :summa,
    suorittaja     = :suorittaja,
    suoritus_alku  = :suoritus_alku,
    suoritus_loppu = :suoritus_loppu,
    tyyppi         = :tyyppi,
    muokattu       = current_timestamp,
    muokkaaja      = :kayttaja
WHERE urakka = :urakka
  AND viite = :viite;

-- name: poista-lasku!
UPDATE lasku
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE urakka = :urakka
  AND viite = :viite;

-- name: luo-laskun-kohdistus<!
INSERT
INTO lasku_kohdistus (lasku, summa, toimenpideinstanssi, tehtavaryhma, tehtava, suorittaja, suoritus_alku,
                      suoritus_loppu, luotu, luoja)
VALUES (:lasku, :summa, :toimenpideinstanssi, :tehtavaryhma, :tehtava, :suorittaja, :suoritus_alku, :suoritus_loppu,
        current_timestamp, :kayttaja);

-- name: paivita-laskun-kohdistus!
UPDATE lasku
SET summa               = :summa,
    toimenpideinstanssi = :toimenpideinstanssi,
    tehtavaryhma        = :tehtavaryhma,
    tehtava             = :tehtava,
    suorittaja          = :suorittaja,
    suoritus_alku       = :suoritus_alku,
    suoritus_loppu      = :suoritus_loppu,
    muokattu            = current_timestamp,
    muokkaaja           = :kayttaja
WHERE urakka = :urakka
  AND viite = :viite;


-- name: poista-laskun-kohdistus!
UPDATE lasku
SET poistettu = TRUE,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE urakka = :urakka
  AND id = :laskuerittelyn - id;