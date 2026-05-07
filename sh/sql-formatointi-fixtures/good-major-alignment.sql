SELECT id,
       nimi
FROM urakka u
WHERE u.poistettu IS NOT TRUE
ORDER BY nimi;