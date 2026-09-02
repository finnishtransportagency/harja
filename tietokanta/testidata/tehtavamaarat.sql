INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and poistettu = false), 200);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto'), 33.4);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'III'), 32.6);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Katupölynsidonta'), 400);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Katupölynsidonta'), 666);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Ib rampit'), 500);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'K2'), 55.5);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'), 777.6);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Is ohituskaistat'), 69.96);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'), 123.4);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Is ohituskaistat'), 5556);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Kuumapäällyste'), 999);

-- Valtakunnallinen määrätoteumaraportti
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Vesakonraivaus/ha' and poistettu = false), 111.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu = false), 222.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 333.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' and poistettu = false), 433.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' and poistettu = false), 544.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Vesakonraivaus/ha' and poistettu = false), 321.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu = false), 654.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 321.1);

INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten puhdistus' and poistettu = false), 6.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten puhdistus' and poistettu = false), 7.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 8.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 9.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Graffitien poisto' and poistettu = false), 10.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Graffitien poisto' and poistettu = false), 11.12);

INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen uusiminen' and poistettu = false limit 1), 2000);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen poistaminen' and poistettu = false limit 1), 1000);

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 52.2, 'jm', 4, null, (select id from tehtava where nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 43.2, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden sorapientareen kunnossapito' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 34.2, 'jm', 4, null, (select id from tehtava where nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 25.2, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden sorapientareen kunnossapito' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 16.2, 'jm', 4, null, (select id from tehtava where nimi = 'Portaiden talvihoito' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 63.2, 'jm', 4, null, (select id from tehtava where nimi = 'Ic 1-ajorat' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 22.7, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 33.7, 'jm', 4, null, (select id from tehtava where nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm' and poistettu is not true limit 1), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 44.7, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 55.7, 'jm', 4, null, (select id from tehtava where nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm' and poistettu is not true limit 1), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 66.7, 'jm', 4, null, (select id from tehtava where nimi = 'Kaivojen ja putkistojen sulatus' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 77.7, 'jm', 4, null, (select id from tehtava where nimi = 'Ic 1-ajorat' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));

-- Sopimuksen mukaiset tehtävämäärät
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and poistettu = false), 25000, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and poistettu = false), 25000, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and poistettu = false), 25000, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and poistettu = false), 25000, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and poistettu = false), 25000, NOW(), null, 2023);

INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto'), 500, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto'), 600, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto'), 400, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto'), 300, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto'), 500, NOW(), null, 2023);

INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'III'), 500, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'III'), 500, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'III'), 500, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'III'), 500, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'III'), 500, NOW(), null, 2023);

INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Katupölynsidonta'), 10000, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Katupölynsidonta'), 10000, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Katupölynsidonta'), 10000, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Katupölynsidonta'), 10000, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Katupölynsidonta'), 10500, NOW(), null, 2023);

INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Ib rampit'), 8000, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Ib rampit'), 8000, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Ib rampit'), 8000, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Ib rampit'), 8000, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Ib rampit'), 8000, NOW(), null, 2023);

INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'K2'), 1000, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'K2'), 2000, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'K2'), 2000, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'K2'), 1000, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'K2'), 1000, NOW(), null, 2023);

--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'), 11000, NOW(), null, 2019);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'), 11000, NOW(), null, 2020);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'), 11000, NOW(), null, 2021);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'), 11000, NOW(), null, 2022);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'), 11000, NOW(), null, 2023);

INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1100, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1100, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1400, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1500, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1100, NOW(), null, 2023);

