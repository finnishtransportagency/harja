-- Päivitetään _kaikki_ urakat elinvoimakeskuksiin. Päätellään tuleva elinvoimakeskus vanhan elyn perusteella,

-- Lapin elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Lappi' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Lappi' AND tyyppi = 'hallintayksikko');

-- Pohjois-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Pohjois-Suomi' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pohjois-Pohjanmaa');

-- Itä-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Itä-Suomi' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pohjois-Savo' AND tyyppi = 'hallintayksikko');

-- Keski-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Keski-Suomi' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Keski-Suomi' AND tyyppi = 'hallintayksikko');

-- Varsinais-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Lounais-Suomi' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Varsinais-Suomi' AND tyyppi = 'hallintayksikko');

-- Vanha ely on kokonaan jonkun uuden elinvoimakeskuksen alueella
-- eli KAS => Kaakkois-Suomi ja PIR => Sisä-Suomi
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Kaakkois-Suomi' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Kaakkois-Suomi' AND tyyppi = 'hallintayksikko');

-- Sisä-Suomen elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Sisä-Suomi' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pirkanmaa' AND tyyppi = 'hallintayksikko');

-- Etelä-Pohjanmaan elinvoimakeskus
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Etelä-Pohjanmaa' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Etelä-Pohjanmaa' AND tyyppi = 'hallintayksikko');

-- Uudenmaan elinvoimakeskuksen alueelle jäävät UUD-elyn urakat
UPDATE urakka
   SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Uusimaa' AND tyyppi = 'elinvoimakeskus')
 WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa' AND tyyppi = 'hallintayksikko');

-- Kanavat, sisävesi- ja meriväylät
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Kanavat ja avattavat sillat')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Kanavat ja avattavat sillat');

UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Sisävesiväylät')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Sisävesiväylät');

UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Meriväylät')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Meriväylät');

-- Varmistetaan, että Kokkolan urakka päätyy Pohjanmaan elinvoimakeskukseen, vaikka se tuotannossa kuuluikin Etelä-pohjanmaan elyyn
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Pohjanmaa' AND tyyppi = 'elinvoimakeskus')
WHERE nimi ilike '%Kokkola%' AND tyyppi in ('hoito','teiden-hoito');
