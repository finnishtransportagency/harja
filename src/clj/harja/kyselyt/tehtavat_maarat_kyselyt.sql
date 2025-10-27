-- name: hae-maaramitattavat-tehtavat
SELECT t.id, t.nimi, t.tehtavaryhma as tehtavaryhmaid, t.yksikko, t.suunnitteluyksikko
  FROM tehtava t
 WHERE t.tehtavaryhma IS NOT NULL
   AND t.yksikko IS NOT NULL
   AND t.poistettu IS NOT TRUE
   AND t.piilota IS NOT TRUE
   AND t."maaramitattava?" = TRUE
   AND t."mhu-tehtava?" = TRUE
 ORDER BY t.jarjestys;
