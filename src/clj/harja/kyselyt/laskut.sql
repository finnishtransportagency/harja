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

-- name: hae-urakan-kulut-raporttiin-aikavalilla
-- Annetulla aikavälillä haetaan urakan kaikki kulut tehtäväryhmittäin
with kohdistukset_ajalla as (select summa, tehtavaryhma from lasku_kohdistus lk
                                join lasku l on lk.lasku = l.id and l.urakka = :urakka and l.erapaiva >= :alkupvm ::date and l.erapaiva <= :loppupvm ::date
                             where l.id = lk.lasku and lk.poistettu is not true)
select tr3.id as "tehtavaryhma",
       sum(kohd.summa) as "summa",
       tr3.jarjestys as "jarjestys",
       tr3.nimi as "nimi"
from tehtavaryhma tr1
       join tehtavaryhma tr2 on tr1.id = tr2.emo
       join tehtavaryhma tr3 on tr2.id = tr3.emo
       left join kohdistukset_ajalla kohd on tr3.id = kohd.tehtavaryhma
where tr1.emo is null
group by tr3.nimi, tr3.id, tr3.jarjestys
order by tr3.jarjestys;

--name: hae-hallintayksikon-kulut-raporttiin-aikavalilla
-- Annetulla aikavälillä haetaan hallintayksikon kaikki kulut tehtäväryhmittäin
select tr.id as "tehtavaryhma",
       sum(lk.summa) as "summa",
       tr.jarjestys as "jarjestys",
       tr.nimi as "nimi"
from lasku_kohdistus lk
       join tehtavaryhma tr on lk.tehtavaryhma = tr.id
  join lasku l on l.id = lk.lasku
  join urakka u on u.id = l.urakka
where l.erapaiva <= :loppupvm ::date
  and l.erapaiva >= :alkupvm ::date
  and u.hallintayksikko = :hallintayksikko
  and lk.poistettu is not true
group by tr.nimi, tr.id, tr.jarjestys
order by tr.jarjestys;

-- name: hae-koko-maan-kulut-raporttiin-aikavalilla-hallintayksikoittain
-- Annetulla aikavälillä haetaan kaikki kulut tehtäväryhmittäin ryhmiteltynä koko maasta
select tr.id as "tehtavaryhma",
       o.id as "hallintayksikko",
       sum(lk.summa) as "summa",
       tr.jarjestys as "jarjestys",
       tr.nimi as "nimi"
from lasku_kohdistus lk
       join tehtavaryhma tr on lk.tehtavaryhma = tr.id
  join lasku l on l.id = lk.lasku
  join urakka u on u.id = l.urakka
join organisaatio o on o.id = u.hallintayksikko
where l.erapaiva <= :loppupvm ::date
  and l.erapaiva >= :alkupvm ::date
  and lk.poistettu is not true
group by tr.nimi, o.id, tr.id, tr.jarjestys
order by tr.jarjestys;;

-- name: hae-koko-maan-kulut-raporttiin-aikavalilla
-- Annetulla aikavälillä haetaan kaikki kulut tehtäväryhmittäin ryhmiteltynä koko maasta
select tr.id as "tehtavaryhma",
       sum(lk.summa) as "summa",
       tr.jarjestys as "jarjestys",
       tr.nimi as "nimi"
from lasku_kohdistus lk
join tehtavaryhma tr on lk.tehtavaryhma = tr.id
where lk.erapaiva <= :loppupvm ::date
  and lk.erapaiva >= :alkupvm ::date
  and lk.poistettu is not true
group by tr.nimi, tr.id, tr.jarjestys
order by tr.jarjestys;;

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

-- name: hae-laskuerittelyt-tietoineen-vientiin
-- Hakee PDF/Excel -generointiin tarvittavien tietojen kanssa urakan kulut
select
       tpi.nimi as "toimenpide",
       tr.nimi as "tehtavaryhma",
       lk.summa,
       me.numero as "maksuera",
       l.erapaiva,
       u.nimi as "urakka"
