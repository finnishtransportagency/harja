-- Tehtäväryhmät täytyy ajaa R-migraatiossa, koska toimenpidekoodit, joihin ne kiinteästi liittyvät, ajetaan R-migraatiossa.
-- Osa lauseista päivittää tehtäväryhmän toimenpidekoodiriveille.
-- Kun tehtäväryhmistä tulee uusi versio. Huomioi että tämän skriptin lopussa päivitetään versioksi 1. Uusi versio on sitten 2. Tehtäväryhmien ja hierarkian käsittely täytynee silloin miettiä muutenkin vähän uusiksi.

------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- TEHTÄVÄRYHMÄT raportointia ja laskutusta varten. Ryhmittelee tehtävät ja lajittelee tehtäväryhmät. Käyttöliittymäjärjestystä vasten ui-taso ------------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

-- Aja aina R_Toimenpidekoodit ennen näitä inserttejä


INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'Talvihoito',	NULL,	'ylataso',	1, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'Välitaso Talvihoito',	(select id from tehtavaryhma where nimi = 'Talvihoito'),	'valitaso',	1, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Talvihoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'TALVIHOITO (A)',	(select id from tehtavaryhma where nimi = 'Välitaso Talvihoito'),	'alataso',	1, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Välitaso Talvihoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'Muut talvihoitotyöt',	(select id from tehtavaryhma where nimi = 'Talvihoito'),	'valitaso',	22, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Talvihoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'Liukkaudentorjunta',	(select id from tehtavaryhma where nimi = 'Talvihoito'),	'valitaso',	25, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Talvihoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'TALVISUOLA (B1)',	(select id from tehtavaryhma where nimi = 'Liukkaudentorjunta'),	'alataso',	25, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Liukkaudentorjunta');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.1 LIIKENNEYMPÄRISTÖN HOITO',	'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen',	NULL,	'ylataso',	35, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.1 LIIKENNEYMPÄRISTÖN HOITO',	'Välitaso Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen',	(select id from tehtavaryhma where nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen'),	'valitaso',	35, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.1 LIIKENNEYMPÄRISTÖN HOITO',	'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)',	(select id from tehtavaryhma where nimi = 'Välitaso Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen'),	'alataso',	35, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Välitaso Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.1 LIIKENNEYMPÄRISTÖN HOITO',	'Vakiokokoiset liikennemerkit',	(select id from tehtavaryhma where nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen'),	'valitaso',	39, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.1 LIIKENNEYMPÄRISTÖN HOITO',	'Opastustaulut ja viitat',	(select id from tehtavaryhma where nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen'),	'valitaso',	42, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.2 LIIKENNEYMPÄRISTÖN HOITO',	'Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito',	NULL,	'ylataso',	48, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.2 LIIKENNEYMPÄRISTÖN HOITO',	'Välitaso Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito',	(select id from tehtavaryhma where nimi = 'Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito'),	'valitaso',	48, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.2 LIIKENNEYMPÄRISTÖN HOITO',	'PUHTAANAPITO (P)',	(select id from tehtavaryhma where nimi = 'Välitaso Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito'),	'alataso',	48, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Välitaso Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.3 LIIKENNEYMPÄRISTÖN HOITO',	'Viheralueiden hoito',	NULL,	'ylataso',	62, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.3 LIIKENNEYMPÄRISTÖN HOITO',	'Vesakon raivaus ja runkopuun poisto',	(select id from tehtavaryhma where nimi = 'Viheralueiden hoito'),	'valitaso',	62, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Viheralueiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.3 LIIKENNEYMPÄRISTÖN HOITO',	'VESAKONRAIVAUKSET JA PUUN POISTO (V)',	(select id from tehtavaryhma where nimi = 'Vesakon raivaus ja runkopuun poisto'),	'alataso',	62, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Vesakon raivaus ja runkopuun poisto');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.3 LIIKENNEYMPÄRISTÖN HOITO',	'Nurmetus, puiden ja pensaiden hoito, muut viheralueiden hoidon työt',	(select id from tehtavaryhma where nimi = 'Viheralueiden hoito'),	'valitaso',	65, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Viheralueiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.3 LIIKENNEYMPÄRISTÖN HOITO',	'NURMETUKSET JA MUUT VIHERTYÖT (N)',	(select id from tehtavaryhma where nimi = 'Nurmetus, puiden ja pensaiden hoito, muut viheralueiden hoidon työt'),	'alataso',	65, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Nurmetus, puiden ja pensaiden hoito, muut viheralueiden hoidon työt');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.4 LIIKENNEYMPÄRISTÖN HOITO',	'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito',	NULL,	'ylataso',	79, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.4 LIIKENNEYMPÄRISTÖN HOITO',	'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito, rumpujen tarkastus ja hoito',	(select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito'),	'valitaso',	79, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.4 LIIKENNEYMPÄRISTÖN HOITO',	'KUIVATUSJÄRJESTELMÄT (K)',	(select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito, rumpujen tarkastus ja hoito'),	'alataso',	79, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi = 'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito, rumpujen tarkastus ja hoito');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.5 LIIKENNEYMPÄRISTÖN HOITO',	'Rumpujen kunnossapito ja uusiminen',	NULL,	'ylataso',	86, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.5 LIIKENNEYMPÄRISTÖN HOITO',	'Rumpujen kunnossapito ja uusiminen (päällystetty tie)',	(select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen'),	'valitaso',	86, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.5 LIIKENNEYMPÄRISTÖN HOITO',	'RUMMUT, PÄÄLLYSTETIET (R)',	(select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen (päällystetty tie)'),	'alataso',	86, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen (päällystetty tie)');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.5 LIIKENNEYMPÄRISTÖN HOITO',	'Rumpujen kunnossapito ja uusiminen (soratie)',	(select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen'),	'valitaso',	90, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = 	(select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.5 LIIKENNEYMPÄRISTÖN HOITO',	'RUMMUT, SORATIET (S)',	(select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen (soratie)'),	'alataso',	90, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Rumpujen kunnossapito ja uusiminen (soratie)');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.6 LIIKENNEYMPÄRISTÖN HOITO',	'Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito',	NULL,	'ylataso',	97, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.6 LIIKENNEYMPÄRISTÖN HOITO',	'Välitaso Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito',	(select id from tehtavaryhma where nimi =  'Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito'),	'valitaso',	97, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.6 LIIKENNEYMPÄRISTÖN HOITO',	'KAITEET, AIDAT JA KIVEYKSET (U)',	(select id from tehtavaryhma where nimi =  'Välitaso Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito'),	'alataso',	97, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO',	'Päällysteiden paikkaus',	NULL,	'ylataso',	102, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO',	'Välitaso Päällysteiden paikkaus',	(select id from tehtavaryhma where nimi =  'Päällysteiden paikkaus'),	'valitaso',	102, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Päällysteiden paikkaus');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO',	'KUUMAPÄÄLLYSTE (Y1)',	(select id from tehtavaryhma where nimi =  'Välitaso Päällysteiden paikkaus'),	'alataso',	102, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Päällysteiden paikkaus');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO',	'Sillan päällysteen korjaus',	(select id from tehtavaryhma where nimi =  'Päällysteiden paikkaus'),	'valitaso',	109, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Päällysteiden paikkaus');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO',	'SILTAPÄÄLLYSTEET (H)',	(select id from tehtavaryhma where nimi =  'Sillan päällysteen korjaus'),	'alataso',	109, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Sillan päällysteen korjaus');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.8 LIIKENNEYMPÄRISTÖN HOITO',	'Päällystettyjen teiden sorapientareen kunnossapito',	NULL,	'ylataso',	116, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.8 LIIKENNEYMPÄRISTÖN HOITO',	'Välitaso Päällystettyjen teiden sorapientareen kunnossapito',	(select id from tehtavaryhma where nimi =  'Päällystettyjen teiden sorapientareen kunnossapito'),	'valitaso',	116, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Päällystettyjen teiden sorapientareen kunnossapito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.8 LIIKENNEYMPÄRISTÖN HOITO',	'SORAPIENTAREET (O)',	(select id from tehtavaryhma where nimi =  'Välitaso Päällystettyjen teiden sorapientareen kunnossapito'),	'alataso',	116, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Päällystettyjen teiden sorapientareen kunnossapito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.9 LIIKENNEYMPÄRISTÖN HOITO',	'Siltojen ja laitureiden hoito',	NULL,	'ylataso',	121, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.9 LIIKENNEYMPÄRISTÖN HOITO',	'Välitaso Siltojen ja laitureiden hoito',	(select id from tehtavaryhma where nimi =  'Siltojen ja laitureiden hoito'),	'valitaso',	121, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Siltojen ja laitureiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.9 LIIKENNEYMPÄRISTÖN HOITO',	'SILLAT JA LAITURIT (I)',	(select id from tehtavaryhma where nimi =  'Välitaso Siltojen ja laitureiden hoito'),	'alataso',	121, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Siltojen ja laitureiden hoito');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'Sorateiden hoito',	NULL,	'ylataso',	124, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'Välitaso Sorateiden hoito',	(select id from tehtavaryhma where nimi =  'Sorateiden hoito'),	'valitaso',	124, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Sorateiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'SORATEIDEN HOITO (C)',	(select id from tehtavaryhma where nimi =  'Välitaso Sorateiden hoito'),	'alataso',	128, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Sorateiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'Sorateiden pinnan hoito',	(select id from tehtavaryhma where nimi =  'Sorateiden hoito'),	'valitaso',	124, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Sorateiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'AVO-OJITUS, SORATIET (Z)',	(select id from tehtavaryhma where nimi =  'Sorateiden pinnan hoito'),	'alataso',	124, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Sorateiden pinnan hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'Muut sorateiden hoidon tehtävät',	(select id from tehtavaryhma where nimi =  'Sorateiden hoito'),	'valitaso',	129, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Sorateiden hoito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'SORASTUS (M)',	(select id from tehtavaryhma where nimi =  'Muut sorateiden hoidon tehtävät'),	'alataso',	129, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Muut sorateiden hoidon tehtävät');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'MHU Ylläpito',	NULL,	'ylataso',	139, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Välitaso MHU Ylläpito',	(select id from tehtavaryhma where nimi =  'MHU Ylläpito'),	'valitaso',	139, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'MHU Ylläpito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'RKR-KORJAUS (Q)',	(select id from tehtavaryhma where nimi =  'Välitaso MHU Ylläpito'),	'alataso',	139, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = 	(select id from tehtavaryhma where nimi =  'Välitaso MHU Ylläpito');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Ojitus',	(select id from tehtavaryhma where nimi =  'MHU Ylläpito'),	'valitaso',	139, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'MHU Ylläpito');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)',	(select id from tehtavaryhma where nimi =  'Ojitus'),	'alataso',	139, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Ojitus');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'AVO-OJITUS, SORATIET (Z)',	(select id from tehtavaryhma where nimi =  'Ojitus'),	'alataso',	142, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Ojitus');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Pohjavesisuojaukset',	NULL,	'ylataso',	152, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'Välitaso Pohjavesisuojaukset',	(select id from tehtavaryhma where nimi =  'Pohjavesisuojaukset'),	'valitaso',	152, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Pohjavesisuojaukset');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '5 KORJAAMINEN',	'MUUT (F)',	(select id from tehtavaryhma where nimi =  'Välitaso Pohjavesisuojaukset'),	'alataso',	152, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Pohjavesisuojaukset');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Muut liik.ymp.hoitosasiat',	NULL,	'ylataso',	155, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Välitaso Muut liik.ymp.hoitosasiat',	(select id from tehtavaryhma where nimi =  'Muut liik.ymp.hoitosasiat'),	'valitaso',	155, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = 	(select id from tehtavaryhma where nimi =  'Muut liik.ymp.hoitosasiat');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'ELY-RAHOITTEISET (E)',	(select id from tehtavaryhma where nimi =  'Välitaso Muut liik.ymp.hoitosasiat'),	'alataso',	155, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Muut liik.ymp.hoitosasiat');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Johto- ja hallintokorvaukseen sisältyvät tehtävät',	NULL,	'ylataso',	161, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Välitaso Johto- ja hallintokorvaukseen sisältyvät tehtävät',	(select id from tehtavaryhma where nimi =  'Johto- ja hallintokorvaukseen sisältyvät tehtävät'),	'valitaso',	161, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Johto- ja hallintokorvaukseen sisältyvät tehtävät');

INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'JOHTO- JA HALLINTOKORVAUS (J)',	(select id from tehtavaryhma where nimi =  'Välitaso Johto- ja hallintokorvaukseen sisältyvät tehtävät'),	'alataso',	161, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Johto- ja hallintokorvaukseen sisältyvät tehtävät');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'HOIDONJOHTOPALKKIO (G)',	(select id from tehtavaryhma where nimi =  'Välitaso Johto- ja hallintokorvaukseen sisältyvät tehtävät'),	'alataso',	161, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Johto- ja hallintokorvaukseen sisältyvät tehtävät');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Erillishankinnat erillishinnoin',	NULL,	'valitaso',	165, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE) ON CONFLICT DO NOTHING;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'Välitaso Erillishankinnat erillishinnoin',	(select id from tehtavaryhma where nimi =  'Erillishankinnat erillishinnoin'),	'valitaso',	165, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Erillishankinnat erillishinnoin');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'ERILLISHANKINNAT (W)',	(select id from tehtavaryhma where nimi =  'Välitaso Erillishankinnat erillishinnoin'),	'alataso',	165, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE set emo = (select id from tehtavaryhma where nimi =  'Välitaso Erillishankinnat erillishinnoin');



-- Uuden tehtävä- ja määräluetteloversion johdosta tarvitut tehtäväryhmät
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'KFO,NAFO (B2)',	(select id from tehtavaryhma where nimi = 'Liukkaudentorjunta'),	'alataso',	26, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Liukkaudentorjunta');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '1.0 TALVIHOITO',	'HIEKOITUS (B3)',	(select id from tehtavaryhma where nimi = 'Liukkaudentorjunta'),	'alataso',	27, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Liukkaudentorjunta');
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'KYLMÄPÄÄLLYSTE (Y2)',	(select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'),	'alataso',	103, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'), jarjestys = 103;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'PUHALLUS-SIP (Y3)',	(select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'),	'alataso',	104, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'), jarjestys = 104;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'SAUMOJEN JUOTTAMINEN BITUMILLA (Y4)',	(select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'),	'alataso',	105, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'), jarjestys = 105;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'MASSASAUMAUS (Y5)',	(select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'),	'alataso',	105, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'), jarjestys = 105;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'KT-VALU (Y6)',	(select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'),	'alataso',	105, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'), jarjestys = 105;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '2.7 LIIKENNEYMPÄRISTÖN HOITO / Päällysteiden paikkaus',	'VALU (Y7)',	(select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'),	'alataso',	105, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Päällysteiden paikkaus'), jarjestys = 105;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '3 SORATEIDEN HOITO',	'KESÄSUOLA, MATERIAALI (D)',	(select id from tehtavaryhma where nimi = 'Välitaso Sorateiden hoito'),	'alataso',	129, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Sorateiden hoito'), jarjestys = 129;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'ÄKILLISET HOITOTYÖT, TALVIHOITO (T1)',	(select id from tehtavaryhma where nimi = 'Välitaso Talvihoito'),	'alataso',	135, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Talvihoito'), jarjestys = 135;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'ÄKILLISET HOITOTYÖT, LIIKENNEYMPÄRISTÖN HOITO (T1)',	(select id from tehtavaryhma where nimi = 'Välitaso Muut liik.ymp.hoitosasiat'),	'alataso',	135, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = 	(select id from tehtavaryhma where nimi = 'Välitaso Muut liik.ymp.hoitosasiat'), jarjestys = 135;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'ÄKILLISET HOITOTYÖT, SORATIET (T1)',	(select id from tehtavaryhma where nimi = 'SORATEIDEN HOITO (C)'),	'alataso',	135, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Sorateiden hoito'), jarjestys = 135;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'VAHINKOJEN KORJAUKSET, TALVIHOITO (T2)',	(select id from tehtavaryhma where nimi = 'Välitaso Talvihoito'),	'alataso',	135, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Talvihoito'), jarjestys = 135;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'VAHINKOJEN KORJAUKSET, LIIKENNEYMPÄRISTÖN HOITO (T2)',	(select id from tehtavaryhma where nimi = 'Välitaso Muut liik.ymp.hoitosasiat'),	'alataso',	135, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = 	(select id from tehtavaryhma where nimi = 'Välitaso Muut liik.ymp.hoitosasiat'), jarjestys = 135;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA',	'VAHINKOJEN KORJAUKSET, SORATIET (T2)',	(select id from tehtavaryhma where nimi = 'SORATEIDEN HOITO (C)'),	'alataso',	135, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Sorateiden hoito'), jarjestys = 135;
INSERT into tehtavaryhma (otsikko, nimi, emo, tyyppi, jarjestys, luotu, luoja, nakyva) VALUES ( '6 MUUTA',	'TILAAJAN RAHAVARAUS (T3)',	(select id from tehtavaryhma where nimi = 'Välitaso Muut liik.ymp.hoitosasiat'),	'alataso',	159, current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), FALSE)
ON CONFLICT (nimi) DO UPDATE SET emo = (select id from tehtavaryhma where nimi = 'Välitaso Muut liik.ymp.hoitosasiat'), jarjestys = 159;


