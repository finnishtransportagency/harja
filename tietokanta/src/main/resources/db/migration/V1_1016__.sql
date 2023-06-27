-- Tässä migraatiossa lisätään tehtäväryhmät, sekä tehtäviä.
-- Tehtäväryhmiä linkitetaan tehtäviin, joten nämä on hyvä ajaa samassa tiedostossa.
-- On syystä ajaa toimenpiteet tasoilla 1-3 migraatiossa ennen tätä tiedostoa.

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'Talvihoito (A)', 	1, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'Talvisuola (B1)',		25, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.1 LIIKENNEYMPÄRISTÖN HOITO',	'Liikennemerkit ja liikenteenohjauslaitteet (L)',		35, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.2 LIIKENNEYMPÄRISTÖN HOITO',	'Puhtaanapito (P)',			48, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.3 LIIKENNEYMPÄRISTÖN HOITO',	'Vesakonraivaukset ja puun poisto (V)',		62, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.3 LIIKENNEYMPÄRISTÖN HOITO',	'Nurmetukset ja muut vihertyöt (N)',		65, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.4 LIIKENNEYMPÄRISTÖN HOITO',	'Kuivatusjärjestelmät (K)',		79, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.5 LIIKENNEYMPÄRISTÖN HOITO',	'Rummut, päällystetiet (R)', 	141, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.5 LIIKENNEYMPÄRISTÖN HOITO',	'Rummut, soratiet (S)',		142, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.6 LIIKENNEYMPÄRISTÖN HOITO',	'Kaiteet, aidat ja kivetykset (U)',		97, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO',	'Kuumapäällyste (Y1)',		102, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO',	'Siltapäällysteet (H)',		109, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.8 LIIKENNEYMPÄRISTÖN HOITO',	'Sorapientareet (O)', 	116, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.9 LIIKENNEYMPÄRISTÖN HOITO',	'Sillat ja laiturit (I)', 	121, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'Sorateiden hoito (C)',		128, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'Sorastus (M)',		129, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Avo-ojitus, soratiet (Z)',		124, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'RKR-korjaus (Q)',		146, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Avo-ojitus, päällystetyt tiet (X)', 	143, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Avo-ojitus, soratiet (Z)',		144, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Muut, MHU ylläpito (F)', 	152, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT DO NOTHING;

-- Nimetään vanha Muut (F) tehtäväryhmä "Muut, MHU ylläpito (F)":ksi ja käytetään sitä nimeä jatkossa.
UPDATE tehtavaryhma SET nimi = 'Muut, MHU ylläpito (F)' WHERE nimi = 'Muut (F)';
-- Mikäli 'Muut, MHU ylläpito (F)'-tehtäväryhmää ei ole olemassa ollenkaan tässä kohtaa (esim. testidb:ssä sitä ei ole), niin insertoidaan se.
INSERT INTO tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva)
VALUES ('5 KORJAAMINEN', 'Muut, MHU ylläpito (F)',
        152, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT INTO tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva)
VALUES ('5 KORJAAMINEN', 'Muut, liikenneympäristön hoito (F)',
        152, CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'ELY-rahoitteiset, liikenneympäristön hoito (E)', 	155, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'ELY-rahoitteiset, ylläpito (E)', 	157, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Johto- ja hallintokorvaus (J)', 	161, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Hoidonjohtopalkkio (G)', 	161, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Erillishankinnat (W)',		165, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Hoitovuoden päättäminen / Tavoitepalkkio',		168, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä',		169, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä',		170, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;

-- Uuden tehtävä- ja määräluetteloversion johdosta tarvitut tehtäväryhmät
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'KFo, NaFo (B2)',		26, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'Hiekoitus (B3)',		27, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'Kylmäpäällyste (Y2)', 	103, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 103;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'Puhallus-SIP (Y5)', 	106, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 106;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'Saumojen juottaminen bitumilla (Y6)', 	107, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 107;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'KT-Valu (Y3)', 	103, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 103;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'Käsipaikkaus pikapaikkausmassalla (Y4)', 	105, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 105;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'Valu (Y7)', 	108, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 108;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'Kesäsuola, materiaali (D)', 	129, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 129;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'Äkilliset hoitotyöt, Talvihoito (T1)',		135, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 135;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)', 	136, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 136;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'Äkilliset hoitotyöt, Soratiet (T1)', 	137, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 137;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'Vahinkojen korjaukset, Talvihoito (T2)',			138, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 138;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)', 	139, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 139;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'Vahinkojen korjaukset, Soratiet (T2)', 	140, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 140;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Tilaajan rahavaraus (T3)', 	159, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 159;
INSERT into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Digitalisaatio ja innovaatiot (T4)', 	1595, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
    ON CONFLICT (nimi) DO UPDATE SET jarjestys = 1595;



---- dummy
insert into tehtavaryhma (otsikko, nimi, jarjestys, luotu, luoja, nakyva) VALUES ('9 LISÄTYÖT', 'Alataso Lisätyöt',  992, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)  on conflict do nothing;
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Uudet tehtävät. Näille ei löydynyt vastaavuutta vanhoista tehtävistä. -----------------------------------------------------------------------------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
-- Aja toimenpidekoodit ja tehtäväryhmät ennen näitä inserttejä
-- Tehtävät on järjestetty (toimenpidekoodi.jarjestys) vuoden 2019 tehtävä- ja määräluettelon mukaan (sopimuksen liite). Siksi saman toimenpiteen alle kuuluvia tehtäviä on useassa osiossa.

-- Tässä luodaan ne toimenpidekoodit, jotka ovat tasolla 4 (aka. tehtävät) -
-- ne ovat Harjassa luotuja eikä niitä ole olemassa esim. Väylviraston Sampo-järjestelmässä.


-- Talvihoito --

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise 2-ajorat.', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'tiekm',	1, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 10;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise 1-ajorat.', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'tiekm',	2, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 20;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise ohituskaistat', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kaistakm',	3, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 30;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise rampit', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kaistakm',	4, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 40;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic 2-ajorat', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'tiekm',	13, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 130;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic 1-ajorat', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'tiekm',	14, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 140;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic ohituskaistat', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kaistakm',	15, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 150;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic rampit', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kaistakm',	16, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 160;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kävely- ja pyöräilyväylien laatukäytävät', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'tiekm',	19, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 190;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Levähdys- ja pysäköimisalueet', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kpl',	22, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 220;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muiden alueiden talvihoito', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'',	23, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 230;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Talvihoidon kohotettu laatu', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'tiekm',	24, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 240;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)', (select id from tehtavaryhma where nimi = 'KFo, NaFo (B2)'),	'tonni',	26, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KFo, NaFo (B2)'), jarjestys = 260;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ennalta arvaamattomien kuljetusten avustaminen', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'tonni',	28, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 280;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Pysäkkikatosten puhdistus', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kpl',	29, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 290;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kpl',	30, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 300;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Portaiden talvihoito', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kpl',	31, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 310;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Lisäkalustovalmius/-käyttö', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'kpl',	32, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'), jarjestys = 320;



-- Liikenneympäristön hoito --

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)', (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	'',	35, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'), jarjestys = 350;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoitotyöt', (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	NULL,	47, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'), jarjestys = 470;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Tie- ja levähdysalueiden kalusteiden kunnossapito ja hoito', (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	NULL,	49, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'), jarjestys = 490;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset', (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	'kpl',	50, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'), jarjestys = 500;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hiekoitushiekan ja irtoainesten poisto', (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'	),	NULL,	52, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'), jarjestys = 520;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut tie- levähdys- ja liitännäisalueiden puhtaanpitoon ja kalusteiden hoitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	'',	57, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'), jarjestys = 570;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Erillisten hoito-ohjeiden mukaiset vihertyöt', (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	NULL,	72, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'), jarjestys = 720;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Erillisten hoito-ohjeiden mukaiset vihertyöt, uudet alueet', (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	NULL,	73, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'), jarjestys = 730;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Vesistöpenkereiden hoito', (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	NULL,	75, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'), jarjestys = 750;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Tiekohtaiset maisemanhoitoprojektit', (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	NULL,	76, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'), jarjestys = 760;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sadevesi- ja salaojakaivojen sekä -putkistojen tyhjennys, puhdistus (huuhtelu) ja toiminnan varmistaminen', (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	NULL,	80, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), jarjestys = 800;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kaivojen ja putkistojen tarkastus', (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	NULL,	80, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), jarjestys = 805;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kaivojen ja putkistojen sulatus', (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	NULL,	81, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), jarjestys = 810;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu', (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	'kpl',	82, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), jarjestys = 820;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Rumpujen sulatus, aukaisu ja toiminnan varmistaminen', (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	'',	84, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), jarjestys = 840;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Rumpujen tarkastus', (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	NULL,	85, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), jarjestys = 850;

-- Uusi Tunnelit-tehtävä liikenneympäristön hoidolle
INSERT INTO toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen)
VALUES ('Tunnelit',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Muut, liikenneympäristön hoito (F)'), NULL, 1530, NULL,
        (SELECT id FROM toimenpidekoodi WHERE koodi = '23116'), CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (SELECT id
                                                           FROM tehtavaryhma
                                                          WHERE nimi = 'Muut, liikenneympäristön hoito (F)'),
                                         jarjestys    = 1530;



-- MHU Ylläpito --

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet', (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'),	'jm',	86, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'), jarjestys = 1371;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet', (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'),	'jm',	87, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'), jarjestys = 1372;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet', (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'),	'jm',	90, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'), jarjestys = 1373;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet', (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'),	'jm',	91, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'), jarjestys = 1374;

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' , (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'), 'jm', 88, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'), jarjestys = 1375;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm' , (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'), 'jm', 89, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, päällystetiet (R)'), jarjestys = 1376;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm' , (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'), 'jm', 92, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'), jarjestys = 1377;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm' , (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'), 'jm', 93, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Rummut, soratiet (S)'), jarjestys = 1378;
-- Liikenneympäristön hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut rumpujen kunnossapitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	NULL,	96, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'), jarjestys = 855;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset', (select id from tehtavaryhma where nimi = 'Kaiteet, aidat ja kivetykset (U)'	),	'',	97, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma =  (select id from tehtavaryhma where nimi = 'Kaiteet, aidat ja kivetykset (U)'	), jarjestys = 970;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Reunakivivaurioiden korjaukset', (select id from tehtavaryhma where nimi = 'Kaiteet, aidat ja kivetykset (U)'),	'',	98, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma =  (select id from tehtavaryhma where nimi = 'Kaiteet, aidat ja kivetykset (U)'	), jarjestys = 980;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'Kaiteet, aidat ja kivetykset (U)'),	NULL,	101, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma =  (select id from tehtavaryhma where nimi = 'Kaiteet, aidat ja kivetykset (U)'	), jarjestys = 1010;
-- Päällysteiden paikkaus

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut päällystettyjen teiden sorapientareiden kunnossapitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'),	NULL,	120, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'), jarjestys = 1200;
-- Liikenneympäristön hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)', (select id from tehtavaryhma where nimi = 'Sillat ja laiturit (I)'),	'kpl',	121, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sillat ja laiturit (I)'), jarjestys = 1210;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)', (select id from tehtavaryhma where nimi = 'Sillat ja laiturit (I)'),	'kpl',	122, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sillat ja laiturit (I)'), jarjestys = 1220;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut siltojen ja laitureiden hoitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'Sillat ja laiturit (I)'),	NULL,	123, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sillat ja laiturit (I)'), jarjestys = 1230;
-- Sorateiden hoito

