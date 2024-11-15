-- Päivitetään kaikki tehtäväryhmän nimet.
UPDATE tehtavaryhma
   SET jarjestys          = 10,
       nimi               = 'A - Talvihoito',
       yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac'
 WHERE nimi = 'Talvihoito (A)';
UPDATE tehtavaryhma
   SET jarjestys          = 20,
       nimi               = 'B1 - Talvisuola',
       yksiloiva_tunniste = '3d5962b4-c7ca-4750-81f1-f589b9c7c52b'
 WHERE nimi = 'Talvisuola (B1)';
UPDATE tehtavaryhma
   SET jarjestys          = 21,
       nimi               = 'B2 - KFo, NaFo',
       yksiloiva_tunniste = 'cbb5f9c5-7a06-4cad-bce1-dbcf067d2fa1'
 WHERE nimi = 'KFo, NaFo (B2)';
UPDATE tehtavaryhma
   SET jarjestys          = 22,
       nimi               = 'B3 - Hiekoitus',
       yksiloiva_tunniste = 'e76fee52-9fbb-4996-8184-c8e02be4b749'
 WHERE nimi = 'Hiekoitus (B3)';
UPDATE tehtavaryhma
   SET jarjestys          = 30,
       nimi               = 'C - Sorateiden hoito',
       yksiloiva_tunniste = 'dc151971-facc-48c4-90c9-e429987206e1'
 WHERE nimi = 'Sorateiden hoito (C)';
UPDATE tehtavaryhma
   SET jarjestys          = 40,
       nimi               = 'D - Kesäsuola, materiaali',
       yksiloiva_tunniste = '0f5cd978-6304-414b-8082-bfbd7eb88c0e'
 WHERE nimi = 'Kesäsuola, materiaali (D)';
UPDATE tehtavaryhma
   SET jarjestys          = 50,
       nimi               = 'E - ELY-rahoitteiset, liikenneympäristön hoito',
       yksiloiva_tunniste = 'c24c6fdd-1653-4d72-9c90-958811c50152'
 WHERE nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)';
UPDATE tehtavaryhma
   SET jarjestys          = 51,
       nimi               = 'E - ELY-rahoitteiset, ylläpito',
       yksiloiva_tunniste = 'c8c65700-7178-4de0-b298-a715d6552840'
 WHERE nimi = 'ELY-rahoitteiset, ylläpito (E)';
UPDATE tehtavaryhma
   SET jarjestys = 60,
       nimi      = 'F - Muut, liikenneympäristön hoito'
 WHERE nimi = 'Muut, liikenneympäristön hoito (F)';
UPDATE tehtavaryhma
   SET jarjestys = 61,
       nimi      = 'F - Muut, MHU ylläpito'
 WHERE nimi = 'Muut, MHU ylläpito (F)';
UPDATE tehtavaryhma
   SET jarjestys = 70,
       nimi      = 'G - Hoidonjohtopalkkio'
 WHERE nimi = 'Hoidonjohtopalkkio (G)';
UPDATE tehtavaryhma
   SET jarjestys          = 80,
       nimi               = 'H - Siltapäällysteet',
       yksiloiva_tunniste = 'c8375a61-33d0-47e5-ae7b-1e07a4e79951'
 WHERE nimi = 'Siltapäällysteet (H)';
UPDATE tehtavaryhma
   SET jarjestys          = 90,
       nimi               = 'I - Sillat ja laiturit',
       yksiloiva_tunniste = '4720bc80-a2eb-45fc-a0b2-65c5fdf82a83'
 WHERE nimi = 'Sillat ja laiturit (I)';
UPDATE tehtavaryhma
   SET jarjestys = 100,
       nimi      = 'J - Johto- ja hallintokorvaus'
 WHERE nimi = 'Johto- ja hallintokorvaus (J)';
UPDATE tehtavaryhma
   SET jarjestys          = 110,
       nimi               = 'K - Kuivatusjärjestelmät',
       yksiloiva_tunniste = '0250dcc5-a13c-4efe-87ee-a7a1b8f65764'
 WHERE nimi = 'Kuivatusjärjestelmät (K)';
UPDATE tehtavaryhma
   SET jarjestys          = 120,
       nimi               = 'L - Liikennemerkit ja liikenteenohjauslaitteet',
       yksiloiva_tunniste = '87a3bd38-ae0a-4c74-ad0d-38a6d5d512ad'
 WHERE nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)';