from lasku l
       join urakka u on l.urakka = u.id
       join lasku_kohdistus lk on l.id = lk.lasku
       join toimenpideinstanssi tpi on lk.toimenpideinstanssi = tpi.id
       join maksuera me on lk.toimenpideinstanssi = me.toimenpideinstanssi
       left join tehtavaryhma tr on lk.tehtavaryhma = tr.id
where l.urakka = :urakka
    and l.erapaiva between :alkupvm ::date and :loppupvm ::date
    and l.poistettu is not true;

-- name: hae-pvm-laskun-numerolla
-- Hakee laskun päivämäärän urakalle käyttäen laskun numeroa.  Yhtä laskun numeroa voi käyttää yhden pvm:n yhteydessä, mutta useampi lasku voi käyttää samaa numeroa samalla pvm:llä.
select
  l.erapaiva as "erapaiva"
from lasku l
where l.urakka = :urakka
  and l.laskun_numero = :laskun-numero
  and l.poistettu is not true;

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
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       lk.lisatyon_lisatieto  as "lisatyon-lisatieto",
       lk.maksueratyyppi      as "maksueratyyppi"
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
WHERE l.urakka = :urakka
  AND l.poistettu IS NOT TRUE;

-- name: hae-urakan-laskuerittelyt
-- Hakee urakan laskut ja niihin liittyvät kohdistukset annetulta aikaväliltä
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
       lk.suoritus_alku       as "suoritus-alku",
       lk.suoritus_loppu      as "suoritus-loppu",
       lk.lisatyon_lisatieto  as "lisatyon-lisatieto",
       lk.maksueratyyppi      as "maksueratyyppi"
from lasku l
       JOIN lasku_kohdistus lk on l.id = lk.lasku AND lk.poistettu IS NOT TRUE
WHERE l.urakka = :urakka
  AND l.erapaiva BETWEEN :alkupvm ::DATE AND :loppupvm ::DATE
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

-- name: hae-lasku
SELECT l.id            as "id",
       l.urakka        as "urakka",
       l.kokonaissumma as "kokonaissumma",
       l.erapaiva      as "erapaiva",
       l.laskun_numero as "laskun-numero",
       l.tyyppi        as "tyyppi",
       l.koontilaskun_kuukausi as "koontilaskun-kuukausi",
       l.lisatieto     as "lisatieto"
FROM lasku l
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
       lk.lisatyon_lisatieto  as "lisatyon-lisatieto",
       lk.maksueratyyppi      as "maksueratyyppi"
  FROM lasku_kohdistus lk
 WHERE lk.lasku = :lasku
   AND lk.poistettu IS NOT TRUE
 ORDER by lk.id;

-- name: luo-lasku<!
INSERT
  INTO lasku
       (erapaiva, kokonaissumma, urakka, tyyppi, luotu, luoja, lisatieto,
        laskun_numero, koontilaskun_kuukausi)
VALUES (:erapaiva, :kokonaissumma, :urakka, :tyyppi ::LASKUTYYPPI,
        current_timestamp, :kayttaja, :lisatieto, :numero, :koontilaskun-kuukausi);

-- name: paivita-lasku<!
update
  lasku
      SET  erapaiva = :erapaiva,
           lisatieto = :lisatieto,
           laskun_numero = :numero,
           kokonaissumma = :kokonaissumma,
           tyyppi = :tyyppi ::LASKUTYYPPI,
           muokattu = current_timestamp,
           muokkaaja = :kayttaja,
           koontilaskun_kuukausi = :koontilaskun-kuukausi
          where id = :id;

-- name: luo-laskun-kohdistus<!
INSERT
INTO lasku_kohdistus (lasku, rivi, summa, toimenpideinstanssi, tehtavaryhma, maksueratyyppi, suoritus_alku,
                      suoritus_loppu, luotu, luoja, lisatyon_lisatieto)
VALUES (:lasku, :rivi, :summa, :toimenpideinstanssi, :tehtavaryhma, :maksueratyyppi ::MAKSUERATYYPPI, :alkupvm, :loppupvm,
        current_timestamp, :kayttaja, :lisatyon-lisatieto);

-- name: paivita-laskun-kohdistus<!
update lasku_kohdistus
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