-- Soratieluokat vuoteen 2022 saakka
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sorateiden pinnan hoito, hoitoluokka I', (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	'tiekm',	125, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1250, voimassaolo_loppuvuosi = 2022;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sorateiden pinnan hoito, hoitoluokka II', (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	'tiekm',	125, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1255, voimassaolo_loppuvuosi = 2022;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sorateiden pinnan hoito, hoitoluokka III', (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	'tiekm',	126, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1260, voimassaolo_loppuvuosi = 2022;
-- Soratieluokat vuodesta 2023 lähtien
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen, voimassaolo_alkuvuosi) VALUES (	'Soratieluokka I', (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	'tiekm',	125, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE, 2023)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1250, voimassaolo_alkuvuosi = 2023;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen, voimassaolo_alkuvuosi) VALUES (	'Soratieluokka II', (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	'tiekm',	125, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE, 2023)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1255, voimassaolo_alkuvuosi = 2023;

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sorapintaisten kävely- ja pyöräilyväylienhoito', (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	'tiekm',	127, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1270;
UPDATE toimenpidekoodi set emo = (select id from toimenpidekoodi where koodi = '23124') WHERE nimi = 'Maakivien (>1m3) poisto' AND emo = (select id from toimenpidekoodi where koodi ='20191'); -- korjataan ensin emo, jos rivi löytyy
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Maakivien (>1m3) poisto', (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	'jm',	130, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1270;

INSERT INTO toimenpidekoodi (nimi, emo, taso, luotu, luoja, yksikko, jarjestys, hinnoittelu, api_seuranta, api_tunnus, tehtavaryhma, ensisijainen, suunnitteluyksikko, voimassaolo_alkuvuosi)
VALUES ('Reunantäyttö km', (select id from toimenpidekoodi where koodi = '23116'), 4, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 'jkm', 1160, '{kokonaishintainen}', TRUE, 7067, (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'), FALSE, 'jkm', 2019)
    ON CONFLICT(nimi, emo) DO NOTHING;


-- MHU Ylläpito
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/päällystetyt tiet' , (select id from tehtavaryhma where nimi = 'Avo-ojitus, päällystetyt tiet (X)'), 'jm', 139, (select id from toimenpidekoodi where nimi = 'Avo-ojitus / päällystetyt tiet' and emo = (select id from toimenpidekoodi where koodi = '20112')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, päällystetyt tiet (X)'), jarjestys = 1390;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' , (select id from tehtavaryhma where nimi = 'Avo-ojitus, päällystetyt tiet (X)'), 'jm', 140, (select id from toimenpidekoodi where nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' and emo = (select id from toimenpidekoodi where koodi = '20112')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, päällystetyt tiet (X)'), jarjestys = 1400;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Laskuojat/päällystetyt tiet' , (select id from tehtavaryhma where nimi = 'Avo-ojitus, päällystetyt tiet (X)'), 'jm', 141, (select id from toimenpidekoodi where nimi = 'Laskuojat/päällystetyt tiet' and emo = (select id from toimenpidekoodi where koodi = '20112')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, päällystetyt tiet (X)'), jarjestys = 1410;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/soratiet' , (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), 'jm', 142, (select id from toimenpidekoodi where nimi = 'Sorateiden avo-ojitus' and emo = (select id from toimenpidekoodi where koodi = '20143')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), jarjestys = 1420;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/soratiet (kaapeli kaivualueella)' , (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), 'jm', 143, (select id from toimenpidekoodi where nimi = 'Sorateiden avo-ojitus (kaapeli kaivualueella)' and emo = (select id from toimenpidekoodi where koodi = '20143')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), jarjestys = 1430;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Laskuojat/soratiet' , (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), 'jm', 144, (select id from toimenpidekoodi where nimi = 'Laskuojat/soratiet' and emo = (select id from toimenpidekoodi where koodi = '20143')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), jarjestys = 1440;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kalliokynsien louhinta ojituksen yhteydessä', (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'),	'm2',	145, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), jarjestys = 1450;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, suunnitteluyksikko, jarjestys, api_tunnus, voimassaolo_alkuvuosi, emo, luotu, luoja, taso, ensisijainen) VALUES ('Sorateitä kaventava ojitus', (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)'), 'tiekm', 'tiekm', 1460, NULL, 2023, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO NOTHING;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Pohjavesisuojaukset', (select id from tehtavaryhma where nimi = 'Muut, MHU ylläpito (F)'),	NULL,	152, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Muut, MHU ylläpito (F)'), jarjestys = 1520;
-- Liikenneympäristön hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Nopeusnäyttötaulun hankinta', (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'),	'kpl/1. hoitovuosi',	155, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'), jarjestys = 1550;

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Pysäkkikatoksen uusiminen' , (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'), 'kpl', 157, (select id from toimenpidekoodi where nimi = 'Pysäkkikatoksen uusiminen' and emo = (select id from toimenpidekoodi where koodi = '14301')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, ylläpito (E)'), jarjestys = 1570;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Pysäkkikatoksen poistaminen' , (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'), 'kpl', 158, (select id from toimenpidekoodi where nimi = 'Pysäkkikatoksen poistaminen' and emo = (select id from toimenpidekoodi where koodi = '14301')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, ylläpito (E)'), jarjestys = 1580;

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Tilaajan rahavaraus lupaukseen 1', (select id from tehtavaryhma where nimi = 'Tilaajan rahavaraus (T3)'),	'euroa',	159, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'), jarjestys = 1590;

-- Uusi "Muut tavoitehintaan vaikuttavat rahavaraukset" MHU Ylläpidolle
INSERT INTO toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen)
VALUES ('Muut tavoitehintaan vaikuttavat rahavaraukset',
        (SELECT id FROM tehtavaryhma WHERE nimi = 'Tilaajan rahavaraus (T3)'),
        'euroa', 1591, NULL, (SELECT id FROM toimenpidekoodi WHERE koodi = '20191'), CURRENT_TIMESTAMP,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Tilaajan rahavaraus (T3)'),
                                         jarjestys    = 1591;


--  MHU
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoidonjohtopalkkio', (select id from tehtavaryhma where nimi = 'Hoidonjohtopalkkio (G)'),	NULL,	160, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Hoidonjohtopalkkio (G)'), jarjestys = 1600;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoitourakan työnjohto', (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'),	NULL,	161, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'), jarjestys = 1610;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.', (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'),	NULL,	162, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'), jarjestys = 1620;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoito- ja korjaustöiden pientarvikevarasto', (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'),	NULL,	163, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'), jarjestys = 1630;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon', (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'),	NULL,	164, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaus (J)'), jarjestys = 1640;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Toimitilat sähkö-, lämmitys-, vesi-, jäte-, siivous-, huolto-, korjaus- ja vakuutus- yms. kuluineen', (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)'),	NULL,	165, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)'), jarjestys = 1650;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut', (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)'),	NULL,	166, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)'), jarjestys = 1660;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)', (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)'),	NULL,	167, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)'), jarjestys = 1670;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoitovuoden päättäminen, tavoitepalkkio (dummy)', (select id from tehtavaryhma where nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio'),	NULL,	168, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio'), jarjestys = 1680;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoitovuoden päättäminen, tavoitehinnan ylitys (dummy)', (select id from tehtavaryhma where nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä'),	NULL,	169, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä'), jarjestys = 1690;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoitovuoden päättäminen, kattohinnan ylitys (dummy)', (select id from tehtavaryhma where nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä'),	NULL,	170, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä'), jarjestys = 1700;

------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Uudet tehtävät. Näille löytyi useita vastaavuuksia vanhoista tehtävistä ja täytyy lisätä ensisijainen tehtävä määrien suunnittelua varten. --------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
-- Jos löytyy oikean niminen vanha tehtävä, niputtavaa tehtävää ei ole luotu vaan käytetään niputtajana olemassa olevaa tehtävää.

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Liukkaudentorjunta hiekoituksella', (select id from tehtavaryhma where nimi = 'Talvisuola (B1)'),	'jkm',27, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvisuola (B1)'), jarjestys = 270;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Opastustaulun/-viitan uusiminen', (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'), 'm2',	42  , NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'), jarjestys = 420;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kuumapäällyste', (select id from tehtavaryhma where nimi = 'Kuumapäällyste (Y1)'),	'tonni', 102, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuumapäällyste (Y1)'), jarjestys = 1020;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Puhallus-SIP', (select id from tehtavaryhma where nimi = 'Puhallus-SIP (Y5)'),	'tonni', 105, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhallus-SIP (Y5)'), jarjestys = 1050;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Massasaumaus', (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'),	'tonni', 106, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'), jarjestys = 1060;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Valuasfaltti', (select id from tehtavaryhma where nimi = 'Valu (Y7)'),	'tonni', 107, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Valu (Y7)'), jarjestys = 1070;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Reunantäyttö', (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'), 'tonni',	116, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'), jarjestys = 1160;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Liikenteen varmistaminen kelirikkokohteessa', (select id from tehtavaryhma where nimi = 'Sorastus (M)'),	'tonni', 129, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorastus (M)'), jarjestys = 1290;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Soratien runkokelirikkokorjaukset', (select id from tehtavaryhma where nimi = 'RKR-korjaus (Q)'),	'tiem', 146, NULL, (select id from toimenpidekoodi where koodi = '14301'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RKR-korjaus (Q)'), jarjestys = 1460;


------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Uudet tehtävät. Äkillinen hoitotyö ja kolmansien osapuolten aiheuttamien vahinkojen korjaaminen                                            --------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

-- Talvihoidolle ja sorateiden hoidolle ei voi enää kustannussuunnitelmassa suunnitella Äkillisten hoitotöiden kustannuksia, mutta toteuma- ja kulupuolella kirjaus on silti mahdollinen
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Äkillinen hoitotyö (talvihoito)', (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Talvihoito (T1)'),	NULL,	135, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET nimi = 'Äkillinen hoitotyö (talvihoito)', tehtavaryhma = (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Talvihoito (T1)'), jarjestys = 1350;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Äkillinen hoitotyö (l.ymp.hoito)', (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)'),	NULL,	135, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET nimi = 'Äkillinen hoitotyö (l.ymp.hoito)',  tehtavaryhma = (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)'), jarjestys = 1351;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Äkillinen hoitotyö (soratiet)', (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Soratiet (T1)'),	NULL,	135, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET nimi = 'Äkillinen hoitotyö (soratiet)',  tehtavaryhma = (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Soratiet (T1)'), jarjestys = 1352;

-- Talvihoidolle ja sorateiden hoidolle ei voi enää kustannussuunnitelmassa suunnitella Vahinkojen korjausten kustannuksia, mutta toteuma- ja kulupuolella kirjaus on silti mahdollinen
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)', (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Talvihoito (T2)'), NULL, 136, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Talvihoito (T2)'), jarjestys = 1360;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)', (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)'),	NULL,	136, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)'), jarjestys = 1361;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)', (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Soratiet (T2)'),	NULL,	136, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
    ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Soratiet (T2)'), jarjestys = 1362;