UPDATE tehtavaryhma
   SET jarjestys          = 130,
       nimi               = 'M - Liikenteen varmistaminen kelirikkokohteessa',
       yksiloiva_tunniste = 'd77048cb-0fee-454d-9af0-e9656fcb7044'
 WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa (M)';
UPDATE tehtavaryhma
   SET jarjestys          = 131,
       nimi               = 'M - Sorastus',
       yksiloiva_tunniste = '07068b69-11bb-4d88-9309-f7a6369e387e'
 WHERE nimi = 'Sorastus (M)';
UPDATE tehtavaryhma
   SET jarjestys          = 140,
       nimi               = 'N - Nurmetukset ja muut vihertyöt',
       yksiloiva_tunniste = '1855032a-2bb3-46d4-b9b4-c6d4e4c25d05'
 WHERE nimi = 'Nurmetukset ja muut vihertyöt (N)';
UPDATE tehtavaryhma
   SET jarjestys          = 150,
       nimi               = 'O - Sorapientareet',
       yksiloiva_tunniste = 'f51c3d67-d21f-4286-bbb5-9354dcd073d6'
 WHERE nimi = 'Sorapientareet (O)';
UPDATE tehtavaryhma
   SET jarjestys          = 160,
       nimi               = 'P - Puhtaanapito',
       yksiloiva_tunniste = '430b0c7e-64c1-42e3-99d2-35d17f9fceba'
 WHERE nimi = 'Puhtaanapito (P)';
UPDATE tehtavaryhma
   SET jarjestys          = 170,
       nimi               = 'Q - RKR-korjaus',
       yksiloiva_tunniste = '9bfa48c6-a225-4d56-9275-8b08cf6302c4'
 WHERE nimi = 'RKR-korjaus (Q)';
UPDATE tehtavaryhma
   SET jarjestys          = 180,
       nimi               = 'R - Rummut, päällystetiet',
       yksiloiva_tunniste = 'd6d8e712-4b08-4954-bea1-c772a37492da'
 WHERE nimi = 'Rummut, päällystetiet (R)';
UPDATE tehtavaryhma
   SET jarjestys          = 190,
       nimi               = 'S - Rummut, soratiet',
       yksiloiva_tunniste = 'e9f86f01-3e9d-4b00-95fa-9431461b78a7'
 WHERE nimi = 'Rummut, soratiet (S)';
UPDATE tehtavaryhma
   SET jarjestys = 200,
       nimi      = 'T1 - Äkilliset hoitotyöt, Talvihoito'
 WHERE nimi = 'Äkilliset hoitotyöt, Talvihoito (T1)';
UPDATE tehtavaryhma
   SET jarjestys = 201,
       nimi      = 'T1 - Äkilliset hoitotyöt, Liikenneympäristön hoito'
 WHERE nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)';
UPDATE tehtavaryhma
   SET jarjestys = 202,
       nimi      = 'T1 - Äkilliset hoitotyöt, Soratiet'
 WHERE nimi = 'Äkilliset hoitotyöt, Soratiet (T1)';
UPDATE tehtavaryhma
   SET jarjestys = 203,
       nimi      = 'T2 - Vahinkojen korjaukset, Talvihoito'
 WHERE nimi = 'Vahinkojen korjaukset, Talvihoito (T2)';
UPDATE tehtavaryhma
   SET jarjestys = 204,
       nimi      = 'T2 - Vahinkojen korjaukset, Liikenneympäristön hoito'
 WHERE nimi = 'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)';
UPDATE tehtavaryhma
   SET jarjestys = 205,
       nimi      = 'T2 - Vahinkojen korjaukset, Soratiet'
 WHERE nimi = 'Vahinkojen korjaukset, Soratiet (T2)';
UPDATE tehtavaryhma
   SET jarjestys = 206,
       nimi      = 'T3 - Tilaajan rahavaraus'
 WHERE nimi = 'Tilaajan rahavaraus (T3)';
UPDATE tehtavaryhma
   SET jarjestys = 207,
       nimi      = 'T4 - Digitalisaatio ja innovaatiot'
 WHERE nimi = 'Digitalisaatio ja innovaatiot (T4)';
UPDATE tehtavaryhma
   SET jarjestys          = 210,
       nimi               = 'U - Kaiteet, aidat ja kivetykset',
       yksiloiva_tunniste = '9c094519-e97e-410f-898c-09019b763d6d'
 WHERE nimi = 'Kaiteet, aidat ja kivetykset (U)';
