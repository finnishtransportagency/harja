-- Äkillisille hoitotöille ei voi käsin lisätä toteumia, joten otetaan niiltä kasin_lisattava_maara pois
UPDATE tehtava SET kasin_lisattava_maara = false
 WHERE yksiloiva_tunniste = '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974' -- Äkillinen hoitotyö (l.ymp.hoito)
    OR yksiloiva_tunniste = '1f12fe16-375e-49bf-9a95-4560326ce6cf' -- Äkillinen hoitotyö (talvihoito)
    OR yksiloiva_tunniste = 'd373c08b-32eb-4ac2-b817-04106b862fb1'; -- Äkillinen hoitotyö (soratiet)

-- Myöskään lisätöille ei saa käsin lisätä toteumia, joten otetaan niiltäkin kasin_lisattava_maara pois
UPDATE tehtava SET kasin_lisattava_maara = false
 WHERE yksiloiva_tunniste = 'e32341fc-775a-490a-8eab-c98b8849f968' -- Lisätyö (talvihoito)
    OR yksiloiva_tunniste = '0c466f20-620d-407d-87b0-3cbb41e8342e' -- Lisätyö (l.ymp.hoito)
    OR yksiloiva_tunniste =  'c058933e-58d3-414d-99d1-352929aa8cf9'; -- Lisätyö (sorateiden hoito)
