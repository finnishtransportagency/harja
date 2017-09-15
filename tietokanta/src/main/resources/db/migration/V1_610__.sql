-- 2051274 PÖYTYÄ 2005 - 2012, P"	> Varsinais-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP921')
WHERE sampoid = 'THPP-2-1274' AND hallintayksikko IS NULL;

-- Vaasan alueurakka, 2006-2013, P"	> Etelä-Pohjanmaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP971')
WHERE sampoid = 'THPP-10-1346' AND hallintayksikko IS NULL;

-- JÄMSÄ alueurakka 2006- 2013, P"	> Keski-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP961')
WHERE sampoid = 'THPP-9-689' AND hallintayksikko IS NULL;

-- Ranua alueurakka 2006-2011, P"	> Varsinais-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP921')
WHERE sampoid = 'THPP-14-888' AND hallintayksikko IS NULL;

-- Sodankylä alueurakka 2004- 2011, P"	> Lappi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP991')
WHERE sampoid = 'THPP-14-684' AND hallintayksikko IS NULL;

-- Hyvinkää alueurakka 2006-2013, P"	> Uusimaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP911')
WHERE sampoid = 'THPP-1-2222' AND hallintayksikko IS NULL;

-- 2061282 PAIMIO 2006-2013, P"	> Varsinais-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP921')
WHERE sampoid = 'THPP-2-1282' AND hallintayksikko IS NULL;

-- Alueurakka Vammala 2005 - 2012, P"	> Pirkanmaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP931')
WHERE sampoid = 'THPP-4-1544' AND hallintayksikko IS NULL;

-- Pulkkilan alueurakka 2008- 2012, P"	> Pohjois-Pohjanmaa ja Kainuu
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP981')
WHERE sampoid = 'THPP-12-2680' AND hallintayksikko IS NULL;

-- Pello alueurakka 2005- 2012, P"	> Lappi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP991')
WHERE sampoid = 'THPP-14-801' AND hallintayksikko IS NULL;

-- Kokkola alueurakka, 2007 - 2012, P"	> Etelä-Pohjanmaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP971')
WHERE sampoid = 'THPP-10-1412' AND hallintayksikko IS NULL;

-- Ivalo alueurakka 2009-2014, P"	> Lappi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP991')
WHERE sampoid = 'THPP-14-1177' AND hallintayksikko IS NULL;

-- Oulun alueurakka 2005- 2012, P"	> Pohjois-Pohjanmaa ja Kainuu
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP981')
WHERE sampoid = 'THPP-12-1977' AND hallintayksikko IS NULL;

-- 2051275 MERIKARVIA 2005-2012, P"	> Varsinais-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP921')
WHERE sampoid = 'THPP-2-1275' AND hallintayksikko IS NULL;

-- Alueurakka Tampere 2005 - 2012, P"	> Pirkanmaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP931')
WHERE sampoid = 'THPP-4-1543' AND hallintayksikko IS NULL;

-- 3H057001 Alueurakka Kouvola 2005 -2012,  P"	> Kaakkois-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP941')
WHERE sampoid = 'THPP-3-1689' AND hallintayksikko IS NULL;

-- 2061281 HUITTINEN 2006-2013, P"	> Varsinais-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP921')
WHERE sampoid = 'THPP-2-1281' AND hallintayksikko IS NULL;

-- VT 4 Lahti-Lusi palvelusopimus 2004- 2012, P"	> Uusimaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP911')
WHERE sampoid = 'THPP-1-2287' AND hallintayksikko IS NULL;

-- Hämeenlinna alueurakka 2006-2013, P"	> Uusimaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP911')
WHERE sampoid = 'THPP-1-2285' AND hallintayksikko IS NULL;

-- Suomussalmen alueurakka 2008- 2013, P"	> Pohjois-Pohjanmaa ja Kainuu
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP981')
WHERE sampoid = 'THPP-12-2653' AND hallintayksikko IS NULL;

-- Pudasjärvi-Taivalkoski alueurakka 2008-2013, P"	> Pohjois-Pohjanmaa ja Kainuu
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP981')
WHERE sampoid = 'THPP-12-2652' AND hallintayksikko IS NULL;

-- Lahti alueurakka 2007- 2012, P"	> Uusimaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP911')
WHERE sampoid = 'THPP-1-2283' AND hallintayksikko IS NULL;

-- Kittilä alueurakka 2008- 2013, P"	> Lappi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP991')
WHERE sampoid = 'THPP-14-1088' AND hallintayksikko IS NULL;

-- JYVÄSKYLÄ alueurakka 2005-2012, P"	> Keski-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP961')
WHERE sampoid = 'THPP-9-687' AND hallintayksikko IS NULL;

-- Rovaniemi alueurakka 2007- 2012, P"	> Lappi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP991')
WHERE sampoid = 'THPP-14-969' AND hallintayksikko IS NULL;

-- Kitee alueurakka 1.10.2005- 30.9.2012, P"	> Pohjois-Savo
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP951')
WHERE sampoid = 'THPP-8-2150' AND hallintayksikko IS NULL;

-- Kauhajoki alueurakka 06-13, P"	> Etelä-Pohjanmaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP971')
WHERE sampoid = 'THPP-10-1347' AND hallintayksikko IS NULL;

-- 3H067040 Alueurakka Kotka 2006-2013, P"	> Kaakkois-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP941')
WHERE sampoid = 'THPP-3-1784' AND hallintayksikko IS NULL;

-- 2071287 RAISIO  2007-2014, P"	> Varsinais-Suomi
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP921')
WHERE sampoid = 'THPP-2-1287' AND hallintayksikko IS NULL;

-- Kiuruvesi alueurakka 1.10.2007- 30.9.2012, P"	> Pohjois-Savo
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP951')
WHERE sampoid = 'THPP-8-2168' AND hallintayksikko IS NULL;

-- Kuusamon alueurakka 2009- 2014, P"	> Pohjois-Pohjanmaa ja Kainuu
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP981')
WHERE sampoid = 'THPP-12-2887' AND hallintayksikko IS NULL;

-- Vantaa alueurakka 2009-2014, P"	> Uusimaa
UPDATE urakka
SET hallintayksikko = (SELECT id FROM organisaatio WHERE tyyppi = 'hallintayksikko' AND sampo_ely_hash = 'KP911')
WHERE sampoid = 'THPP-1-2271' AND hallintayksikko IS NULL;