------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Uudet tehtävät. Näille ei löydynyt vastaavuutta vanhoista tehtävistä. -----------------------------------------------------------------------------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

-- Aja toimenpidekoodit ja tehtäväryhmät ennen näitä inserttejä
-- Tehtävät on järjestetty (toimenpidekoodi.jarjestys) vuoden 2019 tehtävä- ja määräluettelon mukaan (sopimuksen liite). Siksi saman toimenpiteen alle kuuluvia tehtäviä on useassa osiossa.
-- Toivottavasti jatkossa sopimuksen liite on toimenpidekoodi- ja tehtäväryhmätaulujen todellisen hierarkian mukainen.

-- Talvihoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise 2-ajorat.', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'tiekm',	1, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 1;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise 1-ajorat.', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'tiekm',	2, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 2;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise ohituskaistat', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kaistakm',	3, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 3;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ise rampit', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kaistakm',	4, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 4;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic 2-ajorat', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'tiekm',	13, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 13;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic 1-ajorat', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'tiekm',	14, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 14;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic ohituskaistat', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kaistakm',	15, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 15;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ic rampit', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kaistakm',	16, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 16;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kävely- ja pyöräilyväylien laatukäytävät', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'tiekm',	19, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 19;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Levähdys- ja pysäköimisalueet', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kpl',	22, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 22;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muiden alueiden talvihoito', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'',	23, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 23;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Talvihoidon kohotettu laatu', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'tiekm',	24, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 24;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Ennalta arvaamattomien kuljetusten avustaminen', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'tonnia',	28, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 28;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Pysäkkikatosten puhdistus', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kpl',	29, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 29;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kpl',	30, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 30;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Portaiden talvihoito', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kpl',	31, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 31;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Lisäkalustovalmius/-käyttö', (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	'kpl',	32, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'), jarjestys = 32;
-- Liikenneympäristön hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)', (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	'',	35, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'), jarjestys = 35;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoitotyöt', (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	NULL,	47, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'), jarjestys = 47;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Tie- ja levähdysalueiden kalusteiden kunnossapito ja hoito', (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	NULL,	49, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'), jarjestys = 49;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset', (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	'kpl',	50, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'), jarjestys = 50;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hiekoitushiekan ja irtoainesten poisto', (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'	),	NULL,	52, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'), jarjestys = 52;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut tie- levähdys- ja liitännäisalueiden puhtaanpitoon ja kalusteiden hoitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	'',	57, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'), jarjestys = 57;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Erillisten hoito-ohjeiden mukaiset vihertyöt', (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	NULL,	72, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'), jarjestys = 72;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Erillisten hoito-ohjeiden mukaiset vihertyöt, uudet alueet', (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	NULL,	73, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'), jarjestys = 73;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Vesistöpenkereiden hoito', (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	NULL,	75, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'), jarjestys = 75;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Tiekohtaiset maisemanhoitoprojektit', (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	NULL,	76, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'), jarjestys = 76;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kaivojen ja putkistojen tarkastus', (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'),	NULL,	80, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'), jarjestys = 80;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kaivojen ja putkistojen sulatus', (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'),	NULL,	81, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'), jarjestys = 81;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu', (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'),	'kpl',	82, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'), jarjestys = 82;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Rumpujen sulatus, aukaisu ja toiminnan varmistaminen', (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'),	'',	84, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'), jarjestys = 84;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Rumpujen tarkastus', (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'),	NULL,	85, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'), jarjestys = 85;
-- MHU Ylläpito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet', (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'),	'jm',	86, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'), jarjestys = 86;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet', (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'),	'jm',	87, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'), jarjestys = 87;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet', (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'),	'jm',	90, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'), jarjestys = 90;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet', (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'),	'jm',	91, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'), jarjestys = 91;

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' , (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'), 'jm', 88, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'), jarjestys = 88;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm' , (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'), 'jm', 89, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)'), jarjestys = 89;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm' , (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'), 'jm', 92, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'), jarjestys = 92;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm' , (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'), 'jm', 93, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)'), jarjestys = 93;
-- Liikenneympäristön hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut rumpujen kunnossapitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'),	NULL,	96, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'), jarjestys = 96;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset', (select id from tehtavaryhma where nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)'	),	'',	97, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma =  (select id from tehtavaryhma where nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)'	), jarjestys = 97;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Reunakivivaurioiden korjaukset', (select id from tehtavaryhma where nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)'),	'',	98, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma =  (select id from tehtavaryhma where nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)'	), jarjestys = 98;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)'),	NULL,	101, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma =  (select id from tehtavaryhma where nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)'	), jarjestys = 101;
-- Päällysteiden paikkaus

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut päällysteiden paikkaukseen liittyvät työt', (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	NULL,	115, NULL,  (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'), jarjestys = 115;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut päällystettyjen teiden sorapientareiden kunnossapitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'),	NULL,	120, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'), jarjestys = 120;
-- Liikenneympäristön hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)', (select id from tehtavaryhma where nimi = 'SILLAT JA LAITURIT (I)'),	'kpl',	121, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SILLAT JA LAITURIT (I)'), jarjestys = 121;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)', (select id from tehtavaryhma where nimi = 'SILLAT JA LAITURIT (I)'),	'kpl',	122, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SILLAT JA LAITURIT (I)'), jarjestys = 122;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Muut siltojen ja laitureiden hoitoon liittyvät työt', (select id from tehtavaryhma where nimi = 'SILLAT JA LAITURIT (I)'),	NULL,	123, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SILLAT JA LAITURIT (I)'), jarjestys = 123;
-- Sorateiden hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sorateiden pinnan hoito, hoitoluokka II', (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'),	'tiekm',	125, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 125;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sorateiden pinnan hoito, hoitoluokka III', (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'),	'tiekm',	126, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 126;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Sorapintaisten kävely- ja pyöräilyväylienhoito', (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'),	'tiekm',	127, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 127;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Maakivien (>1m3) poisto', (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'),	'jm',	130, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 130;
-- MHU Ylläpito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/päällystetyt tiet' , (select id from tehtavaryhma where nimi = 'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)'), 'jm', 139, (select id from toimenpidekoodi where nimi = 'Avo-ojitus / päällystetyt tiet' and emo = (select id from toimenpidekoodi where koodi = '20112')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)'), jarjestys = 139;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' , (select id from tehtavaryhma where nimi = 'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)'), 'jm', 140, (select id from toimenpidekoodi where nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' and emo = (select id from toimenpidekoodi where koodi = '20112')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)'), jarjestys = 140;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Laskuojat/päällystetyt tiet' , (select id from tehtavaryhma where nimi = 'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)'), 'jm', 141, (select id from toimenpidekoodi where nimi = 'Laskuojat/päällystetyt tiet' and emo = (select id from toimenpidekoodi where koodi = '20112')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)'), jarjestys = 141;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/soratiet' , (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), 'jm', 142, (select id from toimenpidekoodi where nimi = 'Sorateiden avo-ojitus' and emo = (select id from toimenpidekoodi where koodi = '20143')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 142;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Avo-ojitus/soratiet (kaapeli kaivualueella)' , (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), 'jm', 143, (select id from toimenpidekoodi where nimi = 'Sorateiden avo-ojitus (kaapeli kaivualueella)' and emo = (select id from toimenpidekoodi where koodi = '20143')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 143;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Laskuojat/soratiet' , (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), 'jm', 144, (select id from toimenpidekoodi where nimi = 'Laskuojat/soratiet' and emo = (select id from toimenpidekoodi where koodi = '20143')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 144;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kalliokynsien louhinta ojituksen yhteydessä', (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'),	'm2',	145, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'), jarjestys = 145;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Pohjavesisuojaukset', (select id from tehtavaryhma where nimi = 'MUUT (F)'),	NULL,	152, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'MUUT (F)'), jarjestys = 152;
-- Liikenneympäristön hoito

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Nopeusnäyttötaulun hankinta', (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'),	'kpl/1. hoitovuosi',	155, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'), jarjestys = 155;
-- TODO: Onko oikea toimenpidekoodi


INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Pysäkkikatoksen uusiminen' , (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'), 'kpl', 157, (select id from toimenpidekoodi where nimi = 'Pysäkkikatoksen uusiminen' and emo = (select id from toimenpidekoodi where koodi = '14301')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'), jarjestys = 157;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES ('Pysäkkikatoksen poistaminen' , (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'), 'kpl', 158, (select id from toimenpidekoodi where nimi = 'Pysäkkikatoksen poistaminen' and emo = (select id from toimenpidekoodi where koodi = '14301')), (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'), jarjestys = 158;

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Tilaajan rahavaraus lupaukseen 1', (select id from tehtavaryhma where nimi = 'TILAAJAN RAHAVARAUS (T3)'),	'euroa',	159, NULL, (select id from toimenpidekoodi where koodi = '20191'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'), jarjestys = 159;
-- TODO: Onko oikea toimenpidekoodi

