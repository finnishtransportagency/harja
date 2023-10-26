-- Äkillisille hoitotöille ei voi käsin lisätä toteumia, joten otetaan niiltä kasin_lisattava_maara pois
UPDATE tehtava SET kasin_lisattava_maara = false
 WHERE yksiloiva_tunniste = '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974' -- Äkillinen hoitotyö (l.ymp.hoito)
    OR yksiloiva_tunniste = '1f12fe16-375e-49bf-9a95-4560326ce6cf' -- Äkillinen hoitotyö (talvihoito)
    OR yksiloiva_tunniste = 'd373c08b-32eb-4ac2-b817-04106b862fb1'; -- Äkillinen hoitotyö (soratiet)

-- Lisätään käsin lisättävä määrä tehtäville
UPDATE tehtava SET kasin_lisattava_maara = true
 WHERE nimi = 'Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt' AND kasin_lisattava_maara = false;
