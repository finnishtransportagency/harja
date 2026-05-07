SELECT id,
       nimi
FROM urakka
WHERE poistettu IS NOT TRUE
 GROUP BY tyyppi
 ORDER BY tyyppi
 LIMIT 10;