--  MHU
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoidonjohtopalkkio', (select id from tehtavaryhma where nimi = 'HOIDONJOHTOPALKKIO (G)'),	NULL,	160, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'HOIDONJOHTOPALKKIO (G)'), jarjestys = 160;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoitourakan työnjohto', (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'),	NULL,	161, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'), jarjestys = 161;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.', (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'),	NULL,	162, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'), jarjestys = 162;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoito- ja korjaustöiden pientarvikevarasto', (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'),	NULL,	163, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'), jarjestys = 163;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon', (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'),	NULL,	164, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'JOHTO- JA HALLINTOKORVAUS (J)'), jarjestys = 164;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Toimitilat sähkö-, lämmitys-, vesi-, jäte-, siivous-, huolto-, korjaus- ja vakuutus- yms. kuluineen', (select id from tehtavaryhma where nimi = 'ERILLISHANKINNAT (W)'),	NULL,	165, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ERILLISHANKINNAT (W)'), jarjestys = 165;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut', (select id from tehtavaryhma where nimi = 'ERILLISHANKINNAT (W)'),	NULL,	166, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ERILLISHANKINNAT (W)'), jarjestys = 166;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)', (select id from tehtavaryhma where nimi = 'ERILLISHANKINNAT (W)'),	NULL,	167, NULL, (select id from toimenpidekoodi where koodi = '23151'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ERILLISHANKINNAT (W)'), jarjestys = 167;

------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Uudet tehtävät. Näille löytyi useita vastaavuuksia vanhoista tehtävistä ja täytyy lisätä ensisijainen tehtävä määrien suunnittelua varten. --------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
-- Jos löytyy oikean niminen vanha tehtävä, niputtavaa tehtävää ei ole luotu vaan käytetään niputtajana olemassa olevaa tehtävää.

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Liukkaudentorjunta hiekoituksella', (select id from tehtavaryhma where nimi = 'TALVISUOLA (B1)'),	'jkm',27, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVISUOLA (B1)'), jarjestys = 27;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Opastustaulun/-viitan uusiminen', (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'), 'm2',	42  , NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'), jarjestys = 42;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kuumapäällyste', (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	'tonni', 102, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'), jarjestys = 102;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Puhallus-SIP', (select id from tehtavaryhma where nimi = 'PUHALLUS-SIP (Y3)'),	'tonni', 104, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHALLUS-SIP (Y3)'), jarjestys = 104;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Massasaumaus', (select id from tehtavaryhma where nimi = 'MASSASAUMAUS (Y5)'),	'tonni', 106, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'MASSASAUMAUS (Y5)'), jarjestys = 106;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Valuasfaltti', (select id from tehtavaryhma where nimi = 'VALU (Y7)'),	'tonni', 108, NULL, (select id from toimenpidekoodi where koodi = '20107'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VALU (Y7)'), jarjestys = 108;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Reunantäyttö', (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'), 'tonni',	116, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'), jarjestys = 116;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Liikenteen varmistaminen kelirikkokohteessa', (select id from tehtavaryhma where nimi = 'SORASTUS (M)'),	'tonni', 132, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORASTUS (M)'), jarjestys = 132;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Soratien runkokelirikkokorjaukset', (select id from tehtavaryhma where nimi = 'RKR-KORJAUS (Q)'),	'tiem', 146, NULL, (select id from toimenpidekoodi where koodi = '14301'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'RKR-KORJAUS (Q)'), jarjestys = 146;


------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Uudet tehtävät. Äkillinen hoitotyö ja kolmansien osapuolten aiheuttamien vahinkojen korjaaminen                                            --------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------


INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Äkillinen hoitotyö', (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, TALVIHOITO (T1)'),	NULL,	135, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, TALVIHOITO (T1)'), jarjestys = 135;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Äkillinen hoitotyö', (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, LIIKENNEYMPÄRISTÖN HOITO (T1)'),	NULL,	135, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, LIIKENNEYMPÄRISTÖN HOITO (T1)'), jarjestys = 135;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Äkillinen hoitotyö', (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, SORATIET (T1)'),	NULL,	135, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, SORATIET (T1)'), jarjestys = 135;

INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen', (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, TALVIHOITO (T2)'), NULL, 136, NULL, (select id from toimenpidekoodi where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, TALVIHOITO (T2)'), jarjestys = 136;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen', (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, LIIKENNEYMPÄRISTÖN HOITO (T2)'),	NULL,	136, NULL, (select id from toimenpidekoodi where koodi = '23116'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, LIIKENNEYMPÄRISTÖN HOITO (T2)'), jarjestys = 136;
INSERT into toimenpidekoodi (nimi, tehtavaryhma, yksikko, jarjestys, api_tunnus, emo, luotu, luoja, taso, ensisijainen) VALUES (	'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen', (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, SORATIET (T2)'),	NULL,	136, NULL, (select id from toimenpidekoodi where koodi = '23124'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), 4, TRUE)
ON CONFLICT(nimi, emo) DO UPDATE SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, SORATIET (T2)'), jarjestys = 136;


------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--- MHU: Olemassa olleiden tehtävien tehtäväryhmämäppäykset     --------------------------------------------------------------------------------------------------------------------
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

----------------
-- TALVIHOITO --
----------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 5, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is 2-ajorat.' AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 6, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is 1-ajorat.'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 7, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is ohituskaistat' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 8, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Is rampit'	AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 9, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib 2-ajorat.'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 10, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib 1-ajorat.'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 11, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib ohituskaistat'	AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 12, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ib rampit'	AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 17, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'II'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 18, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'III'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 20, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'K1'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 21, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'K2'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVISUOLA (B1)'),	jarjestys = 25, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Suolaus' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
-- Suolaus-tehtävän yksikkö on jkm, materiaalit raportoidaan erikseen (tonneina)

-- Liukkauden torjunta hiekoittamalla
---------------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
-- Yksikkö on jkm, materiaalit tonneina erikseen raportointuna
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVISUOLA (B1)'),	jarjestys = 27, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Pistehiekoitus' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVISUOLA (B1)'),	jarjestys = 27, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Linjahiekoitus' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND poistettu is not true AND piilota is not true;

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 34, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ei yksilöity' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND yksikko = '-' AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'TALVIHOITO (A)'),	jarjestys = 34, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Muut talvihoitotyöt' AND emo = (select id from toimenpidekoodi where koodi = '23104') AND yksikko = '-' AND poistettu is not true AND piilota is not true;


------------------------------
-- LIIKENNEYMPÄRISTÖN HOITO --
------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 36, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Valvontakameratolppien puhdistus/tarkistus keväisin' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 37, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapaalujen kp (uusien)'	AND yksikko = 'tiekm' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 38, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Porttaalien tarkastus ja huolto' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 39, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki'	AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 40, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)' AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 41, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)' AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


-- Opastustaulun/-viitan uusiminen
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 42, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastinviitan tai -taulun uusiminen ja lisääminen -ajoradan yläpuoliset opasteet'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 42, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 43, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis. liikennemerkkien poistamisia)'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


-- Opastustaulun/viitan uusiminen porttaalissa
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 44, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen portaaliin'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 44, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen -portaalissa olevan viitan/opastetaulun uusiminen'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)'),	jarjestys = 44, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	jarjestys = 48, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Levähdysalueen puhtaanapito' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	jarjestys = 51, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Meluesteiden pesu' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


-- Töherrysten poisto
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	jarjestys = 53, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Töherrysten poisto'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	jarjestys = 53, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Graffitien poisto'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;


UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	jarjestys = 54, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Töherrysten estokäsittely'	AND yksikko = 'm2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHTAANAPITO (P)'),	jarjestys = 55, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Katupölynsidonta' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VESAKONRAIVAUKSET JA PUUN POISTO (V)'),	jarjestys = 62, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vesakonraivaus N1'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VESAKONRAIVAUKSET JA PUUN POISTO (V)'),	jarjestys = 63, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vesakonraivaus N2' AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VESAKONRAIVAUKSET JA PUUN POISTO (V)'),	jarjestys = 64, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Vesakonraivaus N3'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 65, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto N1' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 66, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto N2' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 67, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto N3' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 68, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto T1/E1'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 69, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nurmetuksen hoito / niitto T2/E2'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 70, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Puiden ja pensaiden hoito T1/E1'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 71, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Puiden ja pensaiden hoito T2/E2/N1' AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'VESAKONRAIVAUKSET JA PUUN POISTO (V)'),	jarjestys = 74, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Runkopuiden poisto'	AND yksikko = 'kpl' AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)'),	jarjestys = 78, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Muut viheralueiden hoitoon liittyvät työt'	AND yksikko = '-'  AND emo = (select id from toimenpidekoodi where koodi = '23116') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUIVATUSJÄRJESTELMÄT (K)'),	jarjestys = 79, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu'	AND (yksikko = '-' OR yksikko is NULL) AND emo = (select id from toimenpidekoodi where koodi = '23116');