-- Levittäjällä tehtävien Päällysteiden paikkaus -tehtävien osalta erikoisvaatimus, että aina vaaditaan lomakkeella sijainti. Lisätään nämä testidataan, jotta voidaan testata.
INSERT INTO tehtava (nimi, emo, luotu, poistettu, yksikko, jarjestys, hinnoittelu, api_seuranta, suoritettavatehtava, piilota, api_tunnus, tehtavaryhma, "mhu-tehtava?", yksiloiva_tunniste, suunnitteluyksikko, voimassaolo_alkuvuosi, voimassaolo_loppuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", materiaaliluokka_id, materiaalikoodi_id, aluetieto, nopeusrajoitus)
VALUES
    ('AB-paikkaus levittäjällä', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1341, '{kokonaishintainen}', true, null, null, 24628,
     (SELECT id from tehtavaryhma WHERE yksiloiva_tunniste = 'b1cca2a5-6445-4f49-878d-a95f144cc190'), -- Kuumapäällyste
     true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('PAB-paikkaus levittäjällä', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1341, '{kokonaishintainen}', true, null, null, 24629,
     (SELECT id from tehtavaryhma WHERE yksiloiva_tunniste = '91c147a3-4469-40c5-9c8b-9aac05da52db'), -- Kylmäpäällyste
     true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('KT-reikävaluasfalttipaikkaus', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'kpl', 1344, '{kokonaishintainen}', false, null, null, null,
     (SELECT id from tehtavaryhma WHERE yksiloiva_tunniste = '34aa4298-9430-4843-9256-baa743e24e50'), -- KT-valu
     true, null, 'kpl', 2020, null, true, false, null, null, false, 108),
    ('KT-valuasfalttipaikkaus K', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1040, '{kokonaishintainen}', false, null, null, null,
     (SELECT id from tehtavaryhma WHERE yksiloiva_tunniste = '34aa4298-9430-4843-9256-baa743e24e50'), -- KT-valu
     true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('KT-valuasfalttipaikkaus T', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1041, '{kokonaishintainen}', false, null, null, null,
     (SELECT id from tehtavaryhma WHERE yksiloiva_tunniste = '34aa4298-9430-4843-9256-baa743e24e50'), -- KT-valu
     true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('KT-valuasfalttisaumaus', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'jm', 1345, '{kokonaishintainen}', false, null, null, null,
     (SELECT id from tehtavaryhma WHERE yksiloiva_tunniste = '34aa4298-9430-4843-9256-baa743e24e50'), -- KT-valu
     true, null, 'jm', 2020, null, true, true, null, null, false, 108)
    ON CONFLICT DO NOTHING;


DO
$$
declare 
        urakka_rivi record;
        urakan_alkuvuosi integer;
        urakan_loppuvuosi integer;
begin
        select * into urakka_rivi from urakka where nimi = 'Oulun MHU 2019-2024';
        select extract(year from urakka_rivi.alkupvm) into urakan_alkuvuosi;
        select extract(year from urakka_rivi.loppupvm) into urakan_loppuvuosi;
        perform luo_testitarjousmaarat_tehtavalle(urakka_rivi.id,
                (select id from tehtava where nimi = 'Kesäsuola (CaCl2, materiaali)'),
                11000, 
                urakan_alkuvuosi,
                urakan_loppuvuosi - 1);

        perform luo_kaikille_tehtaville_testitarjousmaarat ('Pellon MHU testiurakka (3. hoitovuosi)', 1100);

end 
$$ language plpgsql;

delete from sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Ise ohituskaistat') and urakka = (select id from urakka where nimi = 'Pellon MHU testiurakka (3. hoitovuosi)');
delete from sopimus_tehtavamaara where tehtava = (select id from tehtava where yksiloiva_tunniste = 'c3ada25e-70f2-407b-8dff-2c1a303578be') and urakka = (select id from urakka where nimi = 'Pellon MHU testiurakka (3. hoitovuosi)'); -- Ennalta arvaamattomien kuljetusten avustaminen
delete from sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Opastustaulun/-viitan uusiminen') and urakka = (select id from urakka where nimi = 'Pellon MHU testiurakka (3. hoitovuosi)');

-- Kaikkia toimenpidekoodeja ei ole migraatiotiedostoja ajettaessa lokaaliympäristöissä.
-- Kun dataa haetaan urakat_tehtavamaara taulusta, materliaalikoodi ja materiaaliluokka mäppäykset on oltava.
-- Joten luodaan ne tässä
UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola')
WHERE nimi = 'Liukkaudentorjunta suolaamalla (materiaali)';
UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Formiaatti')
WHERE nimi = 'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)';

-- Materiaaleihin mäpättävät tehtavat
UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE materiaalityyppi = 'hiekoitushiekka'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE yksiloiva_tunniste = 'abbb61e5-beee-42fd-a60d-14ec156afae5')
WHERE nimi = 'Liukkaudentorjunta hiekoituksella (materiaali)'; -- Liukkaudentorjunta hiekoituksella (materiaali)

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesäsuola'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Kesäsuola sorateiden kevätkunnostus')
WHERE nimi = 'Kesäsuola (CaCl2, materiaali)';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Kelirikkomurske')
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE materiaalityyppi = 'hiekoitushiekka'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka, liukkaudentorjunta')
WHERE yksiloiva_tunniste = 'c3ada25e-70f2-407b-8dff-2c1a303578be'; -- Ennalta arvaamattomien kuljetusten avustaminen (km)

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE materiaalityyppi = 'hiekoitushiekka'),
                   materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka, liukkaudentorjunta')
WHERE yksiloiva_tunniste = 'ae67d2b5-a9d9-4880-a7ee-b3870737a177'; -- Ennalta arvaamattomien kuljetusten avustaminen (materiaali)

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Reunantäyttömurske')
WHERE nimi = 'Reunantäyttö';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Sorastusmurske')
WHERE nimi = 'Sorastus';

-- Korjataan sorastuksen yksikkö
UPDATE tehtava SET yksikko = 'tonni', suunnitteluyksikko = 'tonni'
WHERE nimi = 'Sorastus';



CREATE OR REPLACE FUNCTION tehtavamaara_testidata_vuodelle(
  p_urakka text,         -- 'Iin MHU 2021-%'
  p_hoitovuosi int       -- 2025
) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_urakka_id int;
  v_kayttaja_id int;
BEGIN
  SELECT id INTO v_urakka_id FROM urakka WHERE nimi LIKE p_urakka LIMIT 1;
  SELECT id INTO v_kayttaja_id FROM kayttaja WHERE kayttajanimi = 'Integraatio' LIMIT 1;

  IF v_urakka_id IS NULL THEN  
    RAISE EXCEPTION 'Urakkaa ei löydy: %', p_urakka;
  END IF;

  IF v_kayttaja_id IS NULL THEN
    RAISE EXCEPTION 'Käyttäjää ei löydy.';
  END IF;

  INSERT INTO urakka_tehtavamaara
    (urakka,"hoitokauden-alkuvuosi",tehtava,maara,poistettu,luotu,luoja,muokattu,muokkaaja,"muuttunut-tarjouksesta?")
  VALUES
    (v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Reunantäyttö' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE yksiloiva_tunniste = 'ae67d2b5-a9d9-4880-a7ee-b3870737a177' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ojitus' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Reunapalteen poisto' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden pölynsidonta (jkm)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Reunapaalujen kunnossapito' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka III' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka II' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE yksiloiva_tunniste = 'c3ada25e-70f2-407b-8dff-2c1a303578be' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa (materiaali)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ic ohituskaistat' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Is ohituskaistat' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ise ohituskaistat' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ib ohituskaistat' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Sorapintaisten kävely- ja pyöräilyväylienhoito' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka I' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Lisäkalustovalmius/-käyttö' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Portaiden talvihoito' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten puhdistus' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Talvihoidon kohotettu laatu' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Levähdys- ja pysäköimisalueet' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'K2' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'K1' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kävely- ja pyöräilyväylien laatukäytävät' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'III' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'II' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ic rampit' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ic 2-ajorat' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ib rampit' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ib 1-ajorat.' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ib 2-ajorat.' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Is rampit' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Is 1-ajorat.' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Is 2-ajorat.' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ise rampit' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ise 1-ajorat.' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Ise 2-ajorat.' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kesäsuola (CaCl2, materiaali)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Liukkaudentorjunta hiekoituksella (materiaali)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,false),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'KT-valuasfalttipaikkaus T' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Katupölynsidonta' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Graffitien poisto' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Sorateitä kaventava ojitus' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Soratieluokka I' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Soratieluokka II' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden pientareiden täyttö' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Soratien runkokelirikkokorjaukset' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Muiden alueiden talvihoito' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Muut tie- levähdys- ja liitännäisalueiden puhtaanpitoon ja kalusteiden hoitoon liittyvät työt' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Reunakivivaurioiden korjaukset' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Muut tavoitehintaan vaikuttavat rahavaraukset' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Laskuojat/päällystetyt tiet' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Laskuojat/soratiet' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen uusiminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kalliokynsien louhinta ojituksen yhteydessä' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen poistaminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kuumapäällyste' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Puhallus-SIP' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Massasaumaus' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Valuasfaltti' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Reunantäyttö km' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Liikennemerkkipylvään tehostamismerkkien uusiminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Juurakkopuhdistamo, selkeytys- ja hulevesiallas sekä -painanne' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Siltakeilojen sidekiveysten purkaumien, suojaverkkojen ja kosketussuojaseinien pienet korjaukset' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Varalaskupaikkojen hoito' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Meluesteiden pienten vaurioiden korjaaminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Aitojen vaurioiden korjaukset' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Tunneleiden ylläpito' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Nopeusnäyttötaulun hankinta' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden kunnostaminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden rakentaminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Reunapaalujen uusiminen' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Apitunnus-testitehtävä' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Apitunnus-testitehtävä, tupla' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'AB-paikkaus levittäjällä' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'PAB-paikkaus levittäjällä' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'KT-reikävaluasfalttipaikkaus' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'KT-valuasfalttipaikkaus K' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'KT-valuasfalttisaumaus' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Sohjo-ojien teko' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet' limit 1),0,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	-- Määrämitattavat 
	-- Liukkaudentorjunta suolaamalla (materiaali) 
    (v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Liukkaudentorjunta suolaamalla (materiaali)'),6,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	-- Opastetaulut
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen' limit 1),6,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' limit 1),20,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen' limit 1),7,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Opastinviitan tai -taulun uusiminen ja lisääminen -ajoradan yläpuoliset opasteet' limit 1),14,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen' limit 1),6,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	-- Poistot , jm 
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden poisto ja uusiminen' limit 1),11,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Runkopuiden poisto' limit 1),34,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto' limit 1),14,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	-- m3 
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Maakivien (>1m3) poisto' limit 1),16,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	-- Rummut, Tonnia 
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet' limit 1),48,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet' limit 1),11,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet' limit 1),24,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Rumpujen sulatus, aukaisu ja toiminnan varmistaminen' limit 1),34,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' limit 1),41,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm' limit 1),8,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm' limit 1),61,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm' limit 1),13,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	-- Ojitus, jm 
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/päällystetyt tiet' limit 1),1600,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' limit 1),4500,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/soratiet' limit 1),1100,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true),
	(v_urakka_id,p_hoitovuosi,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/soratiet (kaapeli kaivualueella)' limit 1),9500,false,'2025-08-18 08:56:13.763757',v_kayttaja_id,NULL,NULL,true);

  INSERT INTO sopimus_tehtavamaara
    (urakka,tehtava,maara,muokattu,muokkaaja,hoitovuosi)
  VALUES
	-- Määrämitattavat 
    (v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Liukkaudentorjunta suolaamalla (materiaali)'),6,'2025-08-18 08:10:19.324',v_kayttaja_id,p_hoitovuosi),
	-- Opastetaulut
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen' limit 1),7,'2025-08-18 08:09:23.831',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Opastinviitan tai -taulun uusiminen ja lisääminen -ajoradan yläpuoliset opasteet' limit 1),14,'2025-08-18 08:09:23.835',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen' limit 1),6,'2025-08-18 08:09:23.839',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen' limit 1),6,'2025-08-18 08:10:19.324',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' limit 1),20,'2025-08-18 08:10:21.266',v_kayttaja_id,p_hoitovuosi),
	-- Poistot , jm 
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden palteiden poisto' limit 1),14,'2025-08-18 08:09:23.86',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Runkopuiden poisto' limit 1),34,'2025-08-18 08:09:23.848',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden poisto ja uusiminen' limit 1),11,'2025-08-18 08:09:24.379',v_kayttaja_id,p_hoitovuosi),
	-- m3 
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Maakivien (>1m3) poisto' limit 1),16,'2025-08-18 08:09:24.266',v_kayttaja_id,p_hoitovuosi),
	-- Rummut, Tonnia 
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm' limit 1),13,'2025-08-18 08:09:23.856',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm' limit 1),8,'2025-08-18 08:09:23.817',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm' limit 1),61,'2025-08-18 08:09:23.82',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm' limit 1),41,'2025-08-18 08:09:23.852',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Rumpujen sulatus, aukaisu ja toiminnan varmistaminen' limit 1),34,'2025-08-18 08:09:23.879',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet' limit 1),48,'2025-08-18 08:09:24.272',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet' limit 1),11,'2025-08-18 08:09:24.278',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet' limit 1),24,'2025-08-18 08:09:24.285',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet' limit 1),0,'2025-08-18 08:10:44.516',v_kayttaja_id,p_hoitovuosi),
	-- Ojitus, jm 
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/päällystetyt tiet' limit 1),1600,'2025-08-18 08:09:23.824',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)' limit 1),4500,'2025-08-18 08:09:23.828',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/soratiet' limit 1),1100,'2025-08-18 08:09:23.93',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Avo-ojitus/soratiet (kaapeli kaivualueella)' limit 1),9500,'2025-08-18 08:09:23.937',v_kayttaja_id,p_hoitovuosi),
	-- 
	-- Loput 0
	--
	-- Inserteissä käytetyt yksilöivät tunnisteet
	-- ae67d2b5-a9d9-4880-a7ee-b3870737a177 = Ennalta arvaamattoman kuljetuksen avustaminen (materiaali)
	-- c3ada25e-70f2-407b-8dff-2c1a303578be = Ennalta arvaamattomien kuljetusten avustaminen (km)
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)' limit 1),0,'2025-08-18 08:09:23.872',v_kayttaja_id,p_hoitovuosi),
  	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'KT-valuasfalttipaikkaus T' limit 1),0,'2025-08-18 08:09:24.434',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Katupölynsidonta' limit 1),0,'2025-08-18 08:09:23.795',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Graffitien poisto' limit 1),0,'2025-08-18 08:09:23.844',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Sorateitä kaventava ojitus' limit 1),0,'2025-08-18 08:09:23.792',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Soratieluokka I' limit 1),0,'2025-08-18 08:09:23.798',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Soratieluokka II' limit 1),0,'2025-08-18 08:09:23.802',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' limit 1),0,'2025-08-18 08:09:23.805',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden pientareiden täyttö' limit 1),0,'2025-08-18 08:09:23.809',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Soratien runkokelirikkokorjaukset' limit 1),0,'2025-08-18 08:09:23.864',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Muiden alueiden talvihoito' limit 1),0,'2025-08-18 08:09:23.868',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Muut tie- levähdys- ja liitännäisalueiden puhtaanpitoon ja kalusteiden hoitoon liittyvät työt' limit 1),0,'2025-08-18 08:09:23.875',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden ja aitojen tarkastaminen ja vaurioiden korjaukset' limit 1),0,'2025-08-18 08:09:23.883',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Reunakivivaurioiden korjaukset' limit 1),0,'2025-08-18 08:09:23.887',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Muut tavoitehintaan vaikuttavat rahavaraukset' limit 1),0,'2025-08-18 08:09:23.891',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään' limit 1),0,'2025-08-18 08:09:23.896',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Laskuojat/päällystetyt tiet' limit 1),0,'2025-08-18 08:09:23.901',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Laskuojat/soratiet' limit 1),0,'2025-08-18 08:09:23.905',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen uusiminen' limit 1),0,'2025-08-18 08:09:23.91',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kalliokynsien louhinta ojituksen yhteydessä' limit 1),0,'2025-08-18 08:09:23.92',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Osallistuminen tilaajalle kuuluvien viranomaistehtävien hoitoon' limit 1),0,'2025-08-18 08:09:23.925',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Liukkaudentorjunta hiekoituksella (materiaali)' limit 1),0,'2025-08-18 08:09:23.942',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kesäsuola (CaCl2, materiaali)' limit 1),0,'2025-08-18 08:09:23.947',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ise 2-ajorat.' limit 1),0,'2025-08-18 08:09:23.951',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ise 1-ajorat.' limit 1),0,'2025-08-18 08:09:23.957',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ise rampit' limit 1),0,'2025-08-18 08:09:23.962',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Is 2-ajorat.' limit 1),0,'2025-08-18 08:09:23.969',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Is 1-ajorat.' limit 1),0,'2025-08-18 08:09:23.974',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Is rampit' limit 1),0,'2025-08-18 08:09:23.979',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ib 2-ajorat.' limit 1),0,'2025-08-18 08:09:23.984',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ib 1-ajorat.' limit 1),0,'2025-08-18 08:09:23.99',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ib rampit' limit 1),0,'2025-08-18 08:09:23.995',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ic 2-ajorat' limit 1),0,'2025-08-18 08:09:24',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' limit 1),0,'2025-08-18 08:09:24.007',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ic rampit' limit 1),0,'2025-08-18 08:09:24.013',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'II' limit 1),0,'2025-08-18 08:09:24.019',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'III' limit 1),0,'2025-08-18 08:09:24.024',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kävely- ja pyöräilyväylien laatukäytävät' limit 1),0,'2025-08-18 08:09:24.029',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'K1' limit 1),0,'2025-08-18 08:09:24.04',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'K2' limit 1),0,'2025-08-18 08:09:24.045',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Levähdys- ja pysäköimisalueet' limit 1),0,'2025-08-18 08:09:24.05',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Talvihoidon kohotettu laatu' limit 1),0,'2025-08-18 08:09:24.055',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten puhdistus' limit 1),0,'2025-08-18 08:09:24.062',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Portaiden talvihoito' limit 1),0,'2025-08-18 08:09:24.067',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Lisäkalustovalmius/-käyttö' limit 1),0,'2025-08-18 08:09:24.073',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka I' limit 1),0,'2025-08-18 08:09:24.079',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Sorapintaisten kävely- ja pyöräilyväylienhoito' limit 1),0,'2025-08-18 08:09:24.085',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Hiekkalaatikoiden täyttö ja hiekkalaatikoiden edustojen lumityöt' limit 1),0,'2025-08-18 08:09:24.09',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ib ohituskaistat' limit 1),0,'2025-08-18 08:09:24.095',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ise ohituskaistat' limit 1),0,'2025-08-18 08:09:24.1',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Is ohituskaistat' limit 1),0,'2025-08-18 08:09:24.106',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ic ohituskaistat' limit 1),0,'2025-08-18 08:09:24.117',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa (materiaali)' limit 1),0,'2025-08-18 08:09:24.123',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen poistaminen' limit 1),0,'2025-08-18 08:10:54.542',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE yksiloiva_tunniste = 'c3ada25e-70f2-407b-8dff-2c1a303578be' limit 1),0,'2025-08-18 08:09:24.128',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka II' limit 1),0,'2025-08-18 08:09:24.145',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)' limit 1),0,'2025-08-18 08:09:24.156',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Sorateiden pinnan hoito, hoitoluokka III' limit 1),0,'2025-08-18 08:09:24.161',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kuumapäällyste' limit 1),0,'2025-08-18 08:09:24.169',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Puhallus-SIP' limit 1),0,'2025-08-18 08:09:24.174',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Massasaumaus' limit 1),0,'2025-08-18 08:09:24.179',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Valuasfaltti' limit 1),0,'2025-08-18 08:09:24.184',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten siisteydestä huolehtiminen (oikaisu, huoltomaalaus jne.) ja jätehuolto sekä pienet vaurioiden korjaukset' limit 1),0,'2025-08-18 08:09:24.189',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kuivatusjärjestelmän pumppaamoiden hoito ja tarkkailu' limit 1),0,'2025-08-18 08:09:24.201',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Siltojen hoito (kevätpuhdistus, puhtaanapito, kasvuston poisto ja pienet kunnostustoimet sekä vuositarkastukset)' limit 1),0,'2025-08-18 08:09:24.211',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Laitureiden hoito (puhtaanapito, pienet kunnostustoimet, turvavarusteiden kunnon varmistaminen sekä vuositarkastukset)' limit 1),0,'2025-08-18 08:09:24.216',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Reunapaalujen kunnossapito' limit 1),0,'2025-08-18 08:09:24.222',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden pölynsidonta (jkm)' limit 1),0,'2025-08-18 08:09:24.233',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Reunapalteen poisto' limit 1),0,'2025-08-18 08:09:24.238',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Ojitus' limit 1),0,'2025-08-18 08:09:24.244',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Reunantäyttö km' limit 1),0,'2025-08-18 08:09:24.25',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Liikennemerkkipylvään tehostamismerkkien uusiminen' limit 1),0,'2025-08-18 08:09:24.255',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Juurakkopuhdistamo, selkeytys- ja hulevesiallas sekä -painanne' limit 1),0,'2025-08-18 08:09:24.292',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Siltakeilojen sidekiveysten purkaumien, suojaverkkojen ja kosketussuojaseinien pienet korjaukset' limit 1),0,'2025-08-18 08:09:24.3',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Varalaskupaikkojen hoito' limit 1),0,'2025-08-18 08:09:24.313',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen' limit 1),0,'2025-08-18 08:09:24.32',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen' limit 1),0,'2025-08-18 08:09:24.327',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Meluesteiden pienten vaurioiden korjaaminen' limit 1),0,'2025-08-18 08:09:24.333',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Aitojen vaurioiden korjaukset' limit 1),0,'2025-08-18 08:09:24.341',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Muut päällysteiden paikkaukseen liittyvät työt' limit 1),0,'2025-08-18 08:09:24.347',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt' limit 1),0,'2025-08-18 08:09:24.353',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Tunneleiden ylläpito' limit 1),0,'2025-08-18 08:09:24.36',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE yksiloiva_tunniste = 'ae67d2b5-a9d9-4880-a7ee-b3870737a177' limit 1),0,'2025-08-18 08:09:24.367',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Nopeusnäyttötaulun hankinta' limit 1),0,'2025-08-18 08:09:24.373',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden kunnostaminen' limit 1),0,'2025-08-18 08:09:24.384',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Kaiteiden rakentaminen' limit 1),0,'2025-08-18 08:09:24.389',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Reunapaalujen uusiminen' limit 1),0,'2025-08-18 08:09:24.394',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Apitunnus-testitehtävä' limit 1),0,'2025-08-18 08:09:24.399',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Apitunnus-testitehtävä, tupla' limit 1),0,'2025-08-18 08:09:24.404',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'AB-paikkaus levittäjällä' limit 1),0,'2025-08-18 08:09:24.41',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'PAB-paikkaus levittäjällä' limit 1),0,'2025-08-18 08:09:24.418',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'KT-reikävaluasfalttipaikkaus' limit 1),0,'2025-08-18 08:09:24.423',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'KT-valuasfalttipaikkaus K' limit 1),0,'2025-08-18 08:09:24.43',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'KT-valuasfalttisaumaus' limit 1),0,'2025-08-18 08:09:24.439',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Reunantäyttö' limit 1),0,'2025-08-18 08:09:24.446',v_kayttaja_id,p_hoitovuosi),
	(v_urakka_id,(SELECT id FROM tehtava WHERE nimi = 'Sohjo-ojien teko' limit 1),0,'2025-08-18 08:10:24.611',v_kayttaja_id,p_hoitovuosi);
