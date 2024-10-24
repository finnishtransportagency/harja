INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)' and poistettu = false), 200);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto'), 33.4);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'III'), 32.6);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Katupölynsidonta'), 400);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Katupölynsidonta'), 666);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Ib rampit'), 500);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'K2'), 55.5);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'), 777.6);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (select id from tehtava where nimi = 'Is ohituskaistat'), 69.96);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'), 123.4);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Is ohituskaistat'), 5556);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (select id from tehtava where nimi = 'Kuumapäällyste'), 999);

-- Valtakunnallinen määrätoteumaraportti
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Vesakonraivaus' and poistettu = false), 111.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu = false), 222.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 333.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' and poistettu = false), 433.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Päällystettyjen teiden sr-pientareen täyttö' and poistettu = false), 544.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Vesakonraivaus' and poistettu = false), 321.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu = false), 654.1);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Ivalon MHU testiurakka (uusi)'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 321.1);

INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten puhdistus' and poistettu = false), 6.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatosten puhdistus' and poistettu = false), 7.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 8.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Ic 1-ajorat' and poistettu = false), 9.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Graffitien poisto' and poistettu = false), 10.12);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2021, (SELECT id FROM tehtava WHERE nimi = 'Graffitien poisto' and poistettu = false), 11.12);

INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen uusiminen' and poistettu = false), 2000);
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), 2020, (SELECT id FROM tehtava WHERE nimi = 'Pysäkkikatoksen poistaminen' and poistettu = false), 1000);

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 52.2, 'jm', 4, null, (select id from tehtava where nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 43.2, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden sorapientareen kunnossapito' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 34.2, 'jm', 4, null, (select id from tehtava where nimi = 'Puun poisto raivausjätteineen (taajamassa)' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 25.2, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden sorapientareen kunnossapito' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 16.2, 'jm', 4, null, (select id from tehtava where nimi = 'Portaiden talvihoito' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 63.2, 'jm', 4, null, (select id from tehtava where nimi = 'Ic 1-ajorat' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Aktiivinen Oulu Testi')));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 22.7, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2020-10-01', '2020-12-31', 33.7, 'jm', 4, null, (select id from tehtava where nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 44.7, 'jm', 4, null, (select id from tehtava where nimi = 'Päällystettyjen teiden palteiden poisto' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2021-01-01', '2021-09-30', 55.7, 'jm', 4, null, (select id from tehtava where nimi = 'Rumpujen korjaus ja uusiminen  600 - 1000 mm' and poistettu is not true), (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi = 'Tampereen alueurakka 2017-2022')));
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

--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'), 11000, NOW(), null, 2019);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'), 11000, NOW(), null, 2020);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'), 11000, NOW(), null, 2021);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'), 11000, NOW(), null, 2022);
--INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'), 11000, NOW(), null, 2023);

INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1100, NOW(), null, 2019);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1100, NOW(), null, 2020);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1400, NOW(), null, 2021);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1500, NOW(), null, 2022);
INSERT INTO sopimus_tehtavamaara (urakka, tehtava, maara, muokattu, muokkaaja, hoitovuosi) VALUES ((select id from urakka where nimi = 'Oulun MHU 2019-2024'), (select id from tehtava where nimi = 'Is ohituskaistat'), 1100, NOW(), null, 2023);