------------------------------------
-- KORJAUS I                      --
------------------------------------

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)'),	jarjestys = 97, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset'	AND (yksikko = '' OR yksikko is NULL) AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');


------------------------------------
-- PÄÄLLYSTEIDEN PAIKKAUS         --
------------------------------------

-- Kuumapäällyste
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 102, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kuumapäällyste, ab käsityönä'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 102, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - kuumapäällyste'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Kylmäpäällyste ml. SOP
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 103, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus, kylmäpäällyste'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 103, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus -kylmäpäällyste ml. SOP'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 103, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -kylmäpäällyste ml. SOP'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Puhallus-SIP
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 104, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 104, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'SIP paikkaus (kesto+kylmä)' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 105, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus -saumojen juottaminen bitumilla'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Massasaumaus
-----------------------------------
-- Viisi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 106, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Konetiivistetty massasaumaus 10 cm leveä' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 106, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Konetiivistetty massasaumaus 20 cm leveä' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 106, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus - Konetiivistetty massasaumaus 20 cm leveä'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 106, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus - massasaumaus'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 106, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - massasaumaus'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Konetiivistetty valuasfaltti
-----------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 107, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty valuasvaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

-- Valuasfaltti
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 108, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Kuumapäällyste, valuasfaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 108, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - valuasvaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'KUUMAPÄÄLLYSTE (Y1)'),	jarjestys = 108, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällysteiden paikkaus - valuasfaltti'	AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SILTAPÄÄLLYSTEET (H)'),	jarjestys = 109, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sillan päällysteen halkeaman avarrussaumaus'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SILTAPÄÄLLYSTEET (H)'),	jarjestys = 110, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sillan kannen päällysteen päätysauman korjaukset'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SILTAPÄÄLLYSTEET (H)'),	jarjestys = 111, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapalkin ja päällysteen väl. sauman tiivistäminen'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SILTAPÄÄLLYSTEET (H)'),	jarjestys = 112, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapalkin liikuntasauman tiivistäminen'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '20107');


------------------------------------
-- LIIKENNEYMPÄRISTÖN HOITO --
------------------------------------

-- Reunantäyttö
-----------------------------------
-- Kolme vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'),	jarjestys = 116, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden pientareiden täyttö'	AND (yksikko = 'tn' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'),	jarjestys = 116, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden sorapientareen täyttö'	AND (yksikko = 'tn' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'),	jarjestys = 116, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden sr-pientareen täyttö'	AND (yksikko = 'tn' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');


UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'),	jarjestys = 117, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Päällystettyjen teiden palteiden poisto'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORAPIENTAREET (O)'),	jarjestys = 118, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Reunapalteen poisto kaiteen alta'	AND yksikko = 'jm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23116');


------------------------------------
-- SORATEIDEN HOITO --
------------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)'),	jarjestys = 124, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorateiden hoito, hoitoluokka I'	AND yksikko = 'tiekm' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23124');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORATEIDEN HOITO (C)'), jarjestys = 128, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorateiden pölynsidonta' AND poistettu is not true AND piilota is not true AND emo = (select id from toimenpidekoodi where koodi = '23124');
-- Tehtävän yksikkö on jkm, materiaalit erikseen (t)

-- Sorastus
-----------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen (ensimmäinen ensisijainen)
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORASTUS (M)'),	jarjestys = 129, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorastus' AND (yksikko = 't' or yksikko = 'tonni') AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORASTUS (M)'),	jarjestys = 129, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Sorastus km' AND poistettu is not true AND piilota is not true;


UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORASTUS (M)'),	jarjestys = 131, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Oja- ja luiskameteriaalin käyttö kulutuskerrokseeen' AND emo = (select id from toimenpidekoodi where koodi = '23124');

-- Liikenteen varmistaminen kelirikkokohteessa
--------------------------------------
-- Kaksi vanhaa tehtävää mäpätty yhteen
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORASTUS (M)'),	jarjestys = 132, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Liikenteen varmistaminen kelirikkokohteessa' AND emo = (select id from toimenpidekoodi where koodi = '23124');
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORASTUS (M)'),	jarjestys = 132, ensisijainen = FALSE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Liikenteen varmistaminen kelirikkokohteessa (tonni)'	AND yksikko = 'tonni' AND emo = (select id from toimenpidekoodi where koodi = '23124');

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORATEIDEN HOITO (C)'),	jarjestys = 134, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Ei yksilöity' AND emo = (select id from toimenpidekoodi where koodi = '23124')	AND (yksikko = '-' OR yksikko is NULL) AND poistettu is not true AND piilota is not true;
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'SORATEIDEN HOITO (C)'),	jarjestys = 134, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Muut sorateiden hoitoon liittyvät tehtävät' AND emo = (select id from toimenpidekoodi where koodi = '23124')	AND (yksikko = '-' OR yksikko is NULL) AND poistettu is not true AND piilota is not true;


------------------------------------
-- LIIKENNEYMPÄRISTÖN HOITO --
------------------------------------
UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'ELY-RAHOITTEISET (E)'),	jarjestys = 156, ensisijainen = TRUE, muokattu = current_timestamp, muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio') WHERE taso = 4 and nimi = 'Nopeusnäyttötaulujen ylläpito, käyttö ja siirto'	AND emo = (select id from toimenpidekoodi where koodi = '23116');-- AND yksikko = 'kpl'; yksikkö on erä eikä kappal AND poistettu is not true AND piilota is not truee


-- Päivitä vielä nimet
UPDATE toimenpidekoodi set nimi = 'Muut talvihoitotyöt' WHERE emo = (select id from toimenpidekoodi where koodi = '23104') and nimi = 'Ei yksilöity';
UPDATE toimenpidekoodi set nimi = 'Muut sorateiden hoitoon liittyvät tehtävät' WHERE emo = (select id from toimenpidekoodi where koodi = '23124') and nimi = 'Ei yksilöity';


-- Muokkaukset uuden Tehtävä- ja määräluetteloversion johdosta
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'HIEKOITUS (B3)' and tyyppi = 'alataso') WHERE  nimi in ('Linjahiekoitus', 'Pistehiekoitus', 'Liukkaudentorjunta hiekoituksella');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)' and tyyppi = 'alataso') WHERE
    tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)' and tyyppi = 'alataso') OR
    tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)' and tyyppi = 'alataso');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)' and tyyppi = 'alataso') WHERE
    tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)' and tyyppi = 'alataso');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, PÄÄLLYSTETIET (R)' and tyyppi = 'alataso') WHERE
    tehtavaryhma = (select id from tehtavaryhma where nimi = 'RUMMUT, SORATIET (S)' and tyyppi = 'alataso');

