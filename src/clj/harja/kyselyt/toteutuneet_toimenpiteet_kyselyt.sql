-- name: hae-vhu-toteutuneet-toimenpiteet
WITH urakan_tehtavien_kustannukset AS (
    SELECT kk.tehtava,
           SUM(kk.summa) AS toteutunut_kustannus
      FROM kulu k
           JOIN kulu_kohdistus kk ON kk.kulu = k.id
     WHERE k.urakka = :urakka
       AND k.erapaiva BETWEEN :alkupvm::DATE AND :loppupvm::DATE
       AND k.poistettu IS NOT TRUE
       AND kk.poistettu IS NOT TRUE
       AND kk.tehtava IS NOT NULL
     GROUP BY kk.tehtava
)
SELECT t.id AS tehtava_id,
       t.nimi AS tehtava_nimi,
       tro.id AS tehtavaryhmaotsikko_id,
       tro.otsikko AS tehtavaryhmaotsikko,
       tr.id AS tehtavaryhma_id,
       tr.nimi AS tehtavaryhma_nimi,
       t.yksikko,
       ut.laskettu_maara AS tavoitemaara,
       -- Määräsarake tulee toisesta branchista; tämä on valmistautumiseksi NULL.
       NULL::NUMERIC AS toteutunut_maara,
       COALESCE(k.toteutunut_kustannus, 0) AS toteutunut_kustannus,
       t.tavoitehintavaikutus_aina,
       t.tavoitehintavaikutus_ehdollinen,
       t.jarjestys
  FROM tehtavaryhmaotsikko tro
       JOIN tehtavaryhma tr ON tr.tehtavaryhmaotsikko_id = tro.id
       JOIN tehtava t ON t.tehtavaryhma = tr.id
       JOIN urakka u ON u.id = :urakka
       LEFT JOIN urakka_tehtavamaara_yhteenveto ut
              ON ut.urakka = u.id
             AND ut.hoitokauden_alkuvuosi = :hoitokauden-alkuvuosi
             AND ut.tehtava = t.id
       LEFT JOIN urakan_tehtavien_kustannukset k ON k.tehtava = t.id
 WHERE tro.urakkatyyppi = 'vesivayla-hoito'
   AND t."vhu-tehtava?" IS TRUE
   AND t.poistettu IS NOT TRUE
   AND (tr.voimassaolo_alkuvuosi IS NULL OR tr.voimassaolo_alkuvuosi <= DATE_PART('year', u.alkupvm)::INTEGER)
   AND (tr.voimassaolo_loppuvuosi IS NULL OR tr.voimassaolo_loppuvuosi >= DATE_PART('year', u.alkupvm)::INTEGER)
   AND (t.voimassaolo_alkuvuosi IS NULL OR t.voimassaolo_alkuvuosi <= DATE_PART('year', u.alkupvm)::INTEGER)
   AND (t.voimassaolo_loppuvuosi IS NULL OR t.voimassaolo_loppuvuosi >= DATE_PART('year', u.alkupvm)::INTEGER)
 ORDER BY tro.id, tr.jarjestys, tr.id, t.jarjestys;
