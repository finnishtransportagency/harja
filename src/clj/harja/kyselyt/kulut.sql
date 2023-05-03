-- name: hae-urakan-kulut
-- Hakee urakan kulut annetulta aikaväliltä
SELECT k.id            as "id",
       k.kokonaissumma as "kokonaissumma",
       k.erapaiva      as "erapaiva",
       k.tyyppi        as "tyyppi",
       k.luotu         as "luontipvm",
       k.muokattu      as "muokkauspvm",
       k.koontilaskun_kuukausi as "koontilaskun-kuukausi"
from kulu k
WHERE k.urakka = :urakka
  AND k.erapaiva BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
  AND k.poistettu IS NOT TRUE;

-- name: hae-urakan-johto-ja-hallintokorvaus-raporttiin-aikavalilla
select tr.nimi as "nimi",
       tr.id as "tehtavaryhma",
       tr.jarjestys as "jarjestys",
       jhk."toimenkuva-id",
       sum(jhk.tuntipalkka * jhk.tunnit) as "summa"
from johto_ja_hallintokorvaus jhk
  join tehtavaryhma tr on tr.yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54' -- johto- ja hallintokorvaus-tehtäväryhmän yksilöivä tunniste, näitä on muutamilla tehtäväryhmillä ja toimenpidekoodeilla, niin jos nimet muuttuu niin näillä ne löytyy luotettavasti
where jhk."urakka-id" = :urakka
  and format('%s-%s-%s', jhk.vuosi, jhk.kuukausi, 1)::DATE between :alkupvm::DATE and :loppupvm::DATE
group by jhk."toimenkuva-id", tr.nimi, tr.id, tr.jarjestys;

-- name: hae-urakan-hj-kulut-raporttiin-aikavalilla
select rs.nimi as "nimi",
       rs.jarjestys as "jarjestys",
       rs.tehtavaryhma as "tehtavaryhma",
       sum(rs.summa) as "summa"
from (select tr.nimi      as "nimi",
             tr.id        as "tehtavaryhma",
             tr.jarjestys as "jarjestys",
             kt.summa     as "summa"
      from kustannusarvioitu_tyo kt
             join toimenpideinstanssi tpi on kt.toimenpideinstanssi = tpi.id and tpi.urakka = :urakka
             join toimenpidekoodi tpk
             join tehtavaryhma tr on tr.id = tpk.tehtavaryhma
                  on tpk.id = kt.tehtava and tpk.nimi = 'Hoidonjohtopalkkio'
      where format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE between :alkupvm::DATE and :loppupvm::DATE
      union all
      select tr.nimi      as "nimi",
             tr.id        as "tehtavaryhma",
             tr.jarjestys as "jarjestys",
             kt.summa     as "summa"
      from kustannusarvioitu_tyo kt
             join toimenpideinstanssi tpi on kt.toimenpideinstanssi = tpi.id and tpi.urakka = :urakka
             join toimenpidekoodi tpk
             join tehtavaryhma tr
                  on tpk.tehtavaryhma = tr.id and tr.yksiloiva_tunniste in ('a6614475-1950-4a61-82c6-fda0fd19bb54') -- Tilaajan varaukset -tehtäväryhmän yksilöivä tunniste
                  on tpk.id = kt.tehtava
      where format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE between :alkupvm::DATE and :loppupvm::DATE
  union all
      select tr.nimi      as "nimi",
             tr.id        as "tehtavaryhma",
             tr.jarjestys as "jarjestys",
             kt.summa     as "summa"
      from kustannusarvioitu_tyo kt
             join toimenpideinstanssi tpi on kt.toimenpideinstanssi = tpi.id and tpi.urakka = :urakka
             join tehtavaryhma tr
                  on kt.tehtavaryhma = tr.id and tr.yksiloiva_tunniste in ('37d3752c-9951-47ad-a463-c1704cf22f4c') -- Erillishankinnat -tehtäväryhmän yksilöivä tunniste
      where format('%s-%s-%s', kt.vuosi, kt.kuukausi, 1)::DATE between :alkupvm::DATE and :loppupvm::DATE) rs
group by rs.nimi, rs.tehtavaryhma, rs.jarjestys
order by rs.jarjestys;