UPDATE toimenpidekoodi SET tehtavaryhma = (select id from tehtavaryhma where nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)') WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt';

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'KYLMÄPÄÄLLYSTE (Y2)' and tyyppi = 'alataso') WHERE
  tehtavaryhma IS NOT NULL and nimi IN(
                                       'Päällysteiden paikkaus -kylmäpäällyste ml. SOP',
                                       'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -kylmäpäällyste ml. SOP',
                                       'Päällysteiden paikkaus, kylmäpäällyste');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'PUHALLUS-SIP (Y3)' and tyyppi = 'alataso') WHERE
  tehtavaryhma IS NOT NULL and nimi IN(
                                       'Puhallus-SIP',
                                       'SIP paikkaus (kesto+kylmä)',
                                       'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'SAUMOJEN JUOTTAMINEN BITUMILLA (Y4)' and tyyppi = 'alataso') WHERE
tehtavaryhma IS NOT NULL and nimi IN('Päällysteiden paikkaus -saumojen juottaminen bitumilla');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'MASSASAUMAUS (Y5)' and tyyppi = 'alataso') WHERE
tehtavaryhma IS NOT NULL and nimi IN
                               ('Päällysteiden paikkaus - massasaumaus',
                                'Konetiivistetty massasaumaus 10 cm leveä',
                                'Konetiivistetty massasaumaus 20 cm leveä',
                                'Massasaumaus',
                                'Päällysteiden paikkaus - Konetiivistetty massasaumaus 20 cm leveä',
                                'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - massasaumaus');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'KT-VALU (Y6)' and tyyppi = 'alataso') WHERE
 tehtavaryhma IS NOT NULL and nimi IN
                               ('Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty valuasvaltti');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'VALU (Y7)' and tyyppi = 'alataso') WHERE
  tehtavaryhma IS NOT NULL and nimi IN
                               ('Kuumapäällyste, valuasfaltti',
                                'Päällysteiden paikkaus - valuasfaltti',
                                'Valuasfaltti',
                                'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - valuasvaltti');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'KESÄSUOLA, MATERIAALI (D)' and tyyppi = 'alataso') WHERE
  tehtavaryhma IS NOT NULL and nimi = 'Sorateiden pölynsidonta';

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, TALVIHOITO (T1)') WHERE nimi = 'Äkillinen hoitotyö' AND emo = (select id from toimenpidekoodi where koodi = '23104');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, LIIKENNEYMPÄRISTÖN HOITO (T1)') WHERE nimi = 'Äkillinen hoitotyö' AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'ÄKILLISET HOITOTYÖT, SORATIET (T1)') WHERE nimi = 'Äkillinen hoitotyö' AND emo = (select id from toimenpidekoodi where koodi = '23124');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, TALVIHOITO (T2)') WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND emo = (select id from toimenpidekoodi where koodi = '23104');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, LIIKENNEYMPÄRISTÖN HOITO (T2)') WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND emo = (select id from toimenpidekoodi where koodi = '23116');
UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'VAHINKOJEN KORJAUKSET, SORATIET (T2)') WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND emo = (select id from toimenpidekoodi where koodi = '23124');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)' and tyyppi = 'alataso') WHERE
  tehtavaryhma IS NOT NULL and nimi IN ('Kalliokynsien louhinta ojituksen yhteydessä');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'TILAAJAN RAHAVARAUS (T3)' and tyyppi = 'alataso') WHERE
  tehtavaryhma IS NOT NULL and nimi IN ('Tilaajan rahavaraus lupaukseen 1');

UPDATE toimenpidekoodi set tehtavaryhma = (select id from tehtavaryhma where nimi = 'AVO-OJITUS, SORATIET (Z)') WHERE nimi = 'Kalliokynsien louhinta ojituksen yhteydessä';

-- Päivitä toimenpidekoodien järjestys uuden tehtävä- ja määräluettelon mukaiseksi (MHU)
UPDATE toimenpidekoodi SET jarjestys = 1	WHERE nimi = 'Ise 2-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 2	WHERE nimi = 'Ise 1-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 3	WHERE nimi = 'Ise ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 4	WHERE nimi = 'Ise rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 5	WHERE nimi = 'Is 2-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 6	WHERE nimi = 'Is 1-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 7	WHERE nimi = 'Is ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 8	WHERE nimi = 'Is rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 9	WHERE nimi = 'Ib 2-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 10	WHERE nimi = 'Ib 1-ajorat.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 11	WHERE nimi = 'Ib ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 12	WHERE nimi = 'Ib rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 13	WHERE nimi = 'Ic 2-ajorat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 14	WHERE nimi = 'Ic 1-ajorat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 15	WHERE nimi = 'Ic ohituskaistat' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 16	WHERE nimi = 'Ic rampit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 17	WHERE nimi = 'II' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 18	WHERE nimi = 'III' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 19	WHERE nimi = 'Kävely- ja pyöräilyväylien laatukäytävät' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 20	WHERE nimi = 'K1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 21	WHERE nimi = 'K2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 22	WHERE nimi = 'Levähdys- ja pysäköimisalueet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 23	WHERE nimi = 'Muiden alueiden talvihoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 24	WHERE nimi = 'Talvihoidon kohotettu laatu' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 25	WHERE nimi = 'Suolaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 26	WHERE nimi = 'Suolauksessa on vain yksi tehtävä. XXXXXXXXX' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 27	WHERE nimi = 'Linjahiekoitus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 27	WHERE nimi = 'Pistehiekoitus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 27	WHERE nimi = 'Liukkaudentorjunta hiekoituksella' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 28	WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 29	WHERE nimi = 'Pysäkkikatosten puhdistus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 30	WHERE nimi = 'Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 31	WHERE nimi = 'Portaiden talvihoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 32	WHERE nimi = 'Lisäkalustovalmius/-käyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 34	WHERE nimi = 'Muut talvihoitotyöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 35	WHERE nimi = 'Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 36	WHERE nimi = 'Valvontakameratolppien puhdistus/tarkistus keväisin' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 37	WHERE nimi = 'Reunapaalujen kp (uusien)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 38	WHERE nimi = 'Porttaalien tarkastus ja huolto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 39	WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 40	WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 41	WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 42	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 42	WHERE nimi = 'Opastinviitan tai -taulun uusiminen ja lisääminen -ajoradan yläpuoliset opasteet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 42	WHERE nimi = 'Opastustaulun/-viitan uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 43	WHERE nimi = 'Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis. liikennemerkkien poistamisia)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 44	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen portaaliin' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 44	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 44	WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -portaalissa olevan viitan/opastetaulun uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 47	WHERE nimi = 'Muut liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoitotyöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 48	WHERE nimi = 'Levähdysalueen puhtaanapito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 49	WHERE nimi = 'Tie- ja levähdysalueiden kalusteiden kunnossapito ja hoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 50	WHERE nimi = 'Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 51	WHERE nimi = 'Meluesteiden pesu' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 52	WHERE nimi = 'Hiekoitushiekan ja irtoainesten poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 53	WHERE nimi = 'Graffitien poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 53	WHERE nimi = 'Töherrysten poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 54	WHERE nimi = 'Töherrysten estokäsittely' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 55	WHERE nimi = 'Katupölynsidonta' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 57	WHERE nimi = 'Muut tie- levähdys- ja liitännäisalueiden puhtaanpitoon ja kalusteiden hoitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 63	WHERE nimi = 'Vesakonraivaus N2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 64	WHERE nimi = 'Vesakonraivaus N3' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 65	WHERE nimi = 'Runkopuiden poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 66	WHERE nimi = 'Nurmetuksen hoito / niitto N1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 67	WHERE nimi = 'Nurmetuksen hoito / niitto N2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 68	WHERE nimi = 'Nurmetuksen hoito / niitto N3' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 69	WHERE nimi = 'Nurmetuksen hoito / niitto T1/E1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 70	WHERE nimi = 'Nurmetuksen hoito / niitto T2/E2' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 71	WHERE nimi = 'Puiden ja pensaiden hoito T1/E1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 72	WHERE nimi = 'Puiden ja pensaiden hoito T2/E2/N1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 73	WHERE nimi = 'Erillisten hoito-ohjeiden mukaiset vihertyöt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 74	WHERE nimi = 'Erillisten hoito-ohjeiden mukaiset vihertyöt, uudet alueet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 75	WHERE nimi = 'Vesistöpenkereiden hoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 76	WHERE nimi = 'Tiekohtaiset maisemanhoitoprojektit' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 78	WHERE nimi = 'Muut viheralueiden hoitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 80	WHERE nimi = 'Kaivojen ja putkistojen tarkastus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 81	WHERE nimi = 'Kaivojen ja putkistojen sulatus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 82	WHERE nimi = 'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 84	WHERE nimi = 'Rumpujen sulatus, aukaisu ja toiminnan varmistaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 85	WHERE nimi = 'Rumpujen tarkastus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 86	WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 87	WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 88	WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 89	WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 90	WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 91	WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 92	WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 93	WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 96	WHERE nimi = 'Muut rumpujen kunnossapitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 97	WHERE nimi = 'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 98	WHERE nimi = 'Reunakivivaurioiden korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 101	WHERE nimi = 'Muut kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 102	WHERE nimi = 'Kuumapäällyste, ab käsityönä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 102	WHERE nimi = 'Kuumapäällyste' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 102	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - kuumapäällyste' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 103	WHERE nimi = 'Päällysteiden paikkaus -kylmäpäällyste ml. SOP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 103	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -kylmäpäällyste ml. SOP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 103	WHERE nimi = 'Päällysteiden paikkaus, kylmäpäällyste' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 104	WHERE nimi = 'Puhallus-SIP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 104	WHERE nimi = 'SIP paikkaus (kesto+kylmä)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 104	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 105	WHERE nimi = 'Päällysteiden paikkaus -saumojen juottaminen bitumilla' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 106	WHERE nimi = 'Päällysteiden paikkaus - massasaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 106	WHERE nimi = 'Konetiivistetty massasaumaus 10 cm leveä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 106	WHERE nimi = 'Konetiivistetty massasaumaus 20 cm leveä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 106	WHERE nimi = 'Massasaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 106	WHERE nimi = 'Päällysteiden paikkaus - Konetiivistetty massasaumaus 20 cm leveä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 106	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - massasaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 107	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty valuasvaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 108	WHERE nimi = 'Kuumapäällyste, valuasfaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 108	WHERE nimi = 'Päällysteiden paikkaus - valuasfaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 108	WHERE nimi = 'Valuasfaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 108	WHERE nimi = 'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - valuasvaltti' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 109	WHERE nimi = 'Sillan päällysteen halkeaman avarrussaumaus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 110	WHERE nimi = 'Sillan kannen päällysteen päätysauman korjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 111	WHERE nimi = 'Reunapalkin ja päällysteen väl. sauman tiivistäminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 112	WHERE nimi = 'Reunapalkin liikuntasauman tiivistäminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 115	WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 116	WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 116	WHERE nimi = 'Päällystettyjen teiden pientareiden täyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 116	WHERE nimi = 'Reunantäyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 116	WHERE nimi = 'Päällystettyjen teiden sorapientareen täyttö' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 117	WHERE nimi = 'Päällystettyjen teiden palteiden poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 118	WHERE nimi = 'Reunapalteen poisto kaiteen alta' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 120	WHERE nimi = 'Muut päällystettyjen teiden sorapientareiden kunnossapitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 121	WHERE nimi = 'Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 122	WHERE nimi = 'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 124	WHERE nimi = 'Muut siltojen ja laitureiden hoitoon liittyvät työt' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 124	WHERE nimi = 'Sorateiden hoito, hoitoluokka I' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 125	WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka II' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 126	WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka III' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 127	WHERE nimi = 'Sorapintaisten kävely- ja pyöräilyväylienhoito' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 128	WHERE nimi = 'Maakivien (>1m3) poisto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 129	WHERE nimi = 'Sorateiden pölynsidonta' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 130	WHERE nimi = 'Sorastus' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 130	WHERE nimi = 'Sorastus km' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 131	WHERE nimi = 'Oja- ja luiskameteriaalin käyttö kulutuskerrokseeen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 132	WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 134	WHERE nimi = 'Muut sorateiden hoitoon liittyvät tehtävät' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 135	WHERE nimi = 'Äkillinen hoitotyö' AND tehtavaryhma IS NOT NULL; -- 3 riviä
UPDATE toimenpidekoodi SET jarjestys = 137	WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 138	WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 139	WHERE nimi = 'Avo-ojitus/päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 140	WHERE nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 141	WHERE nimi = 'Laskuojat/päällystetyt tiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 142	WHERE nimi = 'Avo-ojitus/soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 143	WHERE nimi = 'Avo-ojitus/soratiet (kaapeli kaivualueella)' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 144	WHERE nimi = 'Laskuojat/soratiet' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 145	WHERE nimi = 'Kalliokynsien louhinta ojituksen yhteydessä' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 146	WHERE nimi = 'Soratien runkokelirikkokorjaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 152	WHERE nimi = 'Pohjavesisuojaukset' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 155	WHERE nimi = 'Nopeusnäyttötaulun hankinta' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 156	WHERE nimi = 'Nopeusnäyttötaulujen ylläpito, käyttö ja siirto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 157	WHERE nimi = 'Pysäkkikatoksen uusiminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 158	WHERE nimi = 'Pysäkkikatoksen poistaminen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 159	WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 160	WHERE nimi = 'Hoidonjohtopalkkio' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 161	WHERE nimi = 'Hoitourakan työnjohto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 162	WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 163	WHERE nimi = 'Hoito- ja korjaustöiden pientarvikevarasto' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 164	WHERE nimi = 'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 165	WHERE nimi = 'Toimitilat sähkö-, lämmitys-, vesi-, jäte-, siivous-, huolto-, korjaus- ja vakuutus- yms. kuluineen' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 166	WHERE nimi = 'Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut' AND tehtavaryhma IS NOT NULL;
UPDATE toimenpidekoodi SET jarjestys = 167	WHERE nimi = 'Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)' AND tehtavaryhma IS NOT NULL;

