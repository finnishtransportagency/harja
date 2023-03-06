-- Tehdään Päällysteiden paikkaus - kuumapäällyste:stä käsin lisättävä määrä
UPDATE toimenpidekoodi SET kasin_lisattava_maara = TRUE, ensisijainen = TRUE
                       WHERE nimi = 'Päällysteiden paikkaus - kuumapäällyste';
