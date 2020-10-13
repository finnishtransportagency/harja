-- name: listaa-tehtavat-ja-toimenpiteet-kustannusten-seurantaan
-- Listaa kustannusten seurantaa varten tehtävien kustannukset ja budjetti toimenpideentietojen kanssa
SELECT
       tr1.nimi                    AS tehtava,
       tr1.otsikko                AS tehtavaryhma,
       100                        AS budjetoitu_summa,
       lk.summa                   AS toteutunut_summa
    FROM

         lasku_kohdistus lk,
         tehtavaryhma tr1,
        toimenpidekoodi t

    WHERE lk.tehtavaryhma = tr1.id
      AND ((:tehtavaryhma::TEXT IS NULL OR tr1.otsikko = :tehtavaryhma)
            OR (t.koodi = '23104'
            OR t.koodi = '23116'
            OR t.koodi = '23124'
            OR t.koodi = '20107'
            OR t.koodi = '20191'
            OR t.koodi = '14301'
            OR t.koodi = '23151'))
limit 20;

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