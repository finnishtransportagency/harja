SELECT id,
       nimi
FROM urakka u
 LEFT JOIN organisaatio o ON u.hallintayksikko = o.id
  LEFT JOIN sopimus s ON u.id = s.urakka
WHERE u.poistettu IS NOT TRUE;