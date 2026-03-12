-- Päivitetään _kaikki_ urakat elinvoimakeskuksiin. Päätellään tuleva elinvoimakeskus vanhan elyn perusteella,

-- Lapin elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Lapin elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Lappi');

-- Pohjois-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Pohjois-Suomen elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pohjois-Pohjanmaa');

-- Itä-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Itä-Suomen elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pohjois-Savo');

-- Keski-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Keski-Suomen elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Keski-Suomi');

-- Varsinais-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Lounais-Suomen elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Varsinais-Suomi');

-- Vanha ely on kokonaan jonkun uuden elinvoimakeskuksen alueella
-- eli KAS => Kaakkois-Suomi ja PIR => Sisä-Suomi
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Kaakkois-Suomen elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Kaakkois-Suomi');

-- Sisä-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Sisä-Suomen elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pirkanmaa');

-- Etelä-Pohjanmaan elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Etelä-Pohjanmaan elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Etelä-Pohjanmaa');

-- Uudenmaan elinvoimakeskuksen alueelle jäävät UUD-elyn urakat
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Uudenmaan elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa');
