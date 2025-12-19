-- Päivitä voimassa oleville urakoille elinvoimakeskustieto

-- Vanha ely ja uusi elinvoimakeskus vastaavat toisiaan
-- Eli LAP => Lappi, POP => Pohjois-Suomi, POS => Itä-Suomi, KES => Keski-Suomi
-- ja VAR => Lounais-Suomi
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Lapin elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Lappi')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Pohjois-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pohjois-Pohjanmaa')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Itä-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pohjois-Savo')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Keski-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Keski-Suomi')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Varsinais-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Lounais-Suomi')
  AND loppupvm > '2026-01-01';

-- Vanha ely on kokonaan jonkun uuden elinvoimakeskuksen alueella
-- eli KAS => Kaakkois-Suomi ja PIR => Sisä-Suomi
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Kaakkois-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Kaakkois-Suomi')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Sisä-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Pirkanmaa')
  AND loppupvm > '2026-01-01';

-- Vanhat elyn urakat jakautuvat selkeästi  kahden uuden elinvoimakeskuksen kesken,
-- mutta Etelä-Pohjanmaan elinvoimakeskus vastaa teiden hoidosta myös Pohjanmaan
-- elinvoimakerkuksen puolella.
-- eli EPO => Etelä-Pohjanmaa
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Etelä-Pohjanmaan elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Etelä-Pohjanmaa')
  AND lyhyt_nimi IN ('Kristiinankaupunki 22',
                     'Vaasa 23',
                     'Kokkola 24',
                     'Pietarsaari 21',
                     'Veteli 21')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Etelä-Pohjanmaan elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Etelä-Pohjanmaa')
  AND lyhyt_nimi IN ('Seinäjoki 24',
                     'Kauhajoki 23',
                     'Alavus 22',
                     'Lapua 25')
  AND loppupvm > '2026-01-01';

-- Uudenmaan elinvoimakeskuksen alueelle jäävät UUD-elyn urakat
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Uudenmaan elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa')
  AND lyhyt_nimi IN ('Raasepori 21',
                     'Espoo 24',
                     'Vantaa 24',
                     'Porvoo 25',
                     'Nummi 21',
                     'Mäntsälä 25')
  AND loppupvm > '2026-01-01';

-- UUD-elystä pois siirtyvät urakat
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Kaakkois-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa')
  AND lyhyt_nimi IN ('Heinola 22',
                     'Lahti 22')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Sisä-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa')
  AND lyhyt_nimi IN ('Hyvinkää 23',
                     'Hämeenlinna 23')
  AND loppupvm > '2026-01-01';