UPDATE tehtavaryhma
   SET jarjestys          = 220,
       nimi               = 'V - Vesakonraivaukset ja puun poisto',
       yksiloiva_tunniste = '405a8a12-70c0-4ef6-91f4-689197493239'
 WHERE nimi = 'Vesakonraivaukset ja puun poisto (V)';
UPDATE tehtavaryhma
   SET jarjestys = 230,
       nimi      = 'W - Erillishankinnat'
 WHERE nimi = 'Erillishankinnat (W)';
UPDATE tehtavaryhma
   SET jarjestys          = 240,
       nimi               = 'X - Avo-ojitus, päällystetyt tiet',
       yksiloiva_tunniste = 'e985931e-56d6-4244-bcc3-3da93652ca16'
 WHERE nimi = 'Avo-ojitus, päällystetyt tiet (X)';
UPDATE tehtavaryhma
   SET jarjestys          = 250,
       nimi               = 'Y1 - Kuumapäällyste',
       yksiloiva_tunniste = 'b1cca2a5-6445-4f49-878d-a95f144cc190'
 WHERE nimi = 'Kuumapäällyste (Y1)';
UPDATE tehtavaryhma
   SET jarjestys          = 251,
       nimi               = 'Y2 - Kylmäpäällyste',
       yksiloiva_tunniste = '91c147a3-4469-40c5-9c8b-9aac05da52db'
 WHERE nimi = 'Kylmäpäällyste (Y2)';
UPDATE tehtavaryhma
   SET jarjestys          = 252,
       nimi               = 'Y3 - KT-Valu',
       yksiloiva_tunniste = '34aa4298-9430-4843-9256-baa743e24e50'
 WHERE nimi = 'KT-Valu (Y3)';
UPDATE tehtavaryhma
   SET jarjestys          = 253,
       nimi               = 'Y4 - Käsipaikkaus pikapaikkausmassalla',
       yksiloiva_tunniste = '97c44a18-866a-481f-9e01-2125330ae5ff'
 WHERE nimi = 'Käsipaikkaus pikapaikkausmassalla (Y4)';
UPDATE tehtavaryhma
   SET jarjestys          = 254,
       nimi               = 'Y5 - Puhallus-SIP',
       yksiloiva_tunniste = '99f3d075-1f1a-40a8-985e-b0713732eb40'
 WHERE nimi = 'Puhallus-SIP (Y5)';
UPDATE tehtavaryhma
   SET jarjestys          = 255,
       nimi               = 'Y6 - Saumojen juottaminen bitumilla',
       yksiloiva_tunniste = 'b3776574-78d2-4517-a2d3-6b71d06f51e9'
 WHERE nimi = 'Saumojen juottaminen bitumilla (Y6)';
UPDATE tehtavaryhma
   SET jarjestys          = 256,
       nimi               = 'Y7 - Valu',
       yksiloiva_tunniste = '391367cc-ce47-4086-8f1b-e1db635958e9'
 WHERE nimi = 'Valu (Y7)';
UPDATE tehtavaryhma
   SET jarjestys          = 257,
       nimi               = 'Y8 - Päällysteiden paikkaus, muut työt',
       yksiloiva_tunniste = 'd08f1502-7c3b-4ba3-b7eb-f2d399814ed8'
 WHERE nimi = 'Päällysteiden paikkaus, muut työt (Y8)';
UPDATE tehtavaryhma
   SET jarjestys          = 260,
       nimi               = 'Z - Avo-ojitus, soratiet',
       yksiloiva_tunniste = '82ecc58a-f96c-46f0-9c70-d29bb6cd4266'
 WHERE nimi = 'Avo-ojitus, soratiet (Z)';

-- Urakoitsijoille tuntemattomia tehtäväryhmiä
UPDATE tehtavaryhma
   SET jarjestys = 300,
       nimi      = 'Hoitovuoden päättäminen / Tavoitepalkkio'
 WHERE nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio';
UPDATE tehtavaryhma
   SET jarjestys = 301,
       nimi      = 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä'
 WHERE nimi =
       'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä';
UPDATE tehtavaryhma
   SET jarjestys = 302,
       nimi      = 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä'
 WHERE nimi =
       'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä';
UPDATE tehtavaryhma
   SET jarjestys = 999,
       nimi      = 'Alataso Lisätyöt'
 WHERE nimi = 'Alataso Lisätyöt';