--- MHU: Lisätyöt - nämä ovat dummy-tehtäviä, joita käytetään lisätöitä kirjatessa selventämään. Kuuluvat toimenpiteelle (tehtäväryhmä) 9 Lisätyöt

INSERT INTO toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen, kasin_lisattava_maara)
VALUES ('Lisätyö (talvihoito)',
        (SELECT id FROM tehtavaryhma WHERE otsikko = '9 LISÄTYÖT' AND nimi = 'Alataso Lisätyöt'), NULL, 9900, NULL,
        (SELECT id FROM toimenpidekoodi WHERE koodi = '23104'), current_timestamp,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), 4, TRUE, TRUE)  on conflict do nothing;

INSERT INTO toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen, kasin_lisattava_maara)
VALUES ('Lisätyö (l.ymp.hoito)',
        (SELECT id FROM tehtavaryhma WHERE otsikko = '9 LISÄTYÖT' AND nimi = 'Alataso Lisätyöt'), NULL, 9910, NULL,
        (SELECT id FROM toimenpidekoodi WHERE koodi = '23116'), current_timestamp,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), 4, TRUE, TRUE) on conflict do nothing;

INSERT INTO toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen, kasin_lisattava_maara)
VALUES ('Lisätyö (sorateiden hoito)',
        (SELECT id FROM tehtavaryhma WHERE otsikko = '9 LISÄTYÖT' AND nimi = 'Alataso Lisätyöt'), NULL, 9920, NULL,
        (SELECT id FROM toimenpidekoodi WHERE koodi = '23124'), current_timestamp,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio'), 4, TRUE, TRUE) ON CONFLICT DO NOTHING;

UPDATE toimenpidekoodi SET kasin_lisattava_maara = TRUE WHERE nimi IN ('Äkillinen hoitotyö (talvihoito)',
                                                                       'Äkillinen hoitotyö (l.ymp.hoito)',
                                                                       'Äkillinen hoitotyö (soratiet)');
-- Poistetaan ylimääräiset tehtävät:
DELETE from toimenpidekoodi WHERE nimi = 'Äkillinen hoitotyö (talvihoito, liikenteen varmistaminen)';
DELETE from toimenpidekoodi WHERE nimi = 'Äkillinen hoitotyö (l.ymp.hoito, liikenteen varmistaminen)';
DELETE from toimenpidekoodi WHERE nimi = 'Äkillinen hoitotyö (soratiet, liikenteen varmistaminen)';
-- Päivitetään oikeat vahinkojen korjauksen tehtävät käsinkirjattavien listaan:
UPDATE toimenpidekoodi SET kasin_lisattava_maara = TRUE WHERE nimi IN ('Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)',
                                                                       'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)',
                                                                       'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)');

-- Poistetaan ylimääräinen tehtäväryhmä:
DELETE from tehtavaryhma where otsikko = '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA' and nimi = 'Alataso Liikenteen varmistaminen';

-- Muuta nimi
UPDATE toimenpidekoodi SET nimi = 'Sorateiden pölynsidonta (materiaali)' WHERE nimi = 'Sorateiden pölynsidonta';

------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Olemassa olleiden tehtävien tehtäväryhmämäppäykset     --------------------------------------------------------------------------------------------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

----------------
-- TALVIHOITO --
----------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 50, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is 2-ajorat.' AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 60, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is 1-ajorat.'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 70, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is ohituskaistat' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 80, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is rampit'	AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 90, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib 2-ajorat.'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 100, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib 1-ajorat.'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 110, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib ohituskaistat'	AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 120, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib rampit'	AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 170, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'II'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 180, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'III'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 200, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'K1'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 210, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'K2'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvisuola (B1)'),	jarjestys = 250, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Suolaus' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
-- Suolaus-tehtävän yksikkö on jkm, materiaalit raportoidaan erikseen (tonneina)

-- Liukkauden torjunta hiekoittamalla
---------------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
-- Yksikkö on jkm, materiaalit tonneina erikseen raportointuna
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvisuola (B1)'),	jarjestys = 270, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'PisteHiekoitus' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvisuola (B1)'),	jarjestys = 270, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'LinjaHiekoitus' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 340, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ei yksilöity' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND yksikko = '-' AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	jarjestys = 340, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Muut talvihoitotyöt' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND yksikko = '-' AND poistettu is not true AND piilota is not true;


------------------------------
-- LIIKENNEYMPÄRISTÖN HOITO --
------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 360, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Valvontakameratolppien puhdistus/tarkistus keväisin' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 370, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapaalujen kp (uusien)'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 380, ensisijainen = TRUE, yksikko = 'kpl', suunnitteluyksikko = 'kpl', muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Porttaalien tarkastus ja huolto' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 390, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki'	AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 400, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)' AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 410, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)' AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


-- Opastustaulun/-viitan uusiminen
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 420, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastinviitan tai -taulun uusiminen ja lisääminen -ajoradan yläpuoliset opasteet'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 420, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;

-- Vanhalla nimellä, voi rivin voi poistaa, kun tehtävä on viety tuotantoon
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 430, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis. liikennemerkkien poistamisia)'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
-- Uudelleen nimetyn tehtävän päivitys
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 430, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


-- Opastustaulun/viitan uusiminen porttaalissa
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 440, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen portaaliin'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 440, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen -portaalissa olevan viitan/opastetaulun uusiminen'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'),	jarjestys = 440, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	jarjestys = 480, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Levähdysalueen Puhtaanapito' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	jarjestys = 510, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Meluesteiden pesu' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


-- Töherrysten poisto
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	jarjestys = 530, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Töherrysten poisto'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	jarjestys = 530, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Graffitien poisto'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	jarjestys = 540, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Töherrysten estokäsittely'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhtaanapito (P)'),	jarjestys = 550, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Katupölynsidonta' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vesakonraivaukset ja puun poisto (V)'),	jarjestys = 620, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vesakonraivaus N1'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vesakonraivaukset ja puun poisto (V)'),	jarjestys = 630, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vesakonraivaus N2' AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vesakonraivaukset ja puun poisto (V)'),	jarjestys = 640, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vesakonraivaus N3'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 650, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto N1' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 660, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto N2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 670, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto N3' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 680, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto T1/E1'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 690, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto T2/E2'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 700, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Puiden ja pensaiden hoito T1/E1'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 710, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Puiden ja pensaiden hoito T2/E2/N1' AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vesakonraivaukset ja puun poisto (V)'),	jarjestys = 645, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Runkopuiden poisto'	AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Nurmetukset ja muut vihertyöt (N)'),	jarjestys = 780, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Muut viheralueiden hoitoon liittyvät työt'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmät (K)'),	jarjestys = 790, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu'	AND (yksikko = '-' OR yksikko is NULL) AND emo = (select id from toimenpidekoodi where koodi = '23116');



------------------------------------
-- KORJAUS I                      --
------------------------------------

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kaiteet, aidat ja kivetykset (U)'),	jarjestys = 970, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset'	AND (yksikko = '' OR yksikko is NULL) AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');


------------------------------------
-- PÄÄLLYSTEIDEN PAIKKAUS         --
------------------------------------

-- Kuumapäällyste
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (kokoava tehtävä on nimeltään Kuumapäällyste)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuumapäällyste (Y1)'),	jarjestys = 1020, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus - Kuumapäällyste'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuumapäällyste (Y1)'),	jarjestys = 1020, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kuumapäällyste, ab käsityönä'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kuumapäällyste (Y1)'),	jarjestys = 1020, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Kuumapäällyste'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Kylmäpäällyste ml. SOP
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kylmäpäällyste (Y2)'),	jarjestys = 1030, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus, Kylmäpäällyste'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kylmäpäällyste (Y2)'),	jarjestys = 1030, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus -Kylmäpäällyste ml. SOP'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kylmäpäällyste (Y2)'),	jarjestys = 1030, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -Kylmäpäällyste ml. SOP'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Puhallus-SIP
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhallus-SIP (Y5)'),	jarjestys = 1050, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhallus-SIP (Y5)'),	jarjestys = 1050, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'SIP paikkaus (kesto+kylmä)' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Saumojen juottaminen bitumilla
-----------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Saumojen juottaminen bitumilla (Y6)'),	jarjestys = 1060, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus -Saumojen juottaminen bitumilla'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Massasaumaus ja konetiivistetty valuasfaltti
-----------------------------------
-- Viisi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'),	jarjestys = 1060, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Konetiivistetty Massasaumaus 10 cm leveä' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'),	jarjestys = 1060, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Konetiivistetty Massasaumaus 20 cm leveä' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'),	jarjestys = 1060, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus - Konetiivistetty Massasaumaus 20 cm leveä'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'),	jarjestys = 1060, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus - Massasaumaus'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'),	jarjestys = 1060, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Massasaumaus'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)'),	jarjestys = 1060, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty Valuasvaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Käsin tehtävät paikkaukset pikapaikkausmassalla
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Käsipaikkaus pikapaikkausmassalla (Y4)'),	jarjestys = 1040, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Käsin tehtävät paikkaukset pikapaikkausmassalla' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Valuasfaltti
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Valu (Y7)'),	jarjestys = 1070, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kuumapäällyste, Valuasfaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Valu (Y7)'),	jarjestys = 1070, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Valuasvaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Valu (Y7)'),	jarjestys = 1070, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus - Valuasfaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Siltapäällysteet (H)'),	jarjestys = 1090, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sillan päällysteen halkeaman avarrussaumaus'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Siltapäällysteet (H)'),	jarjestys = 1100, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sillan kannen päällysteen päätysauman korjaukset'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Siltapäällysteet (H)'),	jarjestys = 1110, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapalkin ja päällysteen väl. sauman tiivistäminen'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Siltapäällysteet (H)'),	jarjestys = 1120, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapalkin liikuntasauman tiivistäminen'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');


------------------------------------
-- LIIKENNEYMPÄRISTÖN HOITO --
------------------------------------

