-- name: listaa-tehtavat-ja-toimenpiteet-kustannusten-seurantaan
-- Listaa kustannusten seurantaa varten tehtävien kustannukset ja budjetti toimenpideentietojen kanssa
-- Arvioidut kustannukset tallennetaan KUSTANNUSARVOITU_TYO-tauluun. Kustannusarvioitu työ toteutuu kolmella tavalla:
-- laskutettava-tyo on tehtävä- ja määräluettelossa listattua työtä. Sen kustannuksista tehdään vaihtelevia kuukausittaisia arvioita toimenpidetasolla.
-- Yksittäiseen tehtävään kuluvaa rahaa ei siis arvioida.
-- akillinen-hoitotyo on työtä, jonka kustannuksia ei voida tarkkaan arvioida, mutta johon tehdään rahavaraus. Summa on joka kuukaudelle sama.
-- vahinkojen-korjaukset on samaan tapaan työtä, jonka kustannuksia ei voida tarkkaan arvioida, mutta johon tehdään rahavaraus. Summa on joka kuukaudelle sama.
SELECT tpi.id                                    AS toimenpideinstanssi_id,
       tk.nimi                                   AS toimenpidekoodi_nimi,
       kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       tk.koodi,
       kt.tyyppi::TEXT                           AS maksutyyppi,
       tk_tehtava.nimi                           AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END                                   AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'kustannusarvioitu_tyo'                 AS tehtavaryhma
FROM toimenpidekoodi tk,
         kustannusarvioitu_tyo kt
            LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = kt.tehtava,
         toimenpideinstanssi tpi,
         sopimus s
    WHERE s.urakka = :urakka
      AND kt.sopimus = s.id
      AND (concat(kt.vuosi,'-',kt.kuukausi,'-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
      AND kt.toimenpideinstanssi = tpi.id
      AND tpi.toimenpide = tk.id
      AND (tk.koodi = '23104' -- talvihoito
        OR tk.koodi = '23116' -- liikenneympariston-hoito
        OR tk.koodi = '23124' -- sorateiden-hoito
        OR tk.koodi = '20107' -- paallystepaikkaukset
        OR tk.koodi = '20191' -- mhu-yllapito
        OR tk.koodi = '14301' -- mhu-korvausinvestointi
        OR tk.koodi = '23151' -- mhu-johto
        )
UNION ALL
-- kiinteahintainen_tyo taulusta haetaan osa suunnitelluista kustannuksista eli hankinnat
-- Kiinteät suunnitellut kustannukset tallennetaan KIINTEAHINTAINEN_TYO-tauluun. Hinta on kiinteä, kun se on sopimuksessa sovittu, yleensä kuukausille jaettava könttäsumma.
SELECT tpi.id                                    AS toimenpideinstanssi_id,
       tk.nimi                                   AS toimenpidekoodi_nimi,
       kt.summa                                  AS budjetoitu_summa,
       0                                         AS toteutunut_summa,
       tk.koodi,
       'hankinta'                                AS maksutyyppi,
       tk_tehtava.nimi                           AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END                                   AS toimenpide,
       kt.luotu                                  AS luotu,
       concat(kt.vuosi, '-', kt.kuukausi, '-01') AS ajankohta,
       'kiinteahintainen_tyo'                 AS tehtavaryhma
FROM toimenpidekoodi tk,
     kiinteahintainen_tyo kt
         LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = kt.tehtava,
     toimenpideinstanssi tpi
WHERE tpi.urakka = :urakka
  AND kt.toimenpideinstanssi = tpi.id
  AND (concat(kt.vuosi,'-',kt.kuukausi,'-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  AND tpi.toimenpide = tk.id
  AND (tk.koodi = '23104' -- talvihoito
    OR tk.koodi = '23116' -- liikenneympariston-hoito
    OR tk.koodi = '23124' -- sorateiden-hoito
    OR tk.koodi = '20107' -- paallystepaikkaukset
    OR tk.koodi = '20191' -- mhu-yllapito
    OR tk.koodi = '14301' -- mhu-korvausinvestointi
    OR tk.koodi = '23151' -- mhu-johto
    )
UNION ALL
-- Johto- ja hallintokorvaus haetaan johto_ja_hallintokorvaus taulusta
SELECT 0                                           AS toimenpideinstanssi_id,
       jjht.toimenkuva                             AS toimenpidekoodi_nimi,
       (hjh.tunnit * hjh.tuntipalkka)              AS budjetoitu_summa,
       0                                           AS toteutunut_summa,
       '0'                                           AS koodi,
       'kiinteahintainen'                          AS maksutyyppi,
       jjht.toimenkuva                             AS tehtava_nimi,
       'MHU Hoidonjohto'                           AS toimenpide,
       hjh.luotu                                   AS luotu,
       concat(hjh.vuosi, '-', hjh.kuukausi, '-01') AS ajankohta,
       'hjh'                 AS tehtavaryhma
FROM johto_ja_hallintokorvaus hjh
         LEFT JOIN johto_ja_hallintokorvaus_toimenkuva jjht on hjh."toimenkuva-id" = jjht.id
WHERE hjh."urakka-id" = :urakka
  AND (concat(hjh.vuosi, '-', hjh.kuukausi, '-01')::DATE BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
  -- '23151' vain hoidon johto koodilliset haetaan
UNION ALL
-- Toteutuneet kustannukset
SELECT tpi.id                  AS toimenpideinstanssi_id,
       tk.nimi                 AS toimenpidekoodi_nimi,
       0                       AS budjetoitu_summa,
       lk.summa                AS toteutunut_summa,
       tk.koodi,
       lk.maksueratyyppi::TEXT AS maksutyyppi,
       tk_tehtava.nimi         AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END                 AS toimenpide,
       lk.luotu                AS luotu,
       l.erapaiva::TEXT        AS ajankohta,
       tr.nimi                 AS tehtavaryhma
    FROM lasku_kohdistus lk
            LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava
            JOIN tehtavaryhma tr ON tr.id = lk.tehtavaryhma,
         toimenpideinstanssi tpi,
         toimenpidekoodi tk,
         lasku l
    WHERE l.urakka = :urakka
      AND l.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
      AND lk.lasku = l.id
      AND lk.toimenpideinstanssi = tpi.id
      AND tpi.toimenpide = tk.id
      AND (tk.koodi = '23104' OR tk.koodi = '23116'
        OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
           tk.koodi = '14301' OR tk.koodi = '23151')
ORDER BY toimenpideinstanssi_id ASC;

-- name: hae-kustannusten-seurannan-toimenpiteet
-- Toimenpiteet menee joka paikassa ristiin. Tässä tarkoitetaan kovakoodattua listaa,
-- joka poikkeaa mm. määrien toteumissa käytettävistä toimenpiteistä täysin.
SELECT id, koodi, nimi
  FROM toimenpidekoodi t
 WHERE (t.koodi = '23104'
    OR t.koodi = '23116'
    OR t.koodi = '23124'
    OR t.koodi = '20107'
    OR t.koodi = '20191'
    OR t.koodi = '14301'
    OR t.koodi = '23151');