END $$;

-- Iin MHU 2021-2026
SELECT tehtavamaara_testidata_vuodelle('Iin MHU 2021-%', 2023);
SELECT tehtavamaara_testidata_vuodelle('Iin MHU 2021-%', 2024);
SELECT tehtavamaara_testidata_vuodelle('Iin MHU 2021-%', 2025);
SELECT tehtavamaara_testidata_vuodelle('Iin MHU 2021-%', 2026);

INSERT INTO sopimuksen_tehtavamaarat_tallennettu (urakka, tallennettu) VALUES 
((SELECT id FROM urakka WHERE nimi LIKE 'Iin MHU 2021-%'), true);


-- POP MHU Kajaani 2025-2030
SELECT tehtavamaara_testidata_vuodelle('POP MHU Kajaani 2025-%', 2025);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Kajaani 2025-%', 2026);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Kajaani 2025-%', 2027);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Kajaani 2025-%', 2028);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Kajaani 2025-%', 2029);

INSERT INTO sopimuksen_tehtavamaarat_tallennettu (urakka, tallennettu) VALUES
    ((SELECT id FROM urakka WHERE nimi LIKE 'POP MHU Kajaani 2025-%'), true);


-- Suomussalmen MHU 2024-2029
SELECT tehtavamaara_testidata_vuodelle('POP MHU Suomussalmi 2024-%', 2024);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Suomussalmi 2024-%', 2025);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Suomussalmi 2024-%', 2026);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Suomussalmi 2024-%', 2027);
SELECT tehtavamaara_testidata_vuodelle('POP MHU Suomussalmi 2024-%', 2028);

INSERT INTO harja.public.sopimuksen_tehtavamaarat_tallennettu(urakka, tallennettu)
VALUES ((SELECT id FROM urakka WHERE nimi LIKE 'POP MHU Suomussalmi 2024-%'), TRUE);
