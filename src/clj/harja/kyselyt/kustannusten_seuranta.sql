-- name: listaa-tehtavat-ja-toimenpiteet-kustannusten-seurantaan
-- Listaa kustannusten seurantaa varten tehtävien kustannukset ja budjetti toimenpideentietojen kanssa
SELECT tpi.id AS toimenpideinstanssi_id,
       tk.nimi AS toimenpidekoodi_nimi,
       kt.summa AS budjetoitu_summa,
       0 AS toteutunut_summa,
       tk.koodi,
       kt.tyyppi::TEXT AS maksutyyppi,
       tk_tehtava.nimi AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
       END AS toimenpide,
       kt.luotu AS luotu,
       concat(kt.vuosi,'-',kt.kuukausi,'-01') AS ajankohta
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
-- Toteutuneet kustannukset
SELECT tpi.id AS toimenpideinstanssi_id,
       tk.nimi AS toimenpidekoodi_nimi,
       0 AS budjetoitu_summa,
       lk.summa AS toteutunut_summa,
       tk.koodi,
       lk.maksueratyyppi::TEXT AS maksutyyppi,
       tk_tehtava.nimi AS tehtava_nimi,
       CASE
           WHEN tk.koodi = '23104' THEN 'Talvihoito'
           WHEN tk.koodi = '23116' THEN 'Liikenneympäristön hoito'
           WHEN tk.koodi = '23124' THEN 'Sorateiden hoito'
           WHEN tk.koodi = '20107' THEN 'Päällystepaikkaukset'
           WHEN tk.koodi = '20191' THEN 'MHU Ylläpito'
           WHEN tk.koodi = '14301' THEN 'MHU Korvausinvestointi'
           WHEN tk.koodi = '23151' THEN 'MHU Hoidonjohto'
           END AS toimenpide,
           lk.luotu AS luotu,
           l.erapaiva::TEXT AS ajankohta
    FROM lasku_kohdistus lk
            LEFT JOIN toimenpidekoodi tk_tehtava ON tk_tehtava.id = lk.tehtava,
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