-- name: hae-urakan-kulut-raporttiin-aikavalilla
-- Annetulla aikavälillä haetaan urakan kaikki kulut tehtäväryhmittäin
with kohdistukset_ajalla as (
SELECT kk.summa, kk.tehtavaryhma
  FROM kulu_kohdistus kk
       JOIN kulu k ON kk.kulu = k.id
                      AND k.urakka = :urakka
                      AND k.erapaiva BETWEEN :alkupvm::DATE and :loppupvm::DATE
 WHERE k.id = kk.kulu
   AND kk.poistettu IS NOT TRUE)
select tr3.id as "tehtavaryhma",
       sum(kohd.summa) as "summa",
       tr3.jarjestys as "jarjestys",
       tr3.nimi as "nimi"
from tehtavaryhma tr3
         left join kohdistukset_ajalla kohd on tr3.id = kohd.tehtavaryhma
group by tr3.nimi, tr3.id, tr3.jarjestys
order by tr3.jarjestys;

-- name: hae-liitteet
-- Haetaan liitteet kululle
select liite.id               AS "liite-id",
       liite.nimi             AS "liite-nimi",
       liite.tyyppi           AS "liite-tyyppi",
       liite.koko             AS "liite-koko",
       liite.liite_oid        AS "liite-oid"
       from kulu k
       join kulu_liite kl on k.id = kl.kulu and kl.poistettu is not true
       join liite liite on kl.liite = liite.id
       where k.id = :kulu-id;

-- name: hae-kulut-kohdistuksineen-tietoineen-vientiin
-- Hakee PDF/Excel -generointiin tarvittavien tietojen kanssa urakan kulut
select
       tpi.nimi as "toimenpide",
       tr.nimi as "tehtavaryhma",
       kk.summa,
       me.numero as "maksuera",
       k.erapaiva,
       u.nimi as "urakka"
from kulu k
       join urakka u on k.urakka = u.id
       join kulu_kohdistus kk on k.id = kk.kulu
       join toimenpideinstanssi tpi on kk.toimenpideinstanssi = tpi.id
       join maksuera me on kk.toimenpideinstanssi = me.toimenpideinstanssi
       left join tehtavaryhma tr on kk.tehtavaryhma = tr.id
where k.urakka = :urakka
    and k.erapaiva between :alkupvm ::date and :loppupvm ::date
    and k.poistettu is not true;

-- name: hae-pvm-laskun-numerolla
-- Hakee laskun päivämäärän urakalle käyttäen laskun numeroa.  Yhtä laskun numeroa voi käyttää yhden pvm:n yhteydessä, mutta useampi lasku voi käyttää samaa numeroa samalla pvm:llä.
select
  k.erapaiva as "erapaiva"
from kulu k
where k.urakka = :urakka
  and k.laskun_numero = :laskun-numero
  and k.poistettu is not true;

-- name: hae-urakan-kulut-kohdistuksineen
-- Hakee urakan kulut ja niihin liittyvät kohdistukset annetulta aikaväliltä
SELECT k.id                   as "id",
       k.kokonaissumma        as "kokonaissumma",
       k.erapaiva             as "erapaiva",
       k.tyyppi               as "tyyppi",
       k.laskun_numero        as "laskun-numero",
       k.koontilaskun_kuukausi as "koontilaskun-kuukausi",
       k.lisatieto            as "lisatieto",
       kk.id                  as "kohdistus-id",
       kk.rivi                as "rivi",
       kk.summa               as "summa",
       kk.toimenpideinstanssi as "toimenpideinstanssi",
       kk.tehtavaryhma        as "tehtavaryhma",
       kk.tehtava             as "tehtava",
       kk.suoritus_alku       as "suoritus-alku",
       kk.suoritus_loppu      as "suoritus-loppu",
       kk.lisatyon_lisatieto  as "lisatyon-lisatieto",
       kk.maksueratyyppi      as "maksueratyyppi"
from kulu k
       JOIN kulu_kohdistus kk on k.id = kk.kulu AND kk.poistettu IS NOT TRUE
WHERE k.urakka = :urakka
    AND (:alkupvm::DATE IS NULL OR :alkupvm::DATE <= k.erapaiva)
    AND (:loppupvm::DATE IS NULL OR k.erapaiva <= :loppupvm::DATE)
    AND k.poistettu IS NOT TRUE;