-- Levittäjällä tehtävien Päällysteiden paikkaus -tehtävien osalta erikoisvaatimus, että aina vaaditaan lomakkeella sijainti. Lisätään nämä testidataan, jotta voidaan testata.
INSERT INTO tehtava (nimi, emo, luotu, poistettu, yksikko, jarjestys, hinnoittelu, api_seuranta, suoritettavatehtava, piilota, api_tunnus, tehtavaryhma, "mhu-tehtava?", yksiloiva_tunniste, suunnitteluyksikko, voimassaolo_alkuvuosi, voimassaolo_loppuvuosi, kasin_lisattava_maara, "raportoi-tehtava?", materiaaliluokka_id, materiaalikoodi_id, aluetieto, nopeusrajoitus)
VALUES
    ('AB-paikkaus levittäjällä', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1341, '{kokonaishintainen}', true, null, null, 24628, (SELECT id from tehtavaryhma WHERE nimi = 'Kuumapäällyste (Y1)'), true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('PAB-paikkaus levittäjällä', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1341, '{kokonaishintainen}', true, null, null, 24629, (SELECT id from tehtavaryhma WHERE nimi = 'Kylmäpäällyste (Y2)'), true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('KT-reikävaluasfalttipaikkaus', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'kpl', 1344, '{kokonaishintainen}', false, null, null, null, (SELECT id from tehtavaryhma WHERE nimi = 'KT-Valu (Y3)'), true, null, 'kpl', 2020, null, true, false, null, null, false, 108),
    ('KT-valuasfalttipaikkaus K', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1040, '{kokonaishintainen}', false, null, null, null, (SELECT id from tehtavaryhma WHERE nimi = 'KT-Valu (Y3)'), true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('KT-valuasfalttipaikkaus T', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'tonni', 1041, '{kokonaishintainen}', false, null, null, null, (SELECT id from tehtavaryhma WHERE nimi = 'KT-Valu (Y3)'), true, null, 'tonni', 2020, null, true, false, null, null, false, 108),
    ('KT-valuasfalttisaumaus', (SELECT id FROM toimenpide WHERE taso = 3 and nimi ilike 'Päällysteiden paikkaus%'), NOW(), false, 'jm', 1345, '{kokonaishintainen}', false, null, null, null, (SELECT id from tehtavaryhma WHERE nimi = 'KT-Valu (Y3)'), true, null, 'jm', 2020, null, true, true, null, null, false, 108)
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
                (select id from tehtava where nimi = 'Sorateiden pölynsidonta (materiaali)'),
                11000, 
                urakan_alkuvuosi,
                urakan_loppuvuosi - 1);

        perform luo_kaikille_tehtaville_testitarjousmaarat ('Pellon MHU testiurakka (3. hoitovuosi)', 1100);

end 
$$ language plpgsql;

delete from sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Ise ohituskaistat') and urakka = (select id from urakka where nimi = 'Pellon MHU testiurakka (3. hoitovuosi)');
delete from sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Ennalta arvaamattomien kuljetusten avustaminen') and urakka = (select id from urakka where nimi = 'Pellon MHU testiurakka (3. hoitovuosi)');
delete from sopimus_tehtavamaara where tehtava = (select id from tehtava where nimi = 'Opastustaulun/-viitan uusiminen') and urakka = (select id from urakka where nimi = 'Pellon MHU testiurakka (3. hoitovuosi)');

-- Kaikkia toimenpidekoodeja ei ole migraatiotiedostoja ajettaessa lokaaliympäristöissä.
-- Kun dataa haetaan urakat_tehtavamaara taulusta, materliaalikoodi ja materiaaliluokka mäppäykset on oltava.
-- Joten luodaan ne tässä
UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Talvisuola')
WHERE nimi = 'Suolaus';
UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Formiaatti')
WHERE nimi = 'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)';

-- Materiaaleihin mäpättävät tehtavat
UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Hiekoitushiekka'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka')
WHERE nimi = 'Liukkaudentorjunta hiekoituksella (materiaali)';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Kesäsuola'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Kesäsuola sorateiden kevätkunnostus')
WHERE nimi = 'Sorateiden pölynsidonta (materiaali)';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Kelirikkomurske')
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Hiekoitushiekka'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Hiekoitushiekka')
WHERE nimi = 'Ennalta arvaamattomien kuljetusten avustaminen';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Reunantäyttömurske')
WHERE nimi = 'Reunantäyttö';

UPDATE tehtava SET materiaaliluokka_id = (SELECT id FROM materiaaliluokka WHERE nimi = 'Murske'),
                           materiaalikoodi_id = (SELECT id FROM materiaalikoodi WHERE nimi = 'Sorastusmurske')
WHERE nimi = 'Sorastus';

-- Korjataan sorastuksen yksikkö
UPDATE tehtava SET yksikko = 'tonni', suunnitteluyksikko = 'tonni'
WHERE nimi = 'Sorastus';
