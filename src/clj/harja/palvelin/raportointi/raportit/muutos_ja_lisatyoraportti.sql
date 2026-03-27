-- name: hae-kirjallisesti-sovitut-muutokset-raportille
-- Hakee urakan hoitovuoden kirjallisesti sovitut muutokset raporttia varten.
-- Palauttaa muutokset tyypeittäin: pysyva, muutostyo, johto-ja-hallintokorvaus
SELECT m.id,
       m.tyyppi,
       m.syy,
       m.voimassa_alkaen,
       -- Johto- ja hallintokorvausmuutosten summa tulee kuluista
       (SELECT SUM(k.kokonaissumma)
          FROM kulu k
               JOIN mhu_muutos_kulu mmk ON (k.id = mmk.kulu AND m.id = mmk.muutos AND m.versio = mmk.versio)
               JOIN kulu_kohdistus kk ON k.id = kk.kulu AND kk.tyyppi = 'jjh-muutos'
         WHERE k.poistettu IS FALSE
           AND kk.poistettu IS FALSE
           AND k.erapaiva BETWEEN :alkupvm AND :loppupvm) AS "jjh-muutosten-summa",
       -- Muiden muutosten summa tulee kustannusvaikutuksista
       (SELECT SUM(kust.summa)
          FROM mhu_muutos_kustannusvaikutus kust
         WHERE kust.muutos = m.id
           AND kust.hoitokauden_alkuvuosi = extract(YEAR FROM m.voimassa_alkaen)) AS "kustannusvaikutusten-summa"
  FROM ONLY mhu_muutos m
 WHERE m.urakka = :urakka-id
   AND m.tyyppi IN ('pysyva', 'muutostyo', 'johto-ja-hallintokorvaus')
   AND m.voimassa_alkaen BETWEEN :alkupvm AND :loppupvm
   AND m.poistettu IS FALSE
 ORDER BY m.tyyppi, m.voimassa_alkaen;




-- name: hae-aiempien-vuosien-pysyvat-muutokset-raportille
-- Hakee aikaisempien hoitovuosien pysyvät muutokset, joilla on kustannusvaikutuksia
-- valitulla aikavälillä. Ks. budjettisuunnittelu.sql rivi 116.
SELECT m.id,
       m.syy,
       m.voimassa_alkaen,
       COALESCE(SUM(mmk.summa), 0) AS "kustannusvaikutusten-summa"
  FROM ONLY mhu_muutos m
       JOIN mhu_muutos_kustannusvaikutus mmk ON m.id = mmk.muutos
 WHERE m.urakka = :urakka-id
   AND m.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI
   AND m.poistettu IS FALSE
   -- Astunut voimaan ennen valittua aikaväliä (= aikaisempien vuosien muutos)
   AND m.voimassa_alkaen < :alkupvm
   -- Kustannusvaikutuksen hoitokauden alkuvuosi vastaa valittua aikaväliä
   AND mmk.hoitokauden_alkuvuosi = EXTRACT(YEAR FROM :alkupvm::DATE)
 GROUP BY m.id, m.syy, m.voimassa_alkaen
 ORDER BY m.voimassa_alkaen;