-- name: linkita-kulu-ja-liite<!
-- Linkittää liitteen ja kulun
insert into kulu_liite (kulu, liite, luotu, luoja, poistettu)
values (:kulu-id, :liite-id, current_timestamp, :kayttaja, false)
on conflict do nothing;

-- name: poista-kulun-ja-liitteen-linkitys!
-- Merkkaa liitteen poistetuksi
update kulu_liite kl
set poistettu = true,
 muokkaaja = :kayttaja,
 muokattu = current_timestamp
 where kl.kulu = :kulu-id and kl.liite = :liite-id;

-- name: hae-kulu
SELECT k.id            as "id",
       k.urakka        as "urakka",
       k.kokonaissumma as "kokonaissumma",
       k.erapaiva      as "erapaiva",
       k.laskun_numero as "laskun-numero",
       k.tyyppi        as "tyyppi",
       k.koontilaskun_kuukausi as "koontilaskun-kuukausi",
       k.lisatieto     as "lisatieto"
FROM kulu k
where k.id = :id
  AND k.poistettu IS NOT TRUE;

-- name: hae-kulun-kohdistukset
SELECT kk.id                  as "kohdistus-id",
       kk.rivi                as "rivi",
       kk.summa               as "summa",
       kk.tehtava             as "tehtava",
       kk.tehtavaryhma        as "tehtavaryhma",
       kk.toimenpideinstanssi as "toimenpideinstanssi",
       kk.suoritus_alku       as "suoritus-alku",
       kk.suoritus_loppu      as "suoritus-loppu",
       kk.luotu               as "luontiaika",
       kk.muokattu            as "muokkausaika",
       kk.lisatyon_lisatieto  as "lisatyon-lisatieto",
       kk.maksueratyyppi      as "maksueratyyppi"
  FROM kulu_kohdistus kk
 WHERE kk.kulu = :kulu
   AND kk.poistettu IS NOT TRUE
 ORDER by kk.id;

-- name: luo-kulu<!
INSERT
  INTO kulu
       (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja, lisatieto,
        laskun_numero, koontilaskun_kuukausi)
VALUES (:erapaiva, :kokonaissumma, :urakka, :tyyppi ::LASKUTYYPPI,
        current_timestamp, :kayttaja, :lisatieto, :numero, :koontilaskun-kuukausi);

-- name: paivita-kulu<!
update
  kulu
      SET  erapaiva = :erapaiva,
           lisatieto = :lisatieto,
           laskun_numero = :numero,
           kokonaissumma = :kokonaissumma,
           tyyppi = :tyyppi ::LASKUTYYPPI,
           muokattu = current_timestamp,
           muokkaaja = :kayttaja,
           koontilaskun_kuukausi = :koontilaskun-kuukausi
          where id = :id;

-- name: luo-kulun-kohdistus<!
INSERT
INTO kulu_kohdistus (kulu, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, suoritus_alku,
                      suoritus_loppu, luotu, luoja, lisatyon_lisatieto)
VALUES (:kulu, :rivi, :summa, :toimenpideinstanssi, :tehtavaryhma, :maksueratyyppi ::MAKSUERATYYPPI, :alkupvm, :loppupvm,
        current_timestamp, :kayttaja, :lisatyon-lisatieto);

-- name: paivita-kulun-kohdistus<!
update kulu_kohdistus
set summa = :summa,
    toimenpideinstanssi = :toimenpideinstanssi,
    tehtavaryhma = :tehtavaryhma,
    maksueratyyppi = :maksueratyyppi ::MAKSUERATYYPPI,
    suoritus_alku = :alkupvm,
    suoritus_loppu = :loppupvm,
    muokattu = current_timestamp,
    muokkaaja = :kayttaja,
    lisatyon_lisatieto = :lisatyon-lisatieto
where id = :id;

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
SELECT nimi FROM toimenpidekoodi
WHERE id = :id AND poistettu IS NOT TRUE;

-- name: hae-tehtavaryhman-nimi
SELECT nimi FROM tehtavaryhma
WHERE id = :id AND poistettu IS NOT TRUE;
