-- name: listaa-tehtavat-ja-toimenpiteet-kustannusten-seurantaan
-- Listaa kustannusten seurantaa varten tehtävien kustannukset ja budjetti toimenpideentietojen kanssa
SELECT tpi.id AS toimenpideinstanssi_id,
       tk.nimi AS toimenpidekoodi_nimi,
       kt.summa AS budjetoitu_summa,
       0 AS toteutunut_summa,
       tk.koodi,
       kt.tyyppi::TEXT AS maksutyyppi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END AS toimenpide
       -- kt.vuosi, kt.kuukausi, kt.summa, kt.tyyppi
    FROM toimenpidekoodi tk,
         toimenpideinstanssi tpi,
         kustannusarvioitu_tyo kt,
         sopimus s
    WHERE s.urakka = :urakka
      AND kt.sopimus = s.id
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
UNION
-- Toteutuneet kustannukset
SELECT tpi.id AS toimenpideinstanssi_id,
       tk.nimi AS toimenpidekoodi_nimi,
       0 AS budjetoitu_summa,
       lk.summa AS toteutunut_summa,
       tk.koodi,
       lk.maksueratyyppi::TEXT AS maksutyyppi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END AS toimenpide
       -- kt.vuosi, kt.kuukausi, kt.summa, kt.tyyppi
    FROM lasku_kohdistus lk,
         toimenpideinstanssi tpi,
         toimenpidekoodi tk,
         lasku l
    WHERE l.urakka = :urakka
      AND lk.lasku = l.id
      AND lk.toimenpideinstanssi = tpi.id
      AND tpi.toimenpide = tk.id
      AND (tk.koodi = '23104' OR tk.koodi = '23116'
        OR tk.koodi = '23124' OR tk.koodi = '20107' OR tk.koodi = '20191' OR
           tk.koodi = '14301' OR tk.koodi = '23151')
ORDER BY toimenpideinstanssi_id ASC
    LIMIT 200;

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