-- Reunantäyttö
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'),	jarjestys = 1160, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden pientareiden täyttö'	AND (yksikko = 'tn' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'),	jarjestys = 1160, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden sorapientareen täyttö'	AND (yksikko = 'tn' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'),	jarjestys = 1160, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden sr-pientareen täyttö'	AND (yksikko = 'tn' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');


UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'),	jarjestys = 1170, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden palteiden poisto'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorapientareet (O)'),	jarjestys = 1180, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapalteen poisto kaiteen alta'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');


------------------------------------
-- Sorateiden hoito --
------------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = null,	jarjestys = 1240, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorateiden hoito, hoitoluokka I'	AND yksikko = 'tiekm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23124');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'), jarjestys = 1280, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorateiden pölynsidonta (materiaali)' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23124');
-- Tehtävän yksikkö on jkm, materiaalit erikseen (t)

-- Sorastus
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorastus (M)'),	jarjestys = 1290, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorastus' AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorastus (M)'),	jarjestys = 1290, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorastus km' AND poistettu is not true AND piilota is not true;


UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorastus (M)'),	jarjestys = 1310, ensisijainen = TRUE, voimassaolo_loppuvuosi = 2020, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Oja- ja luiskameteriaalin käyttö kulutuskerrokseeen' AND emo = (select id from toimenpidekoodi where koodi = '23124');

-- Liikenteen varmistaminen kelirikkokohteessa
--------------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorastus (M)'),	jarjestys = 1320, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Liikenteen varmistaminen kelirikkokohteessa' AND emo = (select id from toimenpidekoodi where koodi = '23124');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorastus (M)'),	jarjestys = 1320, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Liikenteen varmistaminen kelirikkokohteessa (tonni)'	AND yksikko = 'tonni' AND emo = (select id from toimenpidekoodi where koodi = '23124');

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	jarjestys = 1340, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ei yksilöity' AND emo = (select id from toimenpidekoodi where koodi = '23124')	AND (yksikko = '-' OR yksikko is NULL) AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'Sorateiden hoito (C)'),	jarjestys = 1340, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Muut Sorateiden hoitoon liittyvät tehtävät' AND emo = (select id from toimenpidekoodi where koodi = '23124')	AND (yksikko = '-' OR yksikko is NULL) AND poistettu is not true AND piilota is not true;


------------------------------------
-- LIIKENNEYMPÄRISTÖN HOITO --
------------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)'),	jarjestys = 1560, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nopeusnäyttötaulujen ylläpito, käyttö ja siirto'	AND emo = (select id from toimenpidekoodi where koodi = '23116');-- AND yksikko = 'kpl'; yksikkö on erä eikä kappal AND poistettu is not true AND piilota is not truee


-- Päivitä vielä nimet
UPDATE toimenpidekoodi set nimi = 'Muut talvihoitotyöt' WHERE emo = (select id from toimenpidekoodi where koodi = '23104') and nimi = 'Ei yksilöity';
UPDATE toimenpidekoodi set nimi = 'Muut Sorateiden hoitoon liittyvät tehtävät' WHERE emo = (select id from toimenpidekoodi where koodi = '23124') and nimi = 'Ei yksilöity';


-- Muokkaukset uuden Tehtävä- ja määräluetteloversion johdosta
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Hiekoitus (B3)') WHERE  nimi in ('LinjaHiekoitus', 'PisteHiekoitus', 'Liukkaudentorjunta hiekoituksella');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kylmäpäällyste (Y2)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN(
                                         'Päällysteiden paikkaus -Kylmäpäällyste ml. SOP',
                                         'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -Kylmäpäällyste ml. SOP',
                                         'Päällysteiden paikkaus, Kylmäpäällyste');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Puhallus-SIP (Y5)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN(
                                         'Puhallus-SIP',
                                         'SIP paikkaus (kesto+kylmä)',
                                         'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Saumojen juottaminen bitumilla (Y6)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN('Päällysteiden paikkaus -Saumojen juottaminen bitumilla');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN
                                 ('Päällysteiden paikkaus - Massasaumaus',
                                  'Konetiivistetty Massasaumaus 10 cm leveä',
                                  'Konetiivistetty Massasaumaus 20 cm leveä',
                                  'Massasaumaus',
                                  'Päällysteiden paikkaus - Konetiivistetty Massasaumaus 20 cm leveä',
                                  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Massasaumaus');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-Valu (Y3)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN
                                 ('Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty Valuasvaltti');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Valu (Y7)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN
                                 ('Kuumapäällyste, Valuasfaltti',
                                  'Päällysteiden paikkaus - Valuasfaltti',
                                  'Valuasfaltti',
                                  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Valuasvaltti');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Kesäsuola, materiaali (D)') WHERE
    tehtavaryhma IS NOT NULL and nimi = 'Sorateiden pölynsidonta (materiaali)';

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Talvihoito (T1)') WHERE nimi = 'Äkillinen hoitotyö (talvihoito)' AND emo = (select id from toimenpidekoodi where koodi = '23104');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)') WHERE nimi = 'Äkillinen hoitotyö (l.ymp.hoito)' AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Äkilliset hoitotyöt, Soratiet (T1)') WHERE nimi = 'Äkillinen hoitotyö (soratiet)' AND emo = (select id from toimenpidekoodi where koodi = '23124');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Talvihoito (T2)') WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)' AND emo = (select id from toimenpidekoodi where koodi = '23104');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)') WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)' AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Vahinkojen korjaukset, Soratiet (T2)') WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)' AND emo = (select id from toimenpidekoodi where koodi = '23124');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN ('Kalliokynsien louhinta ojituksen yhteydessä');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Tilaajan rahavaraus (T3)') WHERE
    tehtavaryhma IS NOT NULL and nimi IN ('Tilaajan rahavaraus lupaukseen 1');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'Avo-ojitus, soratiet (Z)') WHERE nimi = 'Kalliokynsien louhinta ojituksen yhteydessä';

