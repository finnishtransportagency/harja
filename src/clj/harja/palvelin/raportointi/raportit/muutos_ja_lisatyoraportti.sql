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
                 JOIN ONLY mhu_muutos_kulu mmk ON (k.id = mmk.kulu AND m.id = mmk.muutos AND m.versio = mmk.versio)
                 JOIN kulu_kohdistus kk ON k.id = kk.kulu AND kk.tyyppi = 'jjh-muutos'
        WHERE k.poistettu IS FALSE
          AND kk.poistettu IS FALSE
          AND k.erapaiva BETWEEN :alkupvm AND :loppupvm)                         AS "jjh-muutosten-summa",
       -- Muiden muutosten summa tulee kustannusvaikutuksista
       (SELECT SUM(kust.summa)
          FROM ONLY mhu_muutos_kustannusvaikutus kust
         WHERE kust.muutos = m.id
           AND kust.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi) AS "kustannusvaikutusten-summa"
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
       COALESCE(SUM(mmk.summa), 0) AS "kustannusvaikutusten-summa",
       COALESCE(indeksikorjaa(COALESCE(SUM(mmk.summa), 0), extract(YEAR from :alkupvm::DATE)::INTEGER, 10, :urakka-id::INTEGER), 0) AS "indeksikorjattu-summa"
FROM ONLY mhu_muutos m
         JOIN ONLY mhu_muutos_kustannusvaikutus mmk ON m.id = mmk.muutos
WHERE m.urakka = :urakka-id
  AND m.tyyppi = 'pysyva'::MHU_MUUTOSTYYPPI
  AND m.poistettu IS FALSE
  -- Astunut voimaan ennen valittua aikaväliä (= aikaisempien vuosien muutos)
  AND m.voimassa_alkaen < :alkupvm
  -- Kustannusvaikutuksen hoitokauden alkuvuosi vastaa valittua aikaväliä
  AND mmk.hoitokauden_alkuvuosi = EXTRACT(YEAR FROM :alkupvm::DATE)
GROUP BY m.id, m.syy, m.voimassa_alkaen
ORDER BY m.voimassa_alkaen;

-- name: hae-tavoitehinnan-oikaisut
-- Hakee urakan tavoitehinnan oikaisut valitulta hoitokaudelta raporttia varten.
SELECT toi.id
     , toi.otsikko
     , toi.selite AS syy
     , toi.summa  AS tavoitehinnan_muutos
FROM tavoitehinnan_oikaisu toi
WHERE toi."urakka-id" = :urakka-id
  AND toi."hoitokauden-alkuvuosi" = EXTRACT(YEAR FROM :alkupvm::DATE)
  AND toi.poistettu IS NOT TRUE
ORDER BY toi.id;

-- name: hae-lisatoiden-kulukohdistukset
-- Hakee urakan lisätöiden kulukohdistukset raporttia varten.
-- Rajataan vain lisätyö-tyyppiset kulukohdistukset.
SELECT kk.lisatyon_lisatieto      AS lisatieto
     , COALESCE(SUM(kk.summa), 0) AS summa
     , tp.nimi                    AS toimenpide
     , MIN(k.erapaiva)            AS ajankohta
 FROM kulu_kohdistus kk
      JOIN kulu k ON kk.kulu = k.id
      LEFT JOIN toimenpideinstanssi tpi ON tpi.id = kk.toimenpideinstanssi
      LEFT JOIN toimenpide tp ON tpi.toimenpide = tp.id
WHERE k.urakka = :urakka-id
  AND k.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND k.poistettu IS NOT TRUE
  AND kk.poistettu IS NOT TRUE
  AND kk.tyyppi = 'lisatyo'
GROUP BY kk.lisatyon_lisatieto, tp.nimi
ORDER BY MIN(k.erapaiva);

-- name: hae-muutostoiden-kulukohdistukset
-- Hakee urakan erillisrahoitettujen muutostöiden kulukohdistukset raporttia varten.
-- Kulut linkittyvät muutoksiin kulu_kohdistus.muutos -kentän kautta.
SELECT m.nimi                     AS muutostyon_nimi
     , m.syy                      AS muutostyon_syy
     , tp.nimi                    AS toimenpide
     , COALESCE(SUM(kk.summa), 0) AS summa
     , MIN(k.erapaiva)            AS ajankohta
  FROM kulu_kohdistus kk
       JOIN kulu k ON kk.kulu = k.id
       LEFT JOIN toimenpideinstanssi tpi ON tpi.id = kk.toimenpideinstanssi
       LEFT JOIN toimenpide tp ON tpi.toimenpide = tp.id
       JOIN ONLY mhu_muutos m ON kk.muutos = m.id
 WHERE k.urakka = :urakka-id
   AND k.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
   AND k.poistettu IS NOT TRUE
   AND kk.poistettu IS NOT TRUE
   AND kk.tyyppi = 'erillisrahoitettu-muutos'
   AND m.poistettu IS NOT TRUE
   AND m.tyyppi = 'muutostyo'::MHU_MUUTOSTYYPPI
   AND m.alityyppi = 'erillisrahoitus'::MHU_MUUTOS_ALITYYPPI
 GROUP BY m.id, m.nimi, m.syy, tp.nimi
 ORDER BY MIN(k.erapaiva);
