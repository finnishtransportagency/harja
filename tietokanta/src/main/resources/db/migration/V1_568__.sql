-- Kopioi nykyisille käynnissä oleville urakoille geometriat suoraan tauluun
UPDATE urakka
SET alue = (SELECT au.alue
            FROM alueurakka au
            WHERE au.alueurakkanro = urakkanro)
WHERE alue IS NULL AND tyyppi = 'hoito' :: URAKKATYYPPI;

-- Kopioi geometriat 28.6.2017 ennen päättyneiltä hoidon alueurakoille niitä vastaavilta uusilta urakoilta

-- Veteli alueurakka, 2009 -2014, P	> Veteli, alueurakka 2014- 2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1060' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1029';

-- Espoo alueurakka 2009- 2014, P	> Espoo HJU 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '130' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '119';

-- Hämeenlinna alueurakka 2006-2013, P	> Hämeenlinna alueurakka 2013-2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '128' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '424';

-- Lahti alueurakka 2007- 2012, P	> Lahti alueurakka 2012- 2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '125' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '411';

-- Joensuu alueurakka 1.10.2009- 30.9.2014, P	> Joensuu alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '834' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '822';

-- Kemi alueurakka 2009- 2016, P	> Kemi alueurakka 2016- 2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1432' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1421';

-- Ranua alueurakka 2011- 2016, P	> Ranua alueurakka 2016- 2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1433' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1423';

-- Ranua alueurakka 2006-2011, P	> Ranua alueurakka 2016- 2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1433' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1417';

-- Mikkeli alueurakka 1.10.2008- 30.9.2014, P	> Mikkeli alueurakka 2014- 2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '832' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '365';

-- Kittilä alueurakka 2008- 2013, P	> Kittilä alueurakka  2013-2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1427' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1419';

-- JYVÄSKYLÄ alueurakka 2005-2012, P	> JYVÄSKYLÄ alueurakka 2012-2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '925' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '921';

-- Raasepori alueurakka 2011- 2016, P	> Raasepori HJU 2016-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '137' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '124';

-- POP Puolangan alueurakka 2010-2015, P	> POP Pudasjärvi-TaivalkoskI alueurakka 2013-2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1240' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1233';

-- Rovaniemi alueurakka 2007- 2012, P	> Rovaniemi alueurakka 2012- 2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1426' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1418';

-- Kiuruvesi alueurakka 1.10.2007- 30.9.2012, P	> Kiuruvesi alueurakka 2012-2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '830' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '819';

-- Nummi alueurakka 2007-  2014, P	> Nummi alueurakka 2014-2021,  P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '132' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '113';

-- Sodankylä alueurakka 2011- 2015, P	> Sodankylä alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1429' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1424';

-- Sodankylä alueurakka 2004- 2011, P	> Sodankylä alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1429' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1414';

-- ÄÄNEKOSKI alueurakka 2007 - 2014, P	> Äänekoski ALUEURAKKA 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '927' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '922';

-- Pello alueurakka 2005- 2012, P	> Pello alueurakka 2012- 2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1425' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1405';

-- Hyvinkää alueurakka 2006-2013, P	> Hyvinkää alueurakka 2013 -2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '127' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '116';

-- POP Puolangan alueurakka 2010-2015, P	> POP Pyhäjärven alueurakka 2010-2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1234' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1233';

-- Kokkola alueurakka, 2007 - 2012, P	> Kokkola alueurakka 2012 - 2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1056' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1012';

-- Vaasan alueurakka, 2006-2013, P	> Vaasa, alueurakka 2013-2018 , P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1058' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1034';

-- Ivalo alueurakka 2013-2014, P	> Ivalo alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1431' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1428';

-- Ivalo alueurakka 2009-2014, P	> Ivalo alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1431' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1420';

-- Nurmes alueurakka 1.10.2006- 30.9.2013, P	> Nurmes alueurakka 2013-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '831' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '809';

-- Juva alueurakka 1.10.2008- 30.9.2015, P	> Juva alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '836' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '384';

-- Iisalmi alueurakka 1.10.2007 -30.9.2014, P	> Iisalmi alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '833' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '820';

-- POP Iin alueurakka 2011-2016, P	> POP Iin alueurakka 2016-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1248' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1235';

-- Seinäjoki alueurakka 2008-2014, P	> Seinäjoki, alueurakka 2014-2019 , P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1059' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1026';

-- Kemijärvi-Posio alueurakka 2010-2015, P	> Kemijärvi-Posio alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1430' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1422';

-- Kitee alueurakka 1.10.2005- 30.9.2012, P	> Kitee alueurakka 2012-  2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '829' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '803';

-- JÄMSÄ alueurakka 2006- 2013, P	> Jämsä ALUEURAKKA 2013-2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '926' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '912';

-- POP Raahe-Ylivieskan alueurakka 2009- 2016, P	> POP Raahe-Ylivieska alueurakka 2016-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1247' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1231';

-- POP Puolangan alueurakka 2010-2015, P	> POP Puolangan alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1246' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1233';

-- Mäntsälä alueurakka 2010- 2015, P	> Mäntsälä alueurakka 2015- 2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '135' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '122';

-- Pieksämäki alueurakka 2011-2013, P	> Pieksämäki alueurakka 2014-2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '838' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '826';

-- Pieksämäki alueurakka 2013-2014, P	> Pieksämäki alueurakka 2014-2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '838' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '837';

-- Ilomantsin alueurakka 1.10.2008- 30.9.2015, P	> Ilomantsi alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '835' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '821';