UPDATE tehtavaryhma SET jarjestys = 1 WHERE nimi = 'Talvihoito';
UPDATE tehtavaryhma SET jarjestys = 1 WHERE nimi = 'Välitaso Talvihoito';
UPDATE tehtavaryhma SET jarjestys = 25 WHERE nimi = 'Liukkaudentorjunta';
UPDATE tehtavaryhma SET jarjestys = 1 WHERE nimi = 'TALVIHOITO (A)';
UPDATE tehtavaryhma SET jarjestys = 25 WHERE nimi = 'TALVISUOLA (B1)';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Sillat';
UPDATE tehtavaryhma SET jarjestys = 124 WHERE nimi = 'Sorateiden hoito';
UPDATE tehtavaryhma SET jarjestys = 129 WHERE nimi = 'Välitaso Sorateiden hoito';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Hoitoluokat, kevarit ja kivet sekä muut';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Sorastus, luiska ja varmistus';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Sorastus, luiska ja varmistus';
UPDATE tehtavaryhma SET jarjestys = 139 WHERE nimi = 'MHU Ylläpito';
UPDATE tehtavaryhma SET jarjestys = 135 WHERE nimi = 'ÄKILLISET HOITOTYÖT, SORATIET (T1)';
UPDATE tehtavaryhma SET jarjestys = 146 WHERE nimi = 'Välitaso MHU Ylläpito';
UPDATE tehtavaryhma SET jarjestys = 139 WHERE nimi = 'Ojat';
UPDATE tehtavaryhma SET jarjestys = 152 WHERE nimi = 'Pohjavesisuojaukset';
UPDATE tehtavaryhma SET jarjestys = 152 WHERE nimi = 'Välitaso Pohjavesisuojaukset';
UPDATE tehtavaryhma SET jarjestys = 152 WHERE nimi = 'MUUT (F)';
UPDATE tehtavaryhma SET jarjestys = 159 WHERE nimi = 'Muut liik.ymp.hoitosasiat';
UPDATE tehtavaryhma SET jarjestys = 159 WHERE nimi = 'Välitaso Muut liik.ymp.hoitosasiat';
UPDATE tehtavaryhma SET jarjestys = 155 WHERE nimi = 'ELY-RAHOITTEISET (E)';
UPDATE tehtavaryhma SET jarjestys = 161 WHERE nimi = 'Johto- ja hallintokorvaukseen sisältyvät tehtävät';
UPDATE tehtavaryhma SET jarjestys = 161 WHERE nimi = 'Välitaso Johto- ja hallintokorvaukseen sisältyvät tehtävät';
UPDATE tehtavaryhma SET jarjestys = 165 WHERE nimi = 'Erillishankinnat erillishinnoin';
UPDATE tehtavaryhma SET jarjestys = 139 WHERE nimi = 'AVO-OJITUS, PÄÄLLYSTETYT TIET (X)';
UPDATE tehtavaryhma SET jarjestys = 142 WHERE nimi = 'AVO-OJITUS, SORATIET (Z)';
UPDATE tehtavaryhma SET jarjestys = 146 WHERE nimi = 'RKR-KORJAUS (Q)';
UPDATE tehtavaryhma SET jarjestys = 160 WHERE nimi = 'HOIDONJOHTOPALKKIO (G)';
UPDATE tehtavaryhma SET jarjestys = 161 WHERE nimi = 'JOHTO- JA HALLINTOKORVAUS (J)';
UPDATE tehtavaryhma SET jarjestys = 165 WHERE nimi = 'Välitaso Erillishankinnat erillishinnoin';
UPDATE tehtavaryhma SET jarjestys = 124 WHERE nimi = 'Sorateiden pinnan hoito';
UPDATE tehtavaryhma SET jarjestys = 124 WHERE nimi = 'SORATEIDEN HOITO (C)';
UPDATE tehtavaryhma SET jarjestys = 165 WHERE nimi = 'ERILLISHANKINNAT (W)';
UPDATE tehtavaryhma SET jarjestys = 130 WHERE nimi = 'Muut sorateiden hoidon tehtävät';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Ojitus';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Ojitus';
UPDATE tehtavaryhma SET jarjestys = 35 WHERE nimi = 'Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen';
UPDATE tehtavaryhma SET jarjestys = 35 WHERE nimi = 'Välitaso Liikennemerkkien, liikenteen ohjauslaitteiden ja reunapaalujen hoito sekä uusiminen';
UPDATE tehtavaryhma SET jarjestys = 48 WHERE nimi = 'Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito';
UPDATE tehtavaryhma SET jarjestys = 48 WHERE nimi = 'Välitaso Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito';
UPDATE tehtavaryhma SET jarjestys = 62 WHERE nimi = 'Viheralueiden hoito';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Vesakko ja runkopuu';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Alataso Vesakko ja runkopuu';
UPDATE tehtavaryhma SET jarjestys = 66 WHERE nimi = 'Nurmi, puut ja pensaat sekä muut virheraluehommat';
UPDATE tehtavaryhma SET jarjestys = 62 WHERE nimi = 'Vesakon raivaus ja runkopuun poisto';
UPDATE tehtavaryhma SET jarjestys = 79 WHERE nimi = 'Kuivatusjärjestelmän kaivojen, putkistojen ja pumppaamoiden hoito';
UPDATE tehtavaryhma SET jarjestys = 79 WHERE nimi = 'Sade, kaivot ja rummut';
UPDATE tehtavaryhma SET jarjestys = 86 WHERE nimi = 'Rumpujen kunnossapito ja uusiminen';
UPDATE tehtavaryhma SET jarjestys = 86 WHERE nimi = 'Rumpujen kunnossapito ja uusiminen (päällystetty tie)';
UPDATE tehtavaryhma SET jarjestys = 90 WHERE nimi = 'Rumpujen kunnossapito ja uusiminen (soratie)';
UPDATE tehtavaryhma SET jarjestys = 35 WHERE nimi = 'LIIKENNEMERKIT JA LIIKENTEENOHJAUSLAITTEET (L)';
UPDATE tehtavaryhma SET jarjestys = 48 WHERE nimi = 'PUHTAANAPITO (P)';
UPDATE tehtavaryhma SET jarjestys = 62 WHERE nimi = 'VESAKONRAIVAUKSET JA PUUN POISTO (V)';
UPDATE tehtavaryhma SET jarjestys = 66 WHERE nimi = 'NURMETUKSET JA MUUT VIHERTYÖT (N)';
UPDATE tehtavaryhma SET jarjestys = 79 WHERE nimi = 'KUIVATUSJÄRJESTELMÄT (K)';
UPDATE tehtavaryhma SET jarjestys = 86 WHERE nimi = 'RUMMUT, PÄÄLLYSTETIET (R)';
UPDATE tehtavaryhma SET jarjestys = 90 WHERE nimi = 'RUMMUT, SORATIET (S)';
UPDATE tehtavaryhma SET jarjestys = 120 WHERE nimi = 'SORASTUS (M)';
UPDATE tehtavaryhma SET jarjestys = 97 WHERE nimi = 'Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito';
UPDATE tehtavaryhma SET jarjestys = 97 WHERE nimi = 'Välitaso Kaiteiden, riista- ja suoja-aitojen sekä kiveysten kunnossapito';
UPDATE tehtavaryhma SET jarjestys = 102 WHERE nimi = 'Päällysteiden paikkaus';
UPDATE tehtavaryhma SET jarjestys = 102 WHERE nimi = 'Välitaso Päällysteiden paikkaus';
UPDATE tehtavaryhma SET jarjestys = 666 WHERE nimi = 'Sillat'; ---
UPDATE tehtavaryhma SET jarjestys = 109 WHERE nimi = 'Sillan päällysteen korjaus';
UPDATE tehtavaryhma SET jarjestys = 116 WHERE nimi = 'Päällystettyjen teiden sorapientareen kunnossapito';
UPDATE tehtavaryhma SET jarjestys = 116 WHERE nimi = 'Välitaso Päällystettyjen teiden sorapientareen kunnossapito';
UPDATE tehtavaryhma SET jarjestys = 121 WHERE nimi = 'Siltojen ja laitureiden hoito';
UPDATE tehtavaryhma SET jarjestys = 121 WHERE nimi = 'Välitaso Siltojen ja laitureiden hoito';
UPDATE tehtavaryhma SET jarjestys = 26 WHERE nimi = 'KFO,NAFO (B2)';
UPDATE tehtavaryhma SET jarjestys = 27 WHERE nimi = 'HIEKOITUS (B3)';
UPDATE tehtavaryhma SET jarjestys = 97 WHERE nimi = 'KAITEET, AIDAT JA KIVEYKSET (U)';
UPDATE tehtavaryhma SET jarjestys = 102 WHERE nimi = 'KUUMAPÄÄLLYSTE (Y1)';
UPDATE tehtavaryhma SET jarjestys = 103 WHERE nimi = 'KYLMÄPÄÄLLYSTE (Y2)';
UPDATE tehtavaryhma SET jarjestys = 104 WHERE nimi = 'PUHALLUS-SIP (Y3)';
UPDATE tehtavaryhma SET jarjestys = 105 WHERE nimi = 'SAUMOJEN JUOTTAMINEN BITUMILLA (Y4)';
UPDATE tehtavaryhma SET jarjestys = 106 WHERE nimi = 'MASSASAUMAUS (Y5)';
UPDATE tehtavaryhma SET jarjestys = 107 WHERE nimi = 'KT-VALU (Y6)';
UPDATE tehtavaryhma SET jarjestys = 108 WHERE nimi = 'VALU (Y7)';
UPDATE tehtavaryhma SET jarjestys = 109 WHERE nimi = 'SILTAPÄÄLLYSTEET (H)';
UPDATE tehtavaryhma SET jarjestys = 116 WHERE nimi = 'SORAPIENTAREET (O)';
UPDATE tehtavaryhma SET jarjestys = 121 WHERE nimi = 'SILLAT JA LAITURIT (I)';
UPDATE tehtavaryhma SET jarjestys = 129 WHERE nimi = 'KESÄSUOLA, MATERIAALI (D)';
UPDATE tehtavaryhma SET jarjestys = 159 WHERE nimi = 'TILAAJAN RAHAVARAUS (T3)';

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
UPDATE tehtavaryhma SET yksiloiva_tunniste = '24103c8d-3a8a-4b6f-9315-570834d4479d' WHERE nimi = 'ÄKILLISET HOITOTYÖT, TALVIHOITO (T1)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = 'c3cb9e68-7f08-4145-ad8f-f2985e8f1658' WHERE nimi = 'ÄKILLISET HOITOTYÖT, LIIKENNEYMPÄRISTÖN HOITO (T1)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '5a6760e8-6494-4db2-80bc-c06df391a5b6' WHERE nimi = 'ÄKILLISET HOITOTYÖT, SORATIET (T1)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '0623ae3c-b8b0-4791-96ea-4808029d43de' WHERE nimi = 'VAHINKOJEN KORJAUKSET, TALVIHOITO (T2)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '1b374802-dbe7-430b-bfc5-4635383d18e3' WHERE nimi = 'VAHINKOJEN KORJAUKSET, LIIKENNEYMPÄRISTÖN HOITO (T2)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = 'df612065-20d5-47b9-8cca-51ffd250e1f8' WHERE nimi = 'VAHINKOJEN KORJAUKSET, SORATIET (T2)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '37d3752c-9951-47ad-a463-c1704cf22f4c' WHERE nimi = 'ERILLISHANKINNAT (W)';
UPDATE tehtavaryhma SET yksiloiva_tunniste = '0e78b556-74ee-437f-ac67-7a03381c64f6' WHERE nimi = 'TILAAJAN RAHAVARAUS (T3)';

