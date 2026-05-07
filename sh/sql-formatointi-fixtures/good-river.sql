SELECT id,
       nimi
  FROM urakka
 WHERE id = :id
   AND poistettu IS NOT TRUE
 ORDER BY nimi;