-- Kauhajoki alueurakka 06-13, P	> Kauhajoki, alueurakka 2013-2018 , P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1057' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1018';

-- KAS Alueurakka Lappeenranta 2009- 2016, P	> KAS Alueurakka Kouvola 2012-2019 , P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '385' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '349';

-- KAS Alueurakka Imatra 2009-2016, P	> KAS Alueurakka Kouvola 2012-2019 , P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '385' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '350';

-- KARSTULA alueurakka 2009 - 2016, P	> KARSTULA  alueurakka 2016 -2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '928' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '918';

-- 3H067040 Alueurakka Kotka 2006-2013, P	> KAS Kotkan Alueurakka 2013 -2020 , P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '386' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '322';

-- 2111529 Harjavalta 11-16, P	> Kunnossapidon alueurakka, Harjavalta 2016-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '234' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '224';

-- Orivesi Alueurakka 2011- 2016, P	> PIR Orivesi alueurakka 2016-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '455' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '449';

-- Oulun alueurakka 2005- 2012, P	> POP Oulun alueurakka 2012-2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1238' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1225';

-- Kuusamon alueurakka 2013-2014, P	> POP Kuusamon alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1244' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1242';

-- Kuusamon alueurakka 2009- 2014, P	> POP Kuusamon alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1244' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1232';

-- 2091506 Hoidon ja ylläpidon alueurakka Kankaanpää 2009- 2014, P	> Kunnossapidon alueurakka Kankaanpää 2014 - 2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '232' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '222';

-- Kuhmon alueurakka 2011- 2015, P	> POP Kuhmon alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1245' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1237';

-- Kuhmon alueurakka 2013 -2014, P	> POP Kuhmon alueurakka 2014-2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1245' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1243';

-- 2101515 Hoidon ja ylläpidon alueurakka Lieto 2010- 2015, P	> Kunnossapidon alueurakka Lieto 2015 -2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '233' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '223';

-- Kangasala Alueurakka 2011- 2016, P	> PIR alueurakka Kangasala 2016-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '454' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '448';

-- 2051275 MERIKARVIA 2005-2012, P	> Kunnossapidon alueurakka Merikarvia 2012-2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '225' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '214';

-- Suomussalmen alueurakka 2008- 2013, P	> POP Suomussalmen alueurakka 2013-2018,  P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1241' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1228';

-- Lapua, Alueurakka, 2008- 2015, P	> EPO Lapuan alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1061' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1025';

-- Alueurakka Virrat 2008 - 2015, P	> Virrat alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '453' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '447';

-- Alueurakka Parkano 2008 - 2015, P	> Parkano alueurakka 2015-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '452' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '446';

-- Alueurakka Tampere 2005 - 2012, P	> Tampere Alueurakka 2012-2017, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '450' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '436';

-- Vantaa alueurakka 2009-2014, P	> UUD Vantaan alueurakka 2014- 2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '131' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '120';

-- 3H057001 Alueurakka Kouvola 2005 -2012,  P	> KAS Alueurakka Kouvola 2012-2019 , P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '385' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '333';

-- Kuopio alueurakka 1.10.2009- 30.9.2016, P	> POS Kuopion alueurakka 2016 -2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '839' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '823';

-- Pietarsaari alueurakka 2009 -2016, P	> EPO Pietarsaaren alueurakka 2016-2021, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1062' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1015';

-- 2071288 PORI  2007 -2014, P	> Kunnossapidon alueurakka Pori 2014 - 2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '231' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '218';

-- 2071287 RAISIO  2007-2014, P	> Kunnossapidon alueurakka Raisio 2014- 2019, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '230' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '217';

-- Pudasjärvi-Taivalkoski alueurakka 2008-2013, P	> POP Pudasjärvi-TaivalkoskI alueurakka 2013-2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '1240' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '1229';

-- 2061281 HUITTINEN 2006-2013, P	> Kunnossapidon alueurakka Huittinen 2013 - 2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '227' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '210';

-- 2061282 PAIMIO 2006-2013, P	> Kunnossapidon alueurakka Paimio 2013- 2018, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '228' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '201';

-- Porvoo alueurakka 2010- 2015, P	> UUD Porvoon alueurakka 2015-2020, P
UPDATE urakka
SET alue = (SELECT alue
            FROM urakka
            WHERE urakkanro = '133' AND tyyppi = 'hoito' :: URAKKATYYPPI)
WHERE tyyppi = 'hoito' :: URAKKATYYPPI AND alue IS NULL AND urakkanro = '121';


-- Luo triggeri, joka päivittää urakan geometrian luomisen jälkeen
CREATE FUNCTION paivita_alueurakan_geometria()
  RETURNS TRIGGER AS $$
BEGIN
  UPDATE urakka
  SET alue = (SELECT alue
              FROM alueurakka
              WHERE alueurakkanro = NEW.urakkanro)
  WHERE alue IS NULL AND
        urakkanro = NEW.urakkanro AND
        tyyppi = 'hoito' :: URAKKATYYPPI;
  RETURN NEW;

END;
$$ LANGUAGE plpgsql;

-- Luo triggeri, joka päivittää urakoiden geometriat automaattisesti luonnin jälkeen
CREATE TRIGGER tg_paivita_alueurakan_geometriat_luonnin_jalkeen
AFTER INSERT
  ON urakka
FOR EACH ROW
WHEN (NEW.tyyppi = 'hoito' :: URAKKATYYPPI)
EXECUTE PROCEDURE paivita_alueurakan_geometria();
