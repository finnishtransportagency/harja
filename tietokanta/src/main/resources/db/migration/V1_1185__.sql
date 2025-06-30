-- Päivitetään toimenpide tehtäväryhmätauluun

UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') WHERE yksiloiva_tunniste = '6446eb02-5216-45a8-90aa-be60f3890aac' ; --  'A - Talvihoito', TALVIHOITO;
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') WHERE yksiloiva_tunniste = '3d5962b4-c7ca-4750-81f1-f589b9c7c52b' ; --  'B1 - Talvisuola';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') WHERE yksiloiva_tunniste = 'cbb5f9c5-7a06-4cad-bce1-dbcf067d2fa1' ; --  'B2 - KFo, NaFo';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') WHERE yksiloiva_tunniste = 'e76fee52-9fbb-4996-8184-c8e02be4b749' ; --  'B3 - Hiekoitus';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') WHERE yksiloiva_tunniste = 'dc151971-facc-48c4-90c9-e429987206e1' ; --  'C - Sorateiden hoito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') WHERE yksiloiva_tunniste = '0f5cd978-6304-414b-8082-bfbd7eb88c0e' ; --  'D - Kesäsuola, materiaali';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = 'c24c6fdd-1653-4d72-9c90-958811c50152' ; --  'E - ELY-rahoitteiset, liikenneympäristön hoito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') WHERE yksiloiva_tunniste = 'c8c65700-7178-4de0-b298-a715d6552840' ; --  'E - ELY-rahoitteiset, ylläpito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = 'ce9264f7-0860-4be0-a447-ac79822c3ca6' ; --  'F - Muut, liikenneympäristön hoito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') WHERE yksiloiva_tunniste = '4e3cf237-fdf5-4f58-b2ec-319787127b3e' ; --  'F - Muut, MHU ylläpito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151') WHERE yksiloiva_tunniste = '0ef0b97e-1390-4d6c-bbc4-b30536be8a68' ; --  'G - Hoidonjohtopalkkio';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = 'c8375a61-33d0-47e5-ae7b-1e07a4e79951' ; --  'H - Siltapäällysteet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '4720bc80-a2eb-45fc-a0b2-65c5fdf82a83' ; --  'I - Sillat ja laiturit';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151') WHERE yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54' ; --  'J - Johto- ja hallintokorvaus';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '0250dcc5-a13c-4efe-87ee-a7a1b8f65764' ; --  'K - Kuivatusjärjestelmät';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '87a3bd38-ae0a-4c74-ad0d-38a6d5d512ad' ; --  'L - Liikennemerkit ja liikenteenohjauslaitteet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') WHERE yksiloiva_tunniste = 'd77048cb-0fee-454d-9af0-e9656fcb7044' ; --  'M - Liikenteen varmistaminen kelirikkokohteessa';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') WHERE yksiloiva_tunniste = '07068b69-11bb-4d88-9309-f7a6369e387e' ; --  'M - Sorastus';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '1855032a-2bb3-46d4-b9b4-c6d4e4c25d05' ; --  'N - Nurmetukset ja muut vihertyöt';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = 'f51c3d67-d21f-4286-bbb5-9354dcd073d6' ; --  'O - Sorapientareet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '430b0c7e-64c1-42e3-99d2-35d17f9fceba' ; --  'P - Puhtaanapito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '14301') WHERE yksiloiva_tunniste = '9bfa48c6-a225-4d56-9275-8b08cf6302c4' ; --  'Q - RKR-korjaus';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') WHERE yksiloiva_tunniste = 'd6d8e712-4b08-4954-bea1-c772a37492da' ; --  'R - Rummut, päällystetiet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') WHERE yksiloiva_tunniste = 'e9f86f01-3e9d-4b00-95fa-9431461b78a7' ; --  'S - Rummut, soratiet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') WHERE yksiloiva_tunniste = '24103c8d-3a8a-4b6f-9315-570834d4479d' ; --  'T1 - Äkilliset hoitotyöt, Talvihoito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = 'c3cb9e68-7f08-4145-ad8f-f2985e8f1658' ; --  'T1 - Äkilliset hoitotyöt, Liikenneympäristön hoito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') WHERE yksiloiva_tunniste = '5a6760e8-6494-4db2-80bc-c06df391a5b6' ; --  'T1 - Äkilliset hoitotyöt, Soratiet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23104') WHERE yksiloiva_tunniste = '0623ae3c-b8b0-4791-96ea-4808029d43de' ; --  'T2 - Vahinkojen korjaukset, Talvihoito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '1b374802-dbe7-430b-bfc5-4635383d18e3' ; --  'T2 - Vahinkojen korjaukset, Liikenneympäristön hoito';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23124') WHERE yksiloiva_tunniste = 'df612065-20d5-47b9-8cca-51ffd250e1f8' ; --  'T2 - Vahinkojen korjaukset, Soratiet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') WHERE yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6' ; --  'T3 - Tilaajan rahavaraus';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116'), yksiloiva_tunniste = '9dd75da4-0fde-4db1-81dd-c5f5c6fd4d6f' WHERE nimi = 'T4'; -- Täydennetään toimenpidetiedon lisäksi yksilöivä tunniste jota ei ollut migraatioissa entuudestaan vaikka löytyy tuotantokannasta, T4
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '9c094519-e97e-410f-898c-09019b763d6d' ; --  'U - Kaiteet, aidat ja kivetykset';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23116') WHERE yksiloiva_tunniste = '405a8a12-70c0-4ef6-91f4-689197493239' ; --  'V - Vesakonraivaukset ja puun poisto';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151') WHERE yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c' ; --  'W - Erillishankinnat';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') WHERE yksiloiva_tunniste = 'e985931e-56d6-4244-bcc3-3da93652ca16' ; --  'X - Avo-ojitus, päällystetyt tiet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = 'b1cca2a5-6445-4f49-878d-a95f144cc190' ; --  'Y1 - Kuumapäällyste';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = '91c147a3-4469-40c5-9c8b-9aac05da52db' ; --  'Y2 - Kylmäpäällyste';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = '34aa4298-9430-4843-9256-baa743e24e50' ; --  'Y3 - KT-Valu';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = '97c44a18-866a-481f-9e01-2125330ae5ff' ; --  'Y4 - Käsipaikkaus pikapaikkausmassalla';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = '99f3d075-1f1a-40a8-985e-b0713732eb40' ; --  'Y5 - Puhallus-SIP';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = 'b3776574-78d2-4517-a2d3-6b71d06f51e9' ; --  'Y6 - Saumojen juottaminen bitumilla';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = '391367cc-ce47-4086-8f1b-e1db635958e9' ; --  'Y7 - Valu';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20107') WHERE yksiloiva_tunniste = 'd08f1502-7c3b-4ba3-b7eb-f2d399814ed8' ; --  'Y8 - Päällysteiden paikkaus, muut työt';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '20191') WHERE yksiloiva_tunniste = '82ecc58a-f96c-46f0-9c70-d29bb6cd4266' ; --  'Z - Avo-ojitus, soratiet';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151') WHERE yksiloiva_tunniste = '55c920e7-5656-4bb0-8437-1999add714a3' ; --  'Hoitovuoden päättäminen / Tavoitepalkkio';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151') WHERE yksiloiva_tunniste = '19907c24-dd26-460f-9cb4-2ed974b891aa' ; --  'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä';
UPDATE tehtavaryhma SET toimenpide = (SELECT id FROM toimenpide WHERE koodi = '23151') WHERE yksiloiva_tunniste = 'be34116b-2264-43e0-8ac8-3762b27a9557' ; --  'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä';

-- Tämä on dummy-tehtäväryhmä lisätyötoteumien kirjaamista varten, sille ei voi päivittää yksittäistä toimenpidettä.
UPDATE tehtavaryhma SET toimenpide = null WHERE yksiloiva_tunniste = 'c7d9be7c-7bea-49a4-bd30-a432041cf6dd' ; --  'Alataso Lisätyöt';