-- Päivitä toimenpidekoodien järjestys uuden tehtävä- ja määräluettelon mukaiseksi (MHU)
UPDATE toimenpidekoodi SET jarjestys = 10	WHERE nimi = 'Ise 2-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 20	WHERE nimi = 'Ise 1-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 30	WHERE nimi = 'Ise ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 40	WHERE nimi = 'Ise rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 50	WHERE nimi = 'Is 2-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 60	WHERE nimi = 'Is 1-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 70	WHERE nimi = 'Is ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 80	WHERE nimi = 'Is rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 90	WHERE nimi = 'Ib 2-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 100	WHERE nimi = 'Ib 1-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 110	WHERE nimi = 'Ib ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 120	WHERE nimi = 'Ib rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 130	WHERE nimi = 'Ic 2-ajorat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 140	WHERE nimi = 'Ic 1-ajorat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 150	WHERE nimi = 'Ic ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 160	WHERE nimi = 'Ic rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 170	WHERE nimi = 'II' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 180	WHERE nimi = 'III' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 190	WHERE nimi = 'Kävely- ja pyöräilyväylien laatukäytävät' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 200	WHERE nimi = 'K1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 210	WHERE nimi = 'K2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 220	WHERE nimi = 'Levähdys- ja pysäköimisalueet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 230	WHERE nimi = 'Muiden alueiden talvihoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 240	WHERE nimi = 'Talvihoidon kohotettu laatu' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 250	WHERE nimi = 'Suolaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 260	WHERE nimi = 'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 270	WHERE nimi = 'LinjaHiekoitus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 270	WHERE nimi = 'PisteHiekoitus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 270	WHERE nimi = 'Liukkaudentorjunta hiekoituksella' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 280	WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 290	WHERE nimi = 'Pysäkkikatosten puhdistus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 300	WHERE nimi = 'Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 310	WHERE nimi = 'Portaiden talvihoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 320	WHERE nimi = 'Lisäkalustovalmius/-käyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 340	WHERE nimi = 'Muut talvihoitotyöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 350	WHERE nimi = 'Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 360	WHERE nimi = 'Valvontakameratolppien puhdistus/tarkistus keväisin' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 370	WHERE nimi = 'Reunapaalujen kp (uusien)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 380	WHERE nimi = 'Porttaalien tarkastus ja huolto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 390	WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 400	WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 410	WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 420, kasin_lisattava_maara = TRUE	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 420, kasin_lisattava_maara = TRUE	WHERE nimi = 'Opastinviitan tai -taulun uusiminen ja lisääminen -ajoradan yläpuoliset opasteet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 420, kasin_lisattava_maara = TRUE	WHERE nimi = 'Opastustaulun/-viitan uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 430, kasin_lisattava_maara = TRUE	WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 440	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen portaaliin' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 440, kasin_lisattava_maara = TRUE	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 440	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -portaalissa olevan viitan/opastetaulun uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 470	WHERE nimi = 'Muut liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoitotyöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 480	WHERE nimi = 'Levähdysalueen Puhtaanapito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 490	WHERE nimi = 'Tie- ja levähdysalueiden kalusteiden kunnossapito ja hoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 500	WHERE nimi = 'Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 510	WHERE nimi = 'Meluesteiden pesu' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 520	WHERE nimi = 'Hiekoitushiekan ja irtoainesten poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 530, kasin_lisattava_maara = TRUE	WHERE nimi = 'Graffitien poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 530	WHERE nimi = 'Töherrysten poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 540	WHERE nimi = 'Töherrysten estokäsittely' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 550	WHERE nimi = 'Katupölynsidonta' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 570	WHERE nimi = 'Muut tie- levähdys- ja liitännäisalueiden puhtaanpitoon ja kalusteiden hoitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 630	WHERE nimi = 'Vesakonraivaus N1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 630	WHERE nimi = 'Vesakonraivaus N2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 640	WHERE nimi = 'Vesakonraivaus N3' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 650	WHERE nimi = 'Nurmetuksen hoito / niitto N1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 660	WHERE nimi = 'Nurmetuksen hoito / niitto N2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 670	WHERE nimi = 'Nurmetuksen hoito / niitto N3' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 680	WHERE nimi = 'Nurmetuksen hoito / niitto T1/E1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 690	WHERE nimi = 'Nurmetuksen hoito / niitto T2/E2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 700	WHERE nimi = 'Puiden ja pensaiden hoito T1/E1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 710	WHERE nimi = 'Puiden ja pensaiden hoito T2/E2/N1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 720	WHERE nimi = 'Erillisten hoito-ohjeiden mukaiset vihertyöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 730	WHERE nimi = 'Erillisten hoito-ohjeiden mukaiset vihertyöt, uudet alueet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 645, kasin_lisattava_maara = TRUE WHERE nimi = 'Runkopuiden poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 750	WHERE nimi = 'Vesistöpenkereiden hoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 760	WHERE nimi = 'Tiekohtaiset maisemanhoitoprojektit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 780	WHERE nimi = 'Muut viheralueiden hoitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 800	WHERE nimi = 'Sadevesi- ja salaojakaivojen sekä -putkistojen tyhjennys, puhdistus (huuhtelu) ja toiminnan varmistaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 805	WHERE nimi = 'Kaivojen ja putkistojen tarkastus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 810	WHERE nimi = 'Kaivojen ja putkistojen sulatus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 820	WHERE nimi = 'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 840	WHERE nimi = 'Rumpujen sulatus, aukaisu ja toiminnan varmistaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 850	WHERE nimi = 'Rumpujen tarkastus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1371, kasin_lisattava_maara = TRUE	WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1372, kasin_lisattava_maara = TRUE	WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1373, kasin_lisattava_maara = TRUE	WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1374, kasin_lisattava_maara = TRUE	WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1375, kasin_lisattava_maara = TRUE WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1376, kasin_lisattava_maara = TRUE	WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1377, kasin_lisattava_maara = TRUE	WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1378, kasin_lisattava_maara = TRUE	WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 855	WHERE nimi = 'Muut rumpujen kunnossapitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 970	WHERE nimi = 'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 980	WHERE nimi = 'Reunakivivaurioiden korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1010	WHERE nimi = 'Muut kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1020	WHERE nimi = 'Kuumapäällyste, ab käsityönä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1020, kasin_lisattava_maara = TRUE WHERE nimi = 'Kuumapäällyste' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1020	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Kuumapäällyste' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1030	WHERE nimi = 'Päällysteiden paikkaus -Kylmäpäällyste ml. SOP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1030	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -Kylmäpäällyste ml. SOP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1030, kasin_lisattava_maara = TRUE WHERE nimi = 'Päällysteiden paikkaus, Kylmäpäällyste' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1040, kasin_lisattava_maara = TRUE WHERE nimi = 'Puhallus-SIP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1040	WHERE nimi = 'SIP paikkaus (kesto+kylmä)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1040	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1050	WHERE nimi = 'Päällysteiden paikkaus -Saumojen juottaminen bitumilla' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1060	WHERE nimi = 'Päällysteiden paikkaus - Massasaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1060	WHERE nimi = 'Konetiivistetty Massasaumaus 10 cm leveä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1060	WHERE nimi = 'Konetiivistetty Massasaumaus 20 cm leveä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1060, kasin_lisattava_maara = TRUE	WHERE nimi = 'Massasaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1060	WHERE nimi = 'Päällysteiden paikkaus - Konetiivistetty Massasaumaus 20 cm leveä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1060	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Massasaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1070	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty Valuasvaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1070	WHERE nimi = 'Kuumapäällyste, Valuasfaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1070	WHERE nimi = 'Päällysteiden paikkaus - Valuasfaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1070, kasin_lisattava_maara = TRUE	WHERE nimi = 'Valuasfaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1070	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - Valuasvaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1090	WHERE nimi = 'Sillan päällysteen halkeaman avarrussaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1100	WHERE nimi = 'Sillan kannen päällysteen päätysauman korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1110	WHERE nimi = 'Reunapalkin ja päällysteen väl. sauman tiivistäminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1120	WHERE nimi = 'Reunapalkin liikuntasauman tiivistäminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1160	WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1160	WHERE nimi = 'Päällystettyjen teiden pientareiden täyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1160, kasin_lisattava_maara = FALSE WHERE nimi = 'Reunantäyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1160, kasin_lisattava_maara = FALSE 	WHERE nimi = 'Päällystettyjen teiden sorapientareen täyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1170, kasin_lisattava_maara = TRUE WHERE nimi = 'Päällystettyjen teiden palteiden poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1180	WHERE nimi = 'Reunapalteen poisto kaiteen alta' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1200	WHERE nimi = 'Muut päällystettyjen teiden sorapientareiden kunnossapitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1210	WHERE nimi = 'Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1220	WHERE nimi = 'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1240	WHERE nimi = 'Muut siltojen ja laitureiden hoitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1240	WHERE nimi = 'Sorateiden hoito, hoitoluokka I' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1250	WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka I' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1255	WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka II' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1260	WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka III' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1270	WHERE nimi = 'Sorapintaisten kävely- ja pyöräilyväylienhoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1280	WHERE nimi = 'Sorateiden pölynsidonta (materiaali)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1290, kasin_lisattava_maara = FALSE	WHERE nimi = 'Sorastus' AND tehtavaryhma IS NOT NULL; -- Materiaalimäärät kirjataan materiaalitoteumine
UPDATE toimenpidekoodi SET jarjestys = 1290	WHERE nimi = 'Sorastus km' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1270, kasin_lisattava_maara = TRUE WHERE nimi = 'Maakivien (>1m3) poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1310, kasin_lisattava_maara = TRUE WHERE nimi = 'Oja- ja luiskameteriaalin käyttö kulutuskerrokseeen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1320, kasin_lisattava_maara = FALSE WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa' AND tehtavaryhma IS NOT NULL; -- Materiaalimäärät kirjataan materiaalitoteumina
UPDATE toimenpidekoodi SET jarjestys = 1340	WHERE nimi = 'Muut Sorateiden hoitoon liittyvät tehtävät' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1350 where nimi = 'Äkillinen hoitotyö (talvihoito)';
UPDATE toimenpidekoodi SET jarjestys = 1351 where nimi = 'Äkillinen hoitotyö (l.ymp.hoito)';
UPDATE toimenpidekoodi SET jarjestys = 1352 where nimi = 'Äkillinen hoitotyö (soratiet)';
UPDATE toimenpidekoodi SET jarjestys = 1360 where nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)';
UPDATE toimenpidekoodi SET jarjestys = 1361 where nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)';
UPDATE toimenpidekoodi SET jarjestys = 1362 where nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)';
UPDATE toimenpidekoodi SET jarjestys = 1390, kasin_lisattava_maara = TRUE WHERE nimi = 'Avo-ojitus/päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1400, kasin_lisattava_maara = TRUE WHERE nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1410, kasin_lisattava_maara = TRUE WHERE nimi = 'Laskuojat/päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1420, kasin_lisattava_maara = TRUE WHERE nimi = 'Avo-ojitus/soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1430, kasin_lisattava_maara = TRUE WHERE nimi = 'Avo-ojitus/soratiet (kaapeli kaivualueella)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1440, kasin_lisattava_maara = TRUE WHERE nimi = 'Laskuojat/soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1450, kasin_lisattava_maara = TRUE WHERE nimi = 'Kalliokynsien louhinta ojituksen yhteydessä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1460, kasin_lisattava_maara = TRUE WHERE nimi = 'Soratien runkokelirikkokorjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1550	WHERE nimi = 'Nopeusnäyttötaulun hankinta' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1560	WHERE nimi = 'Nopeusnäyttötaulujen ylläpito, käyttö ja siirto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1570, kasin_lisattava_maara = TRUE WHERE nimi = 'Pysäkkikatoksen uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1571, kasin_lisattava_maara = TRUE WHERE nimi = 'Pysäkkikatoksen poistaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1575 WHERE nimi = 'Pohjavesisuojaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1590	WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1600	WHERE nimi = 'Hoidonjohtopalkkio' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1610	WHERE nimi = 'Hoitourakan työnjohto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1620	WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1630	WHERE nimi = 'Hoito- ja korjaustöiden pientarvikevarasto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1640	WHERE nimi = 'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1650	WHERE nimi = 'Toimitilat sähkö-, lämmitys-, vesi-, jäte-, siivous-, huolto-, korjaus- ja vakuutus- yms. kuluineen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1660	WHERE nimi = 'Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1670	WHERE nimi = 'Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)' AND tehtavaryhma IS NOT NULL;

UPDATE tehtavaryhma SET jarjestys = 1 WHERE nimi = 'Talvihoito';
UPDATE tehtavaryhma SET jarjestys = 25 WHERE nimi = 'Liukkaudentorjunta';
UPDATE tehtavaryhma SET jarjestys = 1 WHERE nimi = 'Talvihoito (A)';
UPDATE tehtavaryhma SET jarjestys = 25 WHERE nimi = 'Talvisuola (B1)';
UPDATE tehtavaryhma SET jarjestys = 26 WHERE nimi = 'KFO, NAFO (B2)';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Sillat';
UPDATE tehtavaryhma SET jarjestys = 124 WHERE nimi = 'Sorateiden hoito';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Hoitoluokat, kevarit ja kivet sekä muut';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Sorastus, luiska ja varmistus';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Sorastus, luiska ja varmistus';
UPDATE tehtavaryhma SET jarjestys = 139 WHERE nimi = 'MHU Ylläpito';
UPDATE tehtavaryhma SET jarjestys = 135 WHERE nimi = 'Äkilliset hoitotyöt, Soratiet (T1)';
UPDATE tehtavaryhma SET jarjestys = 139 WHERE nimi = 'Ojat';
UPDATE tehtavaryhma SET jarjestys = 152 WHERE nimi = 'Pohjavesisuojaukset';
UPDATE tehtavaryhma SET jarjestys = 152 WHERE nimi = 'Muut, MHU ylläpito (F)';
UPDATE tehtavaryhma SET jarjestys = 159 WHERE nimi = 'Muut liik.ymp.hoitosasiat';
UPDATE tehtavaryhma SET jarjestys = 155 WHERE nimi = 'ELY-rahoitteiset, liikenneympäristön hoito (E)';
UPDATE tehtavaryhma SET jarjestys = 157 WHERE nimi = 'ELY-rahoitteiset, ylläpito (E)';
UPDATE tehtavaryhma SET jarjestys = 161 WHERE nimi = 'Johto- ja hallintokorvaukseen sisältyvät tehtävät';
UPDATE tehtavaryhma SET jarjestys = 165 WHERE nimi = 'Erillishankinnat erillishinnoin';
UPDATE tehtavaryhma SET jarjestys = 139 WHERE nimi = 'Avo-ojitus, päällystetyt tiet (X)';
UPDATE tehtavaryhma SET jarjestys = 142 WHERE nimi = 'Avo-ojitus, soratiet (Z)';
UPDATE tehtavaryhma SET jarjestys = 146 WHERE nimi = 'RKR-korjaus (Q)';
UPDATE tehtavaryhma SET jarjestys = 160 WHERE nimi = 'Hoidonjohtopalkkio (G)';
UPDATE tehtavaryhma SET jarjestys = 161 WHERE nimi = 'Johto- ja hallintokorvaus (J)';
UPDATE tehtavaryhma SET jarjestys = 165 WHERE nimi = 'Välitaso Erillishankinnat erillishinnoin';
UPDATE tehtavaryhma SET jarjestys = 124 WHERE nimi = 'Sorateiden pinnan hoito';
UPDATE tehtavaryhma SET jarjestys = 124 WHERE nimi = 'Sorateiden hoito (C)';
UPDATE tehtavaryhma SET jarjestys = 165 WHERE nimi = 'Erillishankinnat (W)';
UPDATE tehtavaryhma SET jarjestys = 130 WHERE nimi = 'Muut sorateiden hoidon tehtävät';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Ojitus';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Ojitus';
UPDATE tehtavaryhma SET jarjestys = 35 WHERE nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen';
UPDATE tehtavaryhma SET jarjestys = 48 WHERE nimi = 'Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito';
UPDATE tehtavaryhma SET jarjestys = 48 WHERE nimi = 'Välitaso Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito';
UPDATE tehtavaryhma SET jarjestys = 62 WHERE nimi = 'Viheralueiden hoito';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Vesakko ja runkopuu';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Vesakko ja runkopuu';
UPDATE tehtavaryhma SET jarjestys = 66 WHERE nimi = 'Nurmi, puut ja pensaat sekä muut virheraluehommat';
UPDATE tehtavaryhma SET jarjestys = 62 WHERE nimi = 'Vesakon raivaus ja runkopuun poisto';
UPDATE tehtavaryhma SET jarjestys = 79 WHERE nimi = 'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito';
UPDATE tehtavaryhma SET jarjestys = 79 WHERE nimi = 'Sade, kaivot ja rummut';
UPDATE tehtavaryhma SET jarjestys = 86 WHERE nimi = 'Rumpujen hoito';
UPDATE tehtavaryhma SET jarjestys = 86 WHERE nimi = 'Rumpujen kunnossapito ja uusiminen (päällystetty tie)';
UPDATE tehtavaryhma SET jarjestys = 90 WHERE nimi = 'Rumpujen kunnossapito ja uusiminen (soratie)';
UPDATE tehtavaryhma SET jarjestys = 35 WHERE nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)';
UPDATE tehtavaryhma SET jarjestys = 48 WHERE nimi = 'Puhtaanapito (P)';
UPDATE tehtavaryhma SET jarjestys = 62 WHERE nimi = 'Vesakonraivaukset ja puun poisto (V)';
UPDATE tehtavaryhma SET jarjestys = 66 WHERE nimi = 'Nurmetukset ja muut vihertyöt (N)';
UPDATE tehtavaryhma SET jarjestys = 79 WHERE nimi = 'Kuivatusjärjestelmät (K)';
UPDATE tehtavaryhma SET jarjestys = 141 WHERE nimi = 'Rummut, päällystetiet (R)';
UPDATE tehtavaryhma SET jarjestys = 142 WHERE nimi = 'Rummut, soratiet (S)';
UPDATE tehtavaryhma SET jarjestys = 120 WHERE nimi = 'Sorastus (M)';
UPDATE tehtavaryhma SET jarjestys = 97 WHERE nimi = 'Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito';
UPDATE tehtavaryhma SET jarjestys = 102 WHERE nimi = 'Päällysteiden paikkaus';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Sillat'; ---
UPDATE tehtavaryhma SET jarjestys = 109 WHERE nimi = 'Sillan päällysteen korjaus';
UPDATE tehtavaryhma SET jarjestys = 116 WHERE nimi = 'Päällystettyjen teiden sorapientareen kunnossapito';
UPDATE tehtavaryhma SET jarjestys = 121 WHERE nimi = 'Siltojen ja laitureiden hoito';
UPDATE tehtavaryhma SET jarjestys = 26 WHERE nimi = 'KFo,NaFo (B2)';
UPDATE tehtavaryhma SET jarjestys = 27 WHERE nimi = 'Hiekoitus (B3)';
UPDATE tehtavaryhma SET jarjestys = 97 WHERE nimi = 'Kaiteet, aidat ja kivetykset (U)';
UPDATE tehtavaryhma SET jarjestys = 116 WHERE nimi = 'Sorapientareet (O)';
UPDATE tehtavaryhma SET jarjestys = 121 WHERE nimi = 'Sillat ja laiturit (I)';
UPDATE tehtavaryhma SET jarjestys = 129 WHERE nimi = 'Kesäsuola, materiaali (D)';
UPDATE tehtavaryhma SET jarjestys = 159 WHERE nimi = 'Tilaajan rahavaraus (T3)';