UPDATE toimenpidekoodi SET yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744' WHERE nimi = 'Hoitourakan työnjohto';
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388' WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.';
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '49b7388b-419c-47fa-9b1b-3797f1fab21d' WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                                                                                   tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '0623ae3c-b8b0-4791-96ea-4808029d43de');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '63a2585b-5597-43ea-945c-1b25b16a06e2' WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                                                                                   tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '1b374802-dbe7-430b-bfc5-4635383d18e3');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = 'b3a7a210-4ba6-4555-905c-fef7308dc5ec' WHERE nimi = 'Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                                                                                   tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'df612065-20d5-47b9-8cca-51ffd250e1f8');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '1f12fe16-375e-49bf-9a95-4560326ce6cf' WHERE nimi = 'Äkillinen hoitotyö' AND
                                                                                   tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '24103c8d-3a8a-4b6f-9315-570834d4479d');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974' WHERE nimi = 'Äkillinen hoitotyö' AND
                                                                                   tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = 'c3cb9e68-7f08-4145-ad8f-f2985e8f1658');
UPDATE toimenpidekoodi SET yksiloiva_tunniste = 'd373c08b-32eb-4ac2-b817-04106b862fb1' WHERE nimi = 'Äkillinen hoitotyö' AND
                                                                                   tehtavaryhma = (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste = '5a6760e8-6494-4db2-80bc-c06df391a5b6');

-- Päivitetään api-tunnus tehtävähierarkian tehtäville, joilla sitä ei entuudestaan ole. Sama tunnus kaikkiin ympäristöihin (prod, stg, test, local). Huom. osa tehtävistä puuttuu kehitysympäristöstä.
-- Api-tunnuksen olemassa olo ei tarkoita sitä, että tehtävälle kirjataan apin kautta toteumia. Api-käyttöä määrittää seurataan-apin-kautta-sarake.
UPDATE toimenpidekoodi
set api_tunnus = (115 * jarjestys)
where tehtavaryhma is not null and api_tunnus is null;
-- TODO: Poista tämä ennen kuin api_tunnus otetaan käyttöön