UPDATE tehtavaryhma SET otsikko = '2.1 LIIKENNEYMPÄRISTÖN HOITO / Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen' WHERE otsikko = '2.1 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.2 LIIKENNEYMPÄRISTÖN HOITO / Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito' WHERE otsikko = '2.2 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.3 LIIKENNEYMPÄRISTÖN HOITO / Viheralueiden hoito' WHERE otsikko = '2.3 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.4 LIIKENNEYMPÄRISTÖN HOITO / Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito' WHERE otsikko = '2.4 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.5 LIIKENNEYMPÄRISTÖN HOITO / Rumpujen kunnossapito ja uusiminen' WHERE otsikko = '2.5 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.6 LIIKENNEYMPÄRISTÖN HOITO / Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito' WHERE otsikko = '2.6 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus' WHERE otsikko = '2.7 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.8 LIIKENNEYMPÄRISTÖN HOITO / Päällystettyjen teiden sorapientareen kunnossapito' WHERE otsikko = '2.8 LIIKENNEYMPÄRISTÖN HOITO';
UPDATE tehtavaryhma SET otsikko = '2.9 LIIKENNEYMPÄRISTÖN HOITO / Siltojen ja laitureiden hoito' WHERE otsikko = '2.9 LIIKENNEYMPÄRISTÖN HOITO';

-- Lisätään tunnisteet sellaisille tehtäväryhmille ja tehtäville, joita käytetään esim. kustannusarvioiden hakemisessa
UPDATE tehtavaryhma SET yksiloiva_tunniste = '24103c8d-3a8a-4b6f-9315-570834d4479d' WHERE nimi = 'Äkilliset hoitotyöt, Talvihoito (T1)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = 'c3cb9e68-7f08-4145-ad8f-f2985e8f1658' WHERE nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '5a6760e8-6494-4db2-80bc-c06df391a5b6' WHERE nimi = 'Äkilliset hoitotyöt, Soratiet (T1)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '0623ae3c-b8b0-4791-96ea-4808029d43de' WHERE nimi = 'Vahinkojen korjaukset, Talvihoito (T2)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '1b374802-dbe7-430b-bfc5-4635383d18e3' WHERE nimi = 'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = 'df612065-20d5-47b9-8cca-51ffd250e1f8' WHERE nimi = 'Vahinkojen korjaukset, Soratiet (T2)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c' WHERE nimi = 'Erillishankinnat (W)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6' WHERE nimi = 'Tilaajan rahavaraus (T3)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54' WHERE nimi = 'Johto- ja hallintokorvaus (J)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '0ef0b97e-1390-4d6c-bbc4-b30536be8a68' WHERE nimi = 'Hoidonjohtopalkkio (G)';

UPDATE tehtavaryhma SET yksiloiva_tunniste = 'ce9264f7-0860-4be0-a447-ac79822c3ca6' WHERE nimi = 'Muut, liikenneympäristön hoito (F)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '4e3cf237-fdf5-4f58-b2ec-319787127b3e' WHERE nimi = 'Muut, MHU ylläpito (F)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '55c920e7-5656-4bb0-8437-1999add714a3' WHERE nimi = 'Hoitovuoden päättäminen / Tavoitepalkkio';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '19907c24-dd26-460f-9cb4-2ed974b891aa' WHERE nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä';
UPDATE tehtavaryhma SET yksiloiva_tunniste = 'be34116b-2264-43e0-8ac8-3762b27a9557' WHERE nimi = 'Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä';

UPDATE tehtavaryhma SET yksiloiva_tunniste = 'c7d9be7c-7bea-49a4-bd30-a432041cf6dd' WHERE nimi = 'Alataso Lisätyöt'; -- Dummy tehtäväryhmä siinä mielessä, että tälle ei kirjata kustannuksia

UPDATE toimenpidekoodi SET yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8' WHERE nimi = 'Hoidonjohtopalkkio';
UPDATE toimenpidekoodi SET yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744' WHERE nimi = 'Hoitourakan työnjohto';
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.';
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '49b7388b-419c-47fa-9b1b-3797f1fab21d' WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito)' AND
        tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '0623ae3c-b8b0-4791-96ea-4808029d43de');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '63a2585b-5597-43ea-945c-1b25b16a06e2' WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito)' AND
        tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '1b374802-dbe7-430b-bfc5-4635383d18e3');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = 'b3a7a210-4ba6-4555-905c-fef7308dc5ec' WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet)' AND
        tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'df612065-20d5-47b9-8cca-51ffd250e1f8');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '1f12fe16-375e-49bf-9a95-4560326ce6cf' WHERE nimi = 'Äkillinen hoitotyö (talvihoito)' AND
        tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '24103c8d-3a8a-4b6f-9315-570834d4479d');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974' WHERE nimi = 'Äkillinen hoitotyö (l.ymp.hoito)' AND
        tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'c3cb9e68-7f08-4145-ad8f-f2985e8f1658');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = 'd373c08b-32eb-4ac2-b817-04106b862fb1' WHERE nimi = 'Äkillinen hoitotyö (soratiet)' AND
        tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '5a6760e8-6494-4db2-80bc-c06df391a5b6');

UPDATE toimenpidekoodi
   SET yksiloiva_tunniste = '4342cd30-a9b7-4194-94ee-00c0ce1f6fc6'
 WHERE nimi = 'Tunnelit'
   AND tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'ce9264f7-0860-4be0-a447-ac79822c3ca6');

UPDATE toimenpidekoodi
   SET yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'
 WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1'
   AND tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6');

UPDATE toimenpidekoodi
   SET yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92'
 WHERE nimi = 'Muut tavoitehintaan vaikuttavat rahavaraukset'
   AND tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6');

UPDATE toimenpidekoodi
   SET yksiloiva_tunniste = 'e5f61569-bed3-4be3-8aa0-9e0dd2725c6b'
 WHERE nimi = 'Hoitovuoden päättäminen'
   AND tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '55c920e7-5656-4bb0-8437-1999add714a3');

-- Lisätyöt

UPDATE toimenpidekoodi
   SET yksiloiva_tunniste = 'e32341fc-775a-490a-8eab-c98b8849f968'
 WHERE nimi = 'Lisätyö (talvihoito)'
   AND tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'c7d9be7c-7bea-49a4-bd30-a432041cf6dd');

UPDATE toimenpidekoodi
   SET yksiloiva_tunniste = '0c466f20-620d-407d-87b0-3cbb41e8342e'
 WHERE nimi = 'Lisätyö (l.ymp.hoito)'
   AND tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'c7d9be7c-7bea-49a4-bd30-a432041cf6dd');

UPDATE toimenpidekoodi
   SET yksiloiva_tunniste = 'c058933e-58d3-414d-99d1-352929aa8cf9'
 WHERE nimi = 'Lisätyö (sorateiden hoito)'
   AND tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'c7d9be7c-7bea-49a4-bd30-a432041cf6dd');


-- Korjataan toimenpidekoodien (tehtävien) tehtäväryhmämäppäyksiä
UPDATE toimenpidekoodi SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'KT-Valu (Y3)') WHERE nimi in ('KT-valuasfalttipaikkaus T', 'KT-valuasfalttipaikkaus K', 'KT-reikävaluasfalttipaikkaus', 'KT-valuasfalttisaumaus');
UPDATE toimenpidekoodi SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Puhallus-SIP (Y5)') WHERE nimi in ('Sirotepuhalluspaikkaus (SIPU)');
UPDATE toimenpidekoodi SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'Saumojen juottaminen bitumilla (Y6)') WHERE nimi in ('Kannukaatosaumaus', 'Päällysteiden paikkaus -saumojen juottaminen bitumilla');
UPDATE toimenpidekoodi SET tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE nimi = 'KT-Valu (Y3)') WHERE nimi in ('Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty valuasvaltti');
UPDATE toimenpidekoodi SET jarjestys = 1040 WHERE nimi = 'KT-valuasfalttipaikkaus K';
UPDATE toimenpidekoodi SET jarjestys = 1041 WHERE nimi = 'KT-valuasfalttipaikkaus T';
UPDATE toimenpidekoodi SET jarjestys = 1042 WHERE nimi = 'KT-reikävaluasfalttipaikkaus';
UPDATE toimenpidekoodi SET jarjestys = 1044 WHERE nimi = 'Käsin tehtävät paikkaukset pikapaikkausmassalla';
UPDATE toimenpidekoodi SET jarjestys = 1080 WHERE nimi = 'KT-valuasfalttisaumaus';

-- Päivitetään api-tunnus tehtävähierarkian tehtäville, joilla sitä ei entuudestaan ole. Sama tunnus kaikkiin ympäristöihin (prod, stg, test, local). Huom. osa tehtävistä puuttuu kehitysympäristöstä.
-- Api-tunnuksen olemassa olo ei tarkoita sitä, että tehtävälle kirjataan apin kautta toteumia. Api-käyttöä määrittää seurataan-apin-kautta-sarake.
UPDATE toimenpidekoodi set api_tunnus = id where api_tunnus is null and api_seuranta is true;

-- Tehtävä oli sovittu poistettavaksi, mutta MH-urakoiksi muunnetuissa HJ-urakoissa oli tähän liittyviä tehtävämääräsuunnitelmia.
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2018 WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt';

-- Tehtävä- ja määräluettelossa näkyvien yksiköiden päivitys
UPDATE toimenpidekoodi
   SET suunnitteluyksikko = 'kuivatonnia'
 WHERE taso = 4
   AND nimi in ('Suolaus', 'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)');

UPDATE toimenpidekoodi
   SET suunnitteluyksikko = 'tonni'
 WHERE taso = 4
   AND nimi in
       ('Liukkaudentorjunta hiekoituksella', 'Ennalta arvaamattomien kuljetusten avustaminen', 'Sorateiden pölynsidonta (materiaali)',
        'Liikenteen varmistaminen kelirikkokohteessa');

UPDATE toimenpidekoodi
   SET suunnitteluyksikko = 'kpl'
 WHERE taso = 4
   AND nimi in
       ('Valvontakameratolppien puhdistus/tarkistus keväisin', 'Nopeusnäyttötaulujen ylläpito, käyttö ja siirto');

UPDATE toimenpidekoodi
   SET suunnitteluyksikko = 'jm'
 WHERE taso = 4
   AND nimi in ('Meluesteiden pesu');

UPDATE toimenpidekoodi
   SET suunnitteluyksikko = 'tiekm'
 WHERE taso = 4
   AND nimi in ('Katupölynsidonta'); -- Tässä yksikkö on h

UPDATE toimenpidekoodi
   SET suunnitteluyksikko = 'm3'
 WHERE taso = 4
   AND nimi in ('Maakivien (>1m3) poisto');

UPDATE toimenpidekoodi
   SET suunnitteluyksikko = yksikko
 WHERE taso = 4
   AND suunnitteluyksikko IS NULL;

UPDATE toimenpidekoodi
   SET suunnitteluyksikko = ''
 WHERE taso = 4
   AND nimi in ('Levähdysalueen puhtaanapito');

-- Käyttöliittymän kautta lisättyjen tehtävien puuttuvien yksiköiden päivitys
UPDATE toimenpidekoodi
   SET yksikko = 'kpl'
 WHERE taso = 4
   AND nimi in ('Valvontakameratolppien puhdistus/tarkistus keväisin');

-- Tehtäviin ei suunnitella määriä tehtävä- ja määräluettelossa
UPDATE toimenpidekoodi
   SET suunnitteluyksikko = null
 WHERE nimi like ('%Äkillinen hoitotyö%');
UPDATE toimenpidekoodi
   SET suunnitteluyksikko = null
 WHERE nimi like ('%Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen%');
UPDATE toimenpidekoodi
   SET suunnitteluyksikko = 'h', yksikko = 'h'
 WHERE nimi = 'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon';
UPDATE toimenpidekoodi
   SET suunnitteluyksikko = null
 WHERE nimi IN ('Vesakonraivaus N1','Vesakonraivaus N2','Vesakonraivaus N3','Nurmetuksen hoito / niitto N1','Nurmetuksen hoito / niitto N2','Nurmetuksen hoito / niitto N3');

-- Siivotaan suunnitteluyksikkö. Kun arvo on null, ei Tehtävä- ja määräluettelossa suunnitella tehtävän määriä.
UPDATE toimenpidekoodi
   SET suunnitteluyksikko = null
 WHERE suunnitteluyksikko = ''
    OR suunnitteluyksikko = '-';

-- Korjaa yksiköt
UPDATE toimenpidekoodi SET yksikko = 'm3' WHERE nimi = 'Maakivien (>1m3) poisto';
UPDATE toimenpidekoodi SET yksikko = 'm2', suunnitteluyksikko = 'm2' WHERE nimi = 'Kalliokynsien louhinta ojituksen yhteydessä';
UPDATE toimenpidekoodi SET yksikko = 'kaistakm', suunnitteluyksikko = 'kaistakm' WHERE nimi IN ('Is rampit', 'Is ohituskaistat', 'Ib ohituskaistat', 'Ib rampit');

-- Yhtenäistetään tapa merkitä yksikkö
UPDATE toimenpidekoodi SET yksikko = 'tonni' WHERE yksikko IN ('t', 'tonnia');
UPDATE toimenpidekoodi SET suunnitteluyksikko = 'tonni' WHERE suunnitteluyksikko IN ('t', 'tonnia');

-- Poistetaan isolla kirjoitettu, ylimääräinen versio (Puhtaanapito). Kantaan jää pienellä kirjoitettu.
DELETE from tehtavaryhma where nimi like ('%Tie-, levähdys- ja liitännäisalueiden Puhtaanapito ja kalusteiden hoito%');

-- Päivitetään hoitoluokkatiedot (aluetiedot) vastaamaan exceliä. Ryhmitelty helpomman ylläpidettävyyden vuoksi
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Ise 2-ajorat.', 'Ise 1-ajorat.', 'Ise ohituskaistat', 'Ise rampit');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Is 2-ajorat.', 'Is 1-ajorat.', 'Is ohituskaistat', 'Is rampit');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Ib 2-ajorat.', 'Ib 1-ajorat.', 'Ib ohituskaistat', 'Ib rampit');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Ic 2-ajorat', 'Ic 1-ajorat', 'Ic ohituskaistat', 'Ic rampit');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('II', 'III', 'Kävely- ja pyöräilyväylien laatukäytävät', 'K1', 'K2');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Suolaus','Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)','Liukkaudentorjunta hiekoituksella','Ennalta arvaamattomien kuljetusten avustaminen');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Levähdys- ja pysäköimisalueet', 'Talvihoidon kohotettu laatu');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Pysäkkikatosten puhdistus','Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt', 'Portaiden talvihoito', 'Lisäkalustovalmius/-käyttö');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Valvontakameratolppien puhdistus/tarkistus keväisin', 'Reunapaalujen kp (uusien)');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Nopeusvalvontakameroiden tolppien ja laitekoteloiden puhdistus', 'Reunapaalujen kunnossapito (oikominen, pesu, yms.)', 'Porttaalien tarkastus ja huolto');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset', 'Meluesteiden siisteydestä huolehtiminen ja pienten vaurioiden korjaus');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)', 'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Sorateiden pinnan hoito, hoitoluokka I', 'Sorateiden pinnan hoito, hoitoluokka II', 'Sorateiden pinnan hoito, hoitoluokka III', 'Sorapintaisten kävely- ja pyöräilyväylienhoito');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Sorateiden pölynsidonta (materiaali)','Oja- ja luiskameteriaalin käyttö kulutuskerrokseeen','Liikenteen varmistaminen kelirikkokohteessa');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Nopeusnäyttötaulun huolto ja ylläpito, siirto kohteiden välillä, akkujen lataaminen ja näyttötaulujen varastointi');
UPDATE toimenpidekoodi SET aluetieto = TRUE WHERE nimi IN ('Reunantäyttö');

DELETE from tehtavaryhma WHERE nimi = 'Kaiteet, aidat ja kiveykset (U)'; -- Väärin kirjoitettu versio, poistetaan roikkumasta

-- Muutokset tehtäviin lokakuussa 2021
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2020 WHERE nimi = 'Oja- ja luiskameteriaalin käyttö kulutuskerrokseeen';
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2020 WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.';
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2020 WHERE nimi = 'Hoito- ja korjaustöiden pientarvikevarasto';
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2020 WHERE nimi = 'Toimitilat sähkö-, lämmitys-, vesi-, jäte-, siivous-, huolto-, korjaus- ja vakuutus- yms. kuluineen';
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2020 WHERE nimi = 'Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut';
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2020 WHERE nimi = 'Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)';
-- Tallennetaanko tieto vain tehtäväryhmään linkitettynä vai tarvitaanko sille tehtävä?

-- Muutokset tehtäviin lokakuussa 2022
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2021 WHERE nimi = 'Oja- ja luiskamateriaalien käyttö kulutuskerrokseen';

-- Tehtäväryhmien järjestysnumeroita. Muutoksia vuodelle 2022 + Kuluraportti ei salli samaa numeroa monelle tehtäväryhmälle.
UPDATE tehtavaryhma SET jarjestys = 102 WHERE nimi = 'Kuumapäällyste (Y1)';
UPDATE tehtavaryhma SET jarjestys = 103 WHERE nimi = 'KT-Valu (Y3)';
UPDATE tehtavaryhma SET jarjestys = 104 WHERE nimi = 'Kylmäpäällyste (Y2)';
UPDATE tehtavaryhma SET jarjestys = 105 WHERE nimi = 'Käsipaikkaus pikapaikkausmassalla (Y4)';
UPDATE tehtavaryhma SET jarjestys = 106 WHERE nimi = 'Puhallus-SIP (Y5)';
UPDATE tehtavaryhma SET jarjestys = 107 WHERE nimi = 'Saumojen juottaminen bitumilla (Y6)';
UPDATE tehtavaryhma SET jarjestys = 108 WHERE nimi = 'Valu (Y7)';
UPDATE tehtavaryhma SET jarjestys = 109 WHERE nimi = 'Siltapäällysteet (H)';
UPDATE tehtavaryhma SET jarjestys = 135 WHERE nimi = 'Äkilliset hoitotyöt, Talvihoito (T1)';
UPDATE tehtavaryhma SET jarjestys = 136 WHERE nimi = 'Äkilliset hoitotyöt, Liikenneympäristön hoito (T1)';
UPDATE tehtavaryhma SET jarjestys = 137 WHERE nimi = 'Äkilliset hoitotyöt, Soratiet (T1)';
UPDATE tehtavaryhma SET jarjestys = 138 WHERE nimi = 'Vahinkojen korjaukset, Talvihoito (T2)';
UPDATE tehtavaryhma SET jarjestys = 139 WHERE nimi = 'Vahinkojen korjaukset, Liikenneympäristön hoito (T2)';
UPDATE tehtavaryhma SET jarjestys = 140 WHERE nimi = 'Vahinkojen korjaukset, Soratiet (T2)';
UPDATE tehtavaryhma SET jarjestys = 141 WHERE nimi = 'Rummut, päällystetiet (R)';
UPDATE tehtavaryhma SET jarjestys = 142 WHERE nimi = 'Rummut, soratiet (S)';
UPDATE tehtavaryhma SET jarjestys = 143 WHERE nimi = 'Avo-ojitus, päällystetyt tiet (X)';
UPDATE tehtavaryhma SET jarjestys = 144 WHERE nimi = 'Avo-ojitus, soratiet (Z)';
UPDATE tehtavaryhma SET jarjestys = 152 WHERE nimi = 'Muut, MHU ylläpito (F)';
UPDATE tehtavaryhma SET jarjestys = 153 WHERE nimi = 'Muut, liikenneympäristön hoito (F)';

-- Tehtäväryhmien otsikkomuutoksia 2022, muutokset näkyvät myös aiemmille urakoille
UPDATE tehtavaryhma SET otsikko = '2.8 LIIKENNEYMPÄRISTÖN HOITO / Siltojen ja laitureiden hoito' WHERE otsikko = '2.9 LIIKENNEYMPÄRISTÖN HOITO / Siltojen ja laitureiden hoito';
UPDATE tehtavaryhma SET otsikko = '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällystettyjen teiden sorapientareen kunnossapito' WHERE otsikko = '2.8 LIIKENNEYMPÄRISTÖN HOITO / Päällystettyjen teiden sorapientareen kunnossapito';
UPDATE tehtavaryhma SET otsikko = '4 PÄÄLLYSTEIDEN PAIKKAUS' WHERE otsikko = '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus';
UPDATE tehtavaryhma SET otsikko = '5 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA' WHERE otsikko = '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA';
UPDATE tehtavaryhma SET otsikko = '6.1 YLLÄPITO / Rumpujen uusiminen' WHERE nimi IN ('Rumpujen kunnossapito ja uusiminen', 'Välitaso MHU Ylläpito', 'Rumpujen kunnossapito ja uusiminen (päällystetty tie)', 'Rumpujen kunnossapito ja uusiminen (soratie)', 'Rummut, päällystetiet (R)', 'Rummut, soratiet (S)');
UPDATE tehtavaryhma SET otsikko = '6.2 YLLÄPITO / Avo-ojien kunnossapito' WHERE nimi IN ('MHU Ylläpito', 'Välitaso MHU Ylläpito', 'Ojitus', 'Avo-ojitus, soratiet (Z)', 'Avo-ojitus, päällystetyt tiet (X)');
UPDATE tehtavaryhma SET otsikko = '7 KORVAUSINVESTOINTI' WHERE otsikko = '5 KORJAAMINEN';
UPDATE tehtavaryhma SET otsikko = '8 MUUTA' WHERE otsikko = '6 MUUTA';

-- Pohjavesisuojaukset kuuluvat Muut-otsikon alle
UPDATE tehtavaryhma
   SET otsikko = '8 MUUTA'
 WHERE nimi in (
                'Muut, liikenneympäristön hoito (F)',
                'Muut, MHU ylläpito (F)',
                'Pohjavesisuojaukset'
     );

-- Siirrä tehtävien paikkaa 2022
UPDATE toimenpidekoodi SET jarjestys = 1270 WHERE nimi = 'Maakivien (>1m3) poisto';
UPDATE toimenpidekoodi SET jarjestys = 1341 WHERE nimi = 'AB-paikkaus levittäjällä';
UPDATE toimenpidekoodi SET jarjestys = 1341 WHERE nimi = 'PAB-paikkaus levittäjällä';
UPDATE toimenpidekoodi SET jarjestys = 1342 WHERE nimi = 'PAB-paikkaus käsin';
UPDATE toimenpidekoodi SET jarjestys = 1343 WHERE nimi = 'KT-valuasfalttipaikkaus, kesä';
UPDATE toimenpidekoodi SET jarjestys = 1343 WHERE nimi = 'KT-valuasfalttipaikkaus, talvi';
UPDATE toimenpidekoodi SET jarjestys = 1344 WHERE nimi = 'KT-reikävaluasfalttipaikkaus';
UPDATE toimenpidekoodi SET jarjestys = 1344 WHERE nimi = 'Käsin tehtävät paikkaukset pikapaikkausmassalla';
UPDATE toimenpidekoodi SET jarjestys = 1344 WHERE nimi = 'Sirotepuhalluspaikkaus (SIPU)';
UPDATE toimenpidekoodi SET jarjestys = 1345 WHERE nimi = 'Kannukaatosaumaus';
UPDATE toimenpidekoodi SET jarjestys = 1345 WHERE nimi = 'KT-valuasfalttisaumaus';
UPDATE toimenpidekoodi SET jarjestys = 1345 WHERE nimi = 'Sillan päällysteen halkeaman avarrussaumaus';
UPDATE toimenpidekoodi SET jarjestys = 1346 WHERE nimi = 'Sillan kannen päällysteen päätysauman korjaukset';
UPDATE toimenpidekoodi SET jarjestys = 1347 WHERE nimi = 'Reunapalkin ja päällysteen väl. sauman tiivistäminen';
UPDATE toimenpidekoodi SET jarjestys = 1347 WHERE nimi = 'Reunapalkin liikuntasauman tiivistäminen';
UPDATE toimenpidekoodi SET jarjestys = 1347 WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt';
UPDATE toimenpidekoodi SET jarjestys = 1371 WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1372 WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1373 WHERE nimi = 'Rumpujen korjaus ja uusiminen  Ø ≤ 600 mm, päällystetyt tiet.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1374 WHERE nimi = 'Rumpujen korjaus ja uusiminen  Ø > 600  ≤ 800 mm, päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1375 WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1376 WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1377 WHERE nimi = 'Rumpujen korjaus ja uusiminen  Ø ≤ 600 mm, soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 1378 WHERE nimi = 'Rumpujen korjaus ja uusiminen  Ø > 600  ≤ 800 mm, soratiet' AND tehtavaryhma IS NOT NULL;

-- Ujutetaan myös vanhat päällystepaikkaukset vuoden 2022 tehtävä- ja määräluettelon mukaiseen sijaintiin
UPDATE toimenpidekoodi SET jarjestys = 1341 WHERE nimi = 'Kuumapäällyste';
UPDATE toimenpidekoodi SET jarjestys = 1341 WHERE nimi = 'Kylmäpäällyste ml. SOP';
UPDATE toimenpidekoodi SET jarjestys = 1342 WHERE nimi = 'Puhallus-SIP';
UPDATE toimenpidekoodi SET jarjestys = 1342 WHERE nimi = 'Saumojen juottaminen bitumilla';
UPDATE toimenpidekoodi SET jarjestys = 1343 WHERE nimi = 'Massasaumaus';
UPDATE toimenpidekoodi SET jarjestys = 1344 WHERE nimi = 'Konetiivistetty valuasfaltti';
UPDATE toimenpidekoodi SET jarjestys = 1344 WHERE nimi = 'Valuasfaltti';
UPDATE toimenpidekoodi SET jarjestys = 1341 WHERE nimi = 'Päällysteiden paikkaus, kylmäpäällyste';
UPDATE toimenpidekoodi SET jarjestys = 1342 WHERE nimi = 'Päällysteiden paikkaus -saumojen juottaminen bitumilla';
UPDATE toimenpidekoodi SET jarjestys = 1343 WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty valuasvaltti';

-- Korjaa virhe HJ-urakkatehtävien voimassaolon loppuvuodessa
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2018 WHERE voimassaolo_alkuvuosi = 2018 AND voimassaolo_loppuvuosi = 2019 AND nimi != 'Päällysteiden paikkaus, kylmäpäällyste';

-- Reunapaalujen kunnossapito suunnitellaan juoksumetreinä 2022 alkaneista urakoista eteenpäin
-- Laitetaan myös uudelle ja vanhalle käsin lisättävä määrä päälle
UPDATE toimenpidekoodi SET voimassaolo_loppuvuosi = 2021, kasin_lisattava_maara = TRUE WHERE nimi = 'Reunapaalujen kp (uusien)' AND voimassaolo_loppuvuosi IS NULL AND yksikko = 'tiekm';
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, suunnitteluyksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen,
                             voimassaolo_alkuvuosi, hinnoittelu, aluetieto, kasin_lisattava_maara)
VALUES ('Reunapaalujen kunnossapito', (select id from tehtavaryhma where nimi = 'Liikennemerkit ja liikenteenohjauslaitteet (L)'), 'jkm', 'jkm', 370, NULL,
        (select id from toimenpidekoodi where koodi = '23116'), current_timestamp,
        (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE, 2022, '{muutoshintainen}', TRUE, TRUE)
    ON CONFLICT(nimi, emo) DO NOTHING;


-- Viilataan tietokannan nimiä samaksi kuin tehtävä- ja määräluettelon tekstissä
UPDATE toimenpidekoodi SET nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' WHERE nimi = 'Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and yksikko = 'm2';
UPDATE toimenpidekoodi SET nimi = 'Nopeusvalvontakameroiden tolppien ja laitekoteloiden puhdistus' WHERE nimi = 'Valvontakameratolppien puhdistus/tarkistus keväisin' and yksikko = 'kpl';
UPDATE toimenpidekoodi SET nimi = 'Meluesteiden siisteydestä huolehtiminen' WHERE nimi = 'Meluesteiden pesu' and suunnitteluyksikko = 'jm';

-- Viilataan käsinkirjausmahdollisuutta
-- Ei nykyisellään aluetietoja
UPDATE toimenpidekoodi SET kasin_lisattava_maara = TRUE, "raportoi-tehtava?" = TRUE WHERE nimi IN ('Nopeusnäyttötaulujen ylläpito, käyttö ja siirto',
                                                                                                   'Nopeusnäyttötaulun hankinta',
                                                                                                   'Meluesteiden siisteydestä huolehtiminen');
-- Aluetietoja
UPDATE toimenpidekoodi SET kasin_lisattava_maara = TRUE, "raportoi-tehtava?" = TRUE  WHERE nimi IN ('Nopeusvalvontakameroiden tolppien ja laitekoteloiden puhdistus',
                                                                                                    'Reunapaalujen kunnossapito',
                                                                                                    'Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset',
                                                                                                    'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu',
                                                                                                    'Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)',
                                                                                                    'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)');

-- Päällystettyjen teiden sorapientareen täyttö-tehtävää ei käytetä MH-urakoissa
UPDATE toimenpidekoodi
   SET voimassaolo_loppuvuosi = 2018 -- Hyvinkään HJU:n toteuman jäävät ohjautumaan tälle tehtävälle
 WHERE nimi = 'Päällystettyjen teiden sorapientareen täyttö' AND tehtavaryhma IS NOT NULL;

-- Reunantäyttö on tehtävä, joka korvaa Päällystettyjen teiden sorapientareen täyttö-tehtävän MH-urakoissa.
-- Siitä on kaksi versiota: käsin kirjattava (yksikkö tonni) ja koneellisesti kirjattava (yksikkö jkm).
-- Materiaalikirjaukset on nykyään ohjeistettu tehtäväksi materiaalitoteumana myös käsin kirjattaessa.
UPDATE toimenpidekoodi
   SET api_seuranta = FALSE, api_tunnus = NULL, voimassaolo_alkuvuosi = 2019
 WHERE nimi = 'Reunantäyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi
   SET api_seuranta = TRUE, api_tunnus = 7067, voimassaolo_alkuvuosi = 2019
 WHERE nimi = 'Reunantäyttö km' AND tehtavaryhma IS NOT NULL;
