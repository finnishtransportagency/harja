INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2005-12-31', 3, 'vrk', 525.50, (SELECT id FROM tehtava WHERE nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2006-01-01', '2006-09-30', 9, 'vrk', 525.50, (SELECT id FROM tehtava WHERE nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2005-12-31', 525.73, 'km', 525.50, (SELECT id FROM tehtava WHERE nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2006-01-01', '2006-09-30', 1525.321, 'km', 525.50, (SELECT id FROM tehtava WHERE nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 525.73, 'km', 525.50, (SELECT id FROM tehtava WHERE  nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2016-10-01', '2016-12-31', 3, 'vrk', 525.50, (SELECT id FROM tehtava WHERE  nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2017-01-01', '2017-09-30', 9, 'vrk', 525.50, (SELECT id FROM tehtava WHERE  nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 3, 'vrk', 525.50, (SELECT id FROM tehtava WHERE  nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2014-01-01', '2014-09-30', 9, 'vrk', 525.50, (SELECT id FROM tehtava WHERE  nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 866.0, 'km', 525.50, (SELECT id FROM tehtava WHERE  nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null));

-- Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 20, 'm2', 1, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Pyörätien jatkeet ja suojatiet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'kpl', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nopeusrajoitusmerkinnät'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'kpl', 5, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Väistämisviivan yksi kolmio hainhammas'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 60, 'm2', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pysäytysviiva'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'm2', 4, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Sulkualueet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'm2', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pyörätien jatkeet ja suojatiet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 50, 'kpl', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 400, 'm2', 4, null, (SELECT id FROM tehtava WHERE nimi = 'Linjamerkinnät massalla paksuus 7 mm: Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 190, 'm2', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Linjamerkinnät massalla paksuus 7 mm: Reunaviiva ja reunaviivan jatke'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'm2', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm: Sulkuviiva ja varoitusviiva keltainen'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 2000, 'm2', 4, null, (SELECT id FROM tehtava WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm: Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'm2', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Linjamerkinnät massalla, paksuus 3mm: Reunaviiva ja reunaviivan jatke'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 20, 'm2', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Muut pienmerkinnät'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Nuolet ja nopeusrajoitusmerkinnät ja väistämisviivat'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'm2', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Sulkualueet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 200, 'm2', 1, null, (SELECT id FROM tehtava WHERE nimi = 'Linjamerkinnän upotusjyrsintä'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 60, 'jm', 4.5, null, (SELECT id FROM tehtava WHERE nimi = 'Täristävät merkinnät: sini-aallonmuotoinen jyrsintä, reunaviiva, 2 ajr tie: lev 30 cm, aallonpit 60 cm, syv 6 mm aallonharjalla, syv 13 mm aallon pohjalla'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'jm', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Täristävät merkinnät: sini-aallonmuotoinen jyrsintä, reunaviiva, 1 ajr tie: lev 30 cm, aallonpit 60 cm, syv 6 mm aallonharjalla, syv 13 mm aallon pohjalla'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'jm', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 2 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'jm', 2, null, (SELECT id FROM tehtava WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, keskiviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 50, 'jm', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));

-- Tievalaistuksen palvelusopimus 2015-2020: sopimuskausi 2015
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 286, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 4455, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: PpNa 35 - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 35 - 150 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 250 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Hg 50 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 120, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 35, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Sytytin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: PpNa 35  - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Monimetalli 35 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 2, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 2, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: LED-yksikkö VP 2221/2223 valaisimeen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 7, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Jalustan vaihto SJ 4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 7, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Teräskartiopylväs HE3 h=10m V=2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Myötäävä puupylväs h=10m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 15, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Puupylväsvarsi V= 1.0 - 2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 50 - 70 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 50, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, 2-tehokuristin ja tehonvaihtorele, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=6 m, K4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=10 m, AL4a'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 2, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED VP 2221/2223 M1 - M3'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 10, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Maajakokeskus'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 15, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Haruksen uusiminen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 50, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Puupylvään oikaisu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 80, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 100, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 1500, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Asentaja'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 150, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Tr- kaivuri'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 50, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Törmäysvaimennin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 360, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Kuorma-auto nosturilla'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 360, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Nostolava alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Nostolava yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

-- Tievalaistuksen palvelusopimus 2015-2020: sopimuskausi 2016
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 386, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 6173, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: PpNa 35 - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 16, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 35 - 150 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 250 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen ryhmävaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Hg 50 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 25, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 140, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 45, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Sytytin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: PpNa 35  - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 5, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Monimetalli 35 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 2, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 2, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Lamppujen yksittäisvaihto: LED-yksikkö VP 2221/2223 valaisimeen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Jalustan vaihto SJ 4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Teräskartiopylväs HE3 h=10m V=2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 25, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Myötäävä puupylväs h=10m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 20, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Puupylväsvarsi V= 1.0 - 2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 50 - 70 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 65, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 25, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, 2-tehokuristin ja tehonvaihtorele, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=6 m, K4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 56, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=10 m, AL4a'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 2, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED VP 2221/2223 M1 - M3'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Maajakokeskus'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 20, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden vaihto: Haruksen uusiminen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 60, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Puupylvään oikaisu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 90, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 5, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 130, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 5, 'kpl', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 1800, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Asentaja'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 150, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Tr- kaivuri'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 50, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Törmäysvaimennin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 450, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Kuorma-auto nosturilla'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 450, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Nostolava alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 20, 'h', 3, null, (SELECT id FROM tehtava WHERE nimi = 'Nostolava yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

-- talvihoidon laaja toimenpide Oulun ja Pudasjärven urakoille
-- talvihoidon  laaja toimenpide 23104
-- soratien hoidon laaja toimenpide 23124
-- hoitokausi 2005-2006
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id from toimenpide where koodi='23104'), 'Oulu Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id from toimenpide where koodi='23116'), 'Oulu Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id from toimenpide where koodi='23124'), 'Oulu Sorateiden hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id from toimenpide where koodi='23104'), 'Oulu Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id from toimenpide where koodi='23116'), 'Oulu Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id from toimenpide where koodi='23124'), 'Oulu Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id from toimenpide where koodi='23104'), 'Pudasjärvi Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id from toimenpide where koodi='23124'), 'Pudasjärvi Sorateiden hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Pudasjärvi Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT id FROM toimenpide WHERE koodi='23104'), 'Pori Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT id FROM toimenpide WHERE koodi='23124'), 'Pori Sorateiden hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Pori Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun tiemerkinnän palvelusopimus 2017-2024'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20123'), 'Tiemerkinnän TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun tiemerkinnän palvelusopimus 2017-2024'),(SELECT loppupvm FROM urakka WHERE nimi='Oulun tiemerkinnän palvelusopimus 2017-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pirkanmaan tiemerkinnän palvelusopimus 2013-2018'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20123'), 'Pirkanmaan Tiemerkinnän TP', '2013-01-01','2018-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20101'), 'Muhos Ajoradan päällyste TP', '2017-01-01','2024-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porintien päällystysurakka'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20101'), 'Porintien Ajoradan päällyste TP', '2007-01-01','2012-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun valaistuksen palvelusopimus 2013-2050'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20172'), 'Oulu Valaistuksen korjaus TP', '2013-01-01','2018-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Kempeleen valaistusurakka'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20172'), 'Kempele Valaistuksen korjaus TP', '2007-10-01','2012-09-30', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20123'), 'Valaistuksen korjaus TP', '2013-10-01','2015-09-30', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM toimenpide WHERE taso=3 AND koodi='20172'), 'Oulu Valaistuksen korjaus TP', '2015-01-01','2020-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
-- Luodaan Kajaanin, Vantaan ja Espoon urakalle tärkeimmät toimenpideinstanssit
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT id FROM toimenpide WHERE koodi='23104'), 'Kajaani Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Kajaani Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT id FROM toimenpide WHERE koodi='23124'), 'Kajaani Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT id FROM toimenpide WHERE koodi='23104'), 'Vantaa Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Vantaa Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT id FROM toimenpide WHERE koodi='23124'), 'Vantaa Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpide WHERE koodi='23104'), 'Espoo Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Espoo Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpide WHERE koodi='23124'), 'Espoo Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

-- Luodaan Raaseporin urakalle tärkeimmät toimenpideinstanssit
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
VALUES
    ((SELECT id FROM urakka WHERE nimi = 'UUD Raasepori  MHU 2021- 2026, P'), (SELECT id FROM toimenpide WHERE koodi='23104'), 'Raasepori Talvihoito TP 2021-2026', (SELECT alkupvm FROM urakka WHERE nimi='UUD Raasepori  MHU 2021- 2026, P'), (SELECT loppupvm FROM urakka WHERE nimi='UUD Raasepori  MHU 2021- 2026, P'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
    ((SELECT id FROM urakka WHERE nimi = 'UUD Raasepori  MHU 2021- 2026, P'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Raasepori Liikenneympäristön hoito TP 2021-2026', (SELECT alkupvm FROM urakka WHERE nimi='UUD Raasepori  MHU 2021- 2026, P'), (SELECT loppupvm FROM urakka WHERE nimi='UUD Raasepori  MHU 2021- 2026, P'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
    ((SELECT id FROM urakka WHERE nimi = 'UUD Raasepori  MHU 2021- 2026, P'), (SELECT id FROM toimenpide WHERE koodi='23124'), 'Raasepori Sorateiden hoito TP 2021-2026', (SELECT alkupvm FROM urakka WHERE nimi='UUD Raasepori  MHU 2021- 2026, P'), (SELECT loppupvm FROM urakka WHERE nimi='UUD Raasepori  MHU 2021- 2026, P'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');


INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM toimenpide WHERE koodi='23104'), 'Tampere Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT loppupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Tampere Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT loppupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM toimenpide WHERE koodi='23124'), 'Tampere Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT loppupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');


INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2005, 10, 3500, '2005-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2005, 11, 3500, '2005-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2005, 12, 3500, '2005-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 1, 3500, '2006-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 2, 3500, '2006-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 3, 3500, '2006-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 4, 3500, '2006-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 5, 3500, '2006-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 6, 3500, '2006-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 7, 3500, '2006-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 8, 3500, '2006-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 9, 3500, '2006-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));

-- hoitokausi 2006-2007
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 10, 3500, '2006-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 11, 3500, '2006-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 12, 3500, '2006-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 1, 3500, '2007-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 2, 3500, '2007-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 3, 3500, '2007-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 4, 3500, '2007-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 5, 3500, '2007-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 6, 3500, '2007-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 7, 3500, '2007-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 8, 3500, '2007-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2007, 9, 3500, '2007-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));

-- toisella sopimusnumerolla kiusaksi yksi työ
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 9, 9999, '2006-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS NOT null));


INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2005, 10, 1500, '2005-10-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2005, 11, 1500, '2005-11-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2005, 12, 1500, '2005-12-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 1, 1500, '2006-01-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 2, 1500, '2006-02-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 3, 1500, '2006-03-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 4, 1500, '2006-04-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 5, 1500, '2006-05-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 6, 1500, '2006-06-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 7, 1500, '2006-07-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 8, 1500, '2006-08-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus) VALUES (2006, 9, 1500, '2006-09-15', (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Sorateiden hoito TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));

-- Vesiväylien TPI:t
INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Kauppamerenkulun kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Kauppamerenkulun kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
    VALUES ((SELECT id FROM toimenpideinstanssi
    WHERE   nimi = 'Kauppamerenkulun kustannukset TP'
            AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')),
            'kauppamerenkulku'::vv_vaylatyyppi);

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Muun vesiliikenteen kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Muun vesiliikenteen kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE nimi = 'Muun vesiliikenteen kustannukset TP'
      AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')),
        'muu'::vv_vaylatyyppi);

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        'Urakan yhteiset kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Urakan yhteiset kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE nimi = 'Urakan yhteiset kustannukset TP'
      AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')),
        'kauppamerenkulku'::vv_vaylatyyppi);

-- Pyhäselän TPI:t
INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Pyhäselän urakka'),
        'Kauppamerenkulun kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Kauppamerenkulun kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE   nimi = 'Kauppamerenkulun kustannukset TP'
        AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')),
        'kauppamerenkulku'::vv_vaylatyyppi);

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Pyhäselän urakka'),
        'Muun vesiliikenteen kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Muun vesiliikenteen kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE nimi = 'Muun vesiliikenteen kustannukset TP'
      AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')),
        'muu'::vv_vaylatyyppi);

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Pyhäselän urakka'),
        'Urakan yhteiset kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Urakan yhteiset kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE nimi = 'Urakan yhteiset kustannukset TP'
      AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')),
        'kauppamerenkulku'::vv_vaylatyyppi);

-- Rentoselän TPI:t
INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Rentoselän urakka'),
        'Kauppamerenkulun kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Kauppamerenkulun kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE   nimi = 'Kauppamerenkulun kustannukset TP'
        AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')),
        'kauppamerenkulku'::vv_vaylatyyppi);

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Rentoselän urakka'),
        'Muun vesiliikenteen kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Muun vesiliikenteen kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE nimi = 'Muun vesiliikenteen kustannukset TP'
      AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')),
        'muu'::vv_vaylatyyppi);

INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide,  alkupvm, loppupvm)
VALUES ((SELECT id
         FROM urakka
         WHERE nimi = 'Rentoselän urakka'),
        'Urakan yhteiset kustannukset TP',
        (SELECT id
         FROM toimenpide
         WHERE nimi = 'Urakan yhteiset kustannukset'), '2016-08-01', '2017-07-30');

INSERT INTO toimenpideinstanssi_vesivaylat("toimenpideinstanssi-id", vaylatyyppi)
VALUES ((SELECT id FROM toimenpideinstanssi
WHERE nimi = 'Urakan yhteiset kustannukset TP'
      AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')),
        'kauppamerenkulku'::vv_vaylatyyppi);


-- Vesiväylien suunnitellut työt

-- Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 8, 0.3, '2016-08-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 9, 0.6, '2016-09-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 10, 0.9, '2016-10-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 11, 1.2, '2016-11-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 12, 1.5, '2016-12-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 1, 1.8, '2017-01-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 2, 2.1, '2017-02-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 3, 2.4, '2017-03-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 4, 2.7, '2017-04-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 5, 9, '2017-05-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 6, 6, '2017-06-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 7, 1.5, '2017-07-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));

INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 8, 100.3, '2017-08-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 9, 101.8, '2017-09-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 10, 100.9, '2017-10-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 11, 102.7, '2017-11-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 12, 102.1, '2017-12-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 1, 100.6, '2018-01-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 2, 106, '2018-02-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 3, 101.5, '2018-03-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 4, 101.5, '2018-04-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 5, 101.2, '2018-05-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 6, 109, '2018-06-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 7, 102.4, '2018-07-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL')), (SELECT id FROM sopimus where nimi = 'Helsingin väyläyksikön pääsopimus' AND paasopimus IS NULL));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 1, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Ammattimies'), (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 2, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet'), (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus)
VALUES ('2017-08-01', '2018-07-31', null, 'h', 3, null,
        (SELECT id FROM tehtava WHERE nimi = 'Henkilöstö: Työnjohto' AND poistettu is false),
        (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'),
        (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'));

-- Pyhäselän suunnitellut työt
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 8, 0.3, '2016-08-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 1, 1.8, '2017-01-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 10, 0.9, '2016-10-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 4, 2.7, '2017-04-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 2, 2.1, '2017-02-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 9, 0.6, '2016-09-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 6, 6, '2017-06-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 7, 1.5, '2017-07-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 12, 1.5, '2016-12-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 11, 1.2, '2016-11-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 5, 9, '2017-05-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 3, 2.4, '2017-03-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Pyhäselän urakka')), (SELECT id FROM sopimus where nimi = 'Pyhäselän pääsopimus' AND paasopimus IS NULL));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 1, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Ammattimies'), (SELECT id FROM urakka WHERE nimi = 'Pyhäselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Pyhäselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 2, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet'), (SELECT id FROM urakka WHERE nimi = 'Pyhäselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Pyhäselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 3, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Työnjohto' AND poistettu is false), (SELECT id FROM urakka WHERE nimi = 'Pyhäselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Pyhäselän pääsopimus'));


-- Rentoselän suunnitellut työt
-- Pyhäselän suunnitellut työt
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 8, 0.3, '2016-08-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 1, 1.8, '2017-01-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 10, 0.9, '2016-10-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 4, 2.7, '2017-04-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 2, 2.1, '2017-02-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 9, 0.6, '2016-09-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 6, 6, '2017-06-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 7, 1.5, '2017-07-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 12, 1.5, '2016-12-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 11, 1.2, '2016-11-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 5, 9, '2017-05-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 3, 2.4, '2017-03-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Kauppamerenkulun kustannukset TP' AND urakka = (select id from urakka where nimi = 'Rentoselän urakka')), (SELECT id FROM sopimus where nimi = 'Rentoselän pääsopimus' AND paasopimus IS NULL));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 1, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Ammattimies'), (SELECT id FROM urakka WHERE nimi = 'Rentoselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Rentoselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 2, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet'), (SELECT id FROM urakka WHERE nimi = 'Rentoselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Rentoselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 3, null, (SELECT id
                                                                                                                                                                             FROM tehtava
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Työnjohto' AND poistettu is false), (SELECT id FROM urakka WHERE nimi = 'Rentoselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Rentoselän pääsopimus'));


-- Kanavien toimenpideinstanssit
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Saimaan kanava'), (SELECT id FROM toimenpide WHERE koodi='27105'), 'Saimaan kanava, sopimukseen kuuluvat työt, TP', (SELECT alkupvm FROM urakka WHERE nimi='Saimaan kanava'), (SELECT loppupvm FROM urakka WHERE nimi='Saimaan kanava'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm) VALUES ((SELECT id FROM urakka WHERE nimi='Saimaan kanava'), (SELECT id FROM toimenpide WHERE nimi = 'Erikseen tilatut työt' AND emo = (SELECT id FROM toimenpide WHERE nimi = 'Väylänhoito')), 'Testitoimenpideinstanssi', '2017-01-01', '2090-01-01');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Joensuun kanava'), (SELECT id FROM toimenpide WHERE koodi='27105'), 'Joensuun kanava, sopimukseen kuuluvat työt, TP', (SELECT alkupvm FROM urakka WHERE nimi='Joensuun kanava'), (SELECT loppupvm FROM urakka WHERE nimi='Joensuun kanava'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 8, 0.3, '2016-08-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 1, 1.8, '2017-01-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 10, 0.9, '2016-10-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 4, 2.7, '2017-04-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 2, 2.1, '2017-02-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 9, 0.6, '2016-09-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 6, 6, '2017-06-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 7, 1.5, '2017-07-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 12, 1.5, '2016-12-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2016, 11, 1.2, '2016-11-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 5, 9, '2017-05-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 3, 2.4, '2017-03-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));

INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 8, 1000, '2017-08-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 1, 1000, '2018-01-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 10, 1000, '2017-10-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 4, 1000, '2018-04-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 2, 1000, '2018-02-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 9, 1000, '2017-09-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 6, 1000, '2018-06-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 7, 1000, '2018-07-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 12, 1000, '2017-12-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 11, 1000, '2017-11-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 5, 1000, '2018-05-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 3, 1000, '2018-03-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));

-- eri sopimukselle ja TPI:llekin vähän jotta testikattavuus
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2017, 10, 1000, '2017-10-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Saimaan kanava, sopimukseen kuuluvat työt, TP'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS NOT null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 5, 1000, '2018-05-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Testitoimenpideinstanssi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS null));
INSERT INTO kokonaishintainen_tyo(vuosi, kuukausi, summa, maksupvm, toimenpideinstanssi, sopimus) VALUES (2018, 3, 1000, '2018-03-01', (SELECT id FROM toimenpideinstanssi WHERE nimi = 'Testitoimenpideinstanssi'), (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Saimaan kanava') AND paasopimus IS NOT null));


-- Oulun MHU-urakka, kaikki sallitut toimenpiteet
DO
$$
    DECLARE
        urakka_id integer := (SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024');
        sopimus_id integer := (SELECT id FROM sopimus WHERE urakka = urakka_id);
    BEGIN
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23104'), 'Oulu MHU Talvihoito TP',
                (SELECT alkupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
                (SELECT loppupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23116'), 'Oulu MHU Liikenneympäristön hoito TP',
                (SELECT alkupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
                (SELECT loppupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23124'), 'Oulu MHU Soratien hoito TP',
                (SELECT alkupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
                (SELECT loppupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23151'), 'Oulu MHU Hallinnolliset toimenpiteet TP',
                (SELECT alkupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
                (SELECT loppupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '20107'), 'Oulu MHU Päällystepaikkaukset TP',
                (SELECT alkupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
                (SELECT loppupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '20191'), 'Oulu MHU MHU Ylläpito TP',
                (SELECT alkupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
                (SELECT loppupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '14301'), 'Oulu MHU MHU Korvausinvestointi TP',
                (SELECT alkupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'),
                (SELECT loppupvm FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');

        INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
        VALUES (2020, 2, 16666, testidata_indeksikorjaa(16666, 2020, 2, urakka_id), (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'), sopimus_id);

        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu)
        VALUES (2020, 4, 220, testidata_indeksikorjaa(220, 2020, 4, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI, null, null,
                (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'), sopimus_id, NOW());
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu)
        VALUES (2020, 6, 500, testidata_indeksikorjaa(500, 2020, 6, urakka_id),
                'akillinen-hoitotyo'::TOTEUMATYYPPI,
                (select id from tehtava where yksiloiva_tunniste = '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID),
                null, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'), sopimus_id, NOW());


        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES (2020, 2, 234, testidata_indeksikorjaa(234, 2020, 2, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI, null,
                (select id from tehtavaryhma where nimi = 'Erillishankinnat (W)'),
                (select id from toimenpideinstanssi where nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP'), sopimus_id);
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu,  tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES (2020, 2, 432, testidata_indeksikorjaa(432, 2020, 2, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI, null,
                (select id from tehtavaryhma where yksiloiva_tunniste = 'a6614475-1950-4a61-82c6-fda0fd19bb54'),
                (select id from toimenpideinstanssi where nimi = 'Oulu MHU Hallinnolliset toimenpiteet TP'), sopimus_id);
    END
$$;

-- Kittilän MHU-urakka, kaikki sallitut toimenpiteet
DO
$$
    DECLARE
        urakka_id integer := (SELECT id FROM urakka WHERE nimi = 'Kittilän MHU 2019-2024');
    BEGIN
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23104'), 'Kittilä MHU Talvihoito TP',
                (SELECT alkupvm FROM urakka WHERE id=urakka_id),
                (SELECT loppupvm FROM urakka WHERE id=urakka_id), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23116'), 'Kittilä MHU Liikenneympäristön hoito TP',
                (SELECT alkupvm FROM urakka WHERE id=urakka_id),
                (SELECT loppupvm FROM urakka WHERE id=urakka_id), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23124'), 'Kittilä MHU Soratien hoito TP',
                (SELECT alkupvm FROM urakka WHERE id=urakka_id),
                (SELECT loppupvm FROM urakka WHERE id=urakka_id), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '23151'), 'Kittilä MHU Hallinnolliset toimenpiteet TP',
                (SELECT alkupvm FROM urakka WHERE id=urakka_id),
                (SELECT loppupvm FROM urakka WHERE id=urakka_id), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '20107'), 'Kittilä MHU Päällystepaikkaukset TP',
                (SELECT alkupvm FROM urakka WHERE id=urakka_id),
                (SELECT loppupvm FROM urakka WHERE id=urakka_id), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '20191'), 'Kittilä MHU MHU Ylläpito TP',
                (SELECT alkupvm FROM urakka WHERE id=urakka_id),
                (SELECT loppupvm FROM urakka WHERE id=urakka_id), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid,
                                         talousosasto_id, talousosastopolku)
        VALUES (urakka_id,
                (SELECT id FROM toimenpide WHERE koodi = '14301'), 'Kittilä MHU MHU Korvausinvestointi TP',
                (SELECT alkupvm FROM urakka WHERE id=urakka_id),
                (SELECT loppupvm FROM urakka WHERE id=urakka_id), 'tuotepolku', 'sampoid',
                'talousosastoid', 'talousosastopolku');
    END
$$;

-- Rovaniemen MHU-urakka
--'Hallinnolliset toimenpiteet TP'
DO $$
DECLARE
  toimenpidenimet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP', 'Päällystepaikkaukset TP', 'MHU Ylläpito TP', 'MHU Korvausinvestointi TP'];
  hoito_toimenpidenimiet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP'];
  toimenpidekoodit TEXT[] := ARRAY ['23104', '23116', '23124', '20107', '20191', '14301'];
  urakan_nimi TEXT := 'Rovaniemen MHU testiurakka (1. hoitovuosi)';
  toimenpideinstanssin_nimi TEXT;
  toimenpidenimi TEXT;
  urakan_sopimus INT := (SELECT id FROM sopimus WHERE nimi = 'Rovaniemen MHU testiurakan sopimus');
  i INTEGER;
  vuosi_ INTEGER;
  urakka_id INTEGER := (SELECT id FROM urakka WHERE nimi = urakan_nimi);
  urakan_alkuvuosi INT := (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi)));
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOR i IN 1..6 LOOP
    INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpide WHERE koodi=toimenpidekoodit[i]),
               urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  END LOOP;
  INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpide WHERE koodi='23151'),
               urakan_nimi || ' ' || 'MHU ja HJU Hoidon johto', (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  -- URAKAN KIINTEÄHINTAISET TYÖT (eli suunnitellut hankinnat)
  FOREACH toimenpidenimi IN ARRAY toimenpidenimet LOOP
    IF toimenpidenimi = 'Soratien hoito TP' THEN
      -- Jätetään soratiet suunnittelematta
      CONTINUE;
    END IF;
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    FOR i IN 10..12 LOOP
            INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
        VALUES (urakan_alkuvuosi, i, 8000 + i*100, testidata_indeksikorjaa(8000 + i*100, urakan_alkuvuosi, i, urakka_id),(select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
          VALUES (vuosi_ + urakan_alkuvuosi, i, 8000 + i*100, testidata_indeksikorjaa(8000 + i*100, vuosi_ + urakan_alkuvuosi, i,  urakka_id), (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
        VALUES (5 + urakan_alkuvuosi, i, 8000 + i*100, testidata_indeksikorjaa(8000 + i*100, 5 + urakan_alkuvuosi, i, urakka_id),(select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
  END LOOP;

  -- URAKAN KUSTANNUSARVIOIDUT TYÖT
  FOREACH toimenpidenimi IN ARRAY hoito_toimenpidenimiet LOOP
    IF toimenpidenimi = 'Soratien hoito TP' THEN
      -- Jätetään soratiet suunnittelematta
      continue;
    END IF;
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    -- UI tarvitsee periaatteessa vain yhden rivin kutakin lajia (kustannussuunnitelmissa), mutta raportoinnissa joka vuoden kuulle pitää olla omansa.
    FOR i IN 10..12 LOOP
        -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
            IF toimenpidenimi = 'Liikenneympäristön hoito TP'
            THEN
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                   tehtavaryhma, toimenpideinstanssi, sopimus)
                VALUES -- kolmansien osapuolien aiheuttamat vahingot
                       (urakan_alkuvuosi, i, 5000,
                        testidata_indeksikorjaa(5000, urakan_alkuvuosi, i, urakka_id),
                        'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '49b7388b-419c-47fa-9b1b-3797f1fab21d'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'b3a7a210-4ba6-4555-905c-fef7308dc5ec'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Äkilliset hoitotyöt
                       (urakan_alkuvuosi, i, 20000,
                        testidata_indeksikorjaa(20000, urakan_alkuvuosi, i, urakka_id),
                        'akillinen-hoitotyo'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '1f12fe16-375e-49bf-9a95-4560326ce6cf'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'd373c08b-32eb-4ac2-b817-04106b862fb1'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus);
            END IF;
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES (urakan_alkuvuosi, i, 1000,
                  testidata_indeksikorjaa(1000, urakan_alkuvuosi, i,  urakka_id),
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
              -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
              IF toimenpidenimi = 'Liikenneympäristön hoito TP'
              THEN
                  INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                     tehtavaryhma, toimenpideinstanssi, sopimus)
                  VALUES -- kolmansien osapuolien aiheuttamat vahingot
                         ((vuosi_ + urakan_alkuvuosi), i, 100,
                          testidata_indeksikorjaa(100, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                          'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                          (SELECT id
                             FROM tehtava
                            WHERE yksiloiva_tunniste = CASE
                                                           WHEN (toimenpidenimi = 'Talvihoito TP')
                                                               THEN '49b7388b-419c-47fa-9b1b-3797f1fab21d'::UUID
                                                           WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                               THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                                                           WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                               THEN 'b3a7a210-4ba6-4555-905c-fef7308dc5ec'::UUID
                                END),
                          NULL,
                          (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                          urakan_sopimus),
                         -- Äkilliset hoitotyöt
                         ((vuosi_ + urakan_alkuvuosi), i, 20000,
                          testidata_indeksikorjaa(20000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                          'akillinen-hoitotyo'::TOTEUMATYYPPI,
                          (SELECT id
                             FROM tehtava
                            WHERE yksiloiva_tunniste = CASE
                                                           WHEN (toimenpidenimi = 'Talvihoito TP')
                                                               THEN '1f12fe16-375e-49bf-9a95-4560326ce6cf'::UUID
                                                           WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                               THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                                                           WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                               THEN 'd373c08b-32eb-4ac2-b817-04106b862fb1'::UUID
                                END),
                          NULL,
                          (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                          urakan_sopimus);
              END IF;
        -- Laskutukseen perustusvat toimenpidekustannukset
        IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
            VALUES ((vuosi_ + urakan_alkuvuosi), i, 1000,
                    testidata_indeksikorjaa(1000, (vuosi_ + urakan_alkuvuosi), i,  urakka_id),
                    'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        END IF;
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
            -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
            IF toimenpidenimi = 'Liikenneympäristön hoito TP'
            THEN
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                   tehtavaryhma, toimenpideinstanssi, sopimus)
                VALUES -- kolmansien osapuolien aiheuttamat vahingot
                       ((5 + urakan_alkuvuosi), i, 100,
                        testidata_indeksikorjaa(100, (5 + urakan_alkuvuosi), i, urakka_id),
                        'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '49b7388b-419c-47fa-9b1b-3797f1fab21d'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'b3a7a210-4ba6-4555-905c-fef7308dc5ec'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Äkilliset hoitotyöt
                       ((5 + urakan_alkuvuosi), i, 20000,
                        testidata_indeksikorjaa(20000, (5 + urakan_alkuvuosi), i, urakka_id),
                        'akillinen-hoitotyo'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '1f12fe16-375e-49bf-9a95-4560326ce6cf'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'd373c08b-32eb-4ac2-b817-04106b862fb1'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus);
            END IF;
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((5 + urakan_alkuvuosi), i, 1000,
                  testidata_indeksikorjaa(1000, (5 + urakan_alkuvuosi), i, urakka_id),
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
  END LOOP;

  -- URAKAN 'MHU ja HJU Hoidon johto'

  toimenpidenimi = 'MHU ja HJU Hoidon johto';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                (urakan_alkuvuosi, i, 700,
                 testidata_indeksikorjaa(700, urakan_alkuvuosi, i, urakka_id),
                 'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                (urakan_alkuvuosi, i, 9000,
                 testidata_indeksikorjaa(9000, urakan_alkuvuosi, i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                (urakan_alkuvuosi, i, 9100,
                 testidata_indeksikorjaa(9100, urakan_alkuvuosi, i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- Erillishankinnat
                ((vuosi_ + urakan_alkuvuosi), i, 700,
                 testidata_indeksikorjaa(700, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((vuosi_ + urakan_alkuvuosi), i, 9000,
                 testidata_indeksikorjaa(9000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((vuosi_ + urakan_alkuvuosi), i, 9100,
                 testidata_indeksikorjaa(9100, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((5 + urakan_alkuvuosi), i, 700,
                 testidata_indeksikorjaa(700, (5 + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((5 + urakan_alkuvuosi), i, 9000,
                 testidata_indeksikorjaa(9000, (5 + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((5 + urakan_alkuvuosi), i, 9100,
                 testidata_indeksikorjaa(9100, (5 + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
END $$;


-- Pellon MHU-urakka
--'Hallinnolliset toimenpiteet TP'
DO $$
DECLARE
  toimenpidenimet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP', 'Päällystepaikkaukset TP', 'MHU Ylläpito TP', 'MHU Korvausinvestointi TP'];
  hoito_toimenpidenimiet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP'];
  toimenpidekoodit TEXT[] := ARRAY ['23104', '23116', '23124', '20107', '20191', '14301'];
  urakan_nimi TEXT := 'Pellon MHU testiurakka (3. hoitovuosi)';
  toimenpideinstanssin_nimi TEXT;
  toimenpidenimi TEXT;
  urakan_sopimus INT := (SELECT id FROM sopimus WHERE nimi = 'Pellon MHU testiurakan sopimus');
  i INTEGER;
  vuosi_ INTEGER;
  urakka_id INTEGER = (SELECT id FROM urakka WHERE nimi = urakan_nimi);
  urakan_alkuvuosi INT := (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi)));
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOR i IN 1..6 LOOP
    INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpide WHERE koodi=toimenpidekoodit[i]),
               urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  END LOOP;
  INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpide WHERE koodi='23151'),
               urakan_nimi || ' ' || 'MHU ja HJU Hoidon johto', (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  -- URAKAN KIINTEÄHINTAISET TYÖT (eli suunnitellut hankinnat)
  FOREACH toimenpidenimi IN ARRAY toimenpidenimet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    FOR i IN 10..12 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
        VALUES (urakan_alkuvuosi, i, 8000 + i*100, testidata_indeksikorjaa(8000 + i*100, urakan_alkuvuosi, i, urakka_id)
, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
          VALUES ((vuosi_ + urakan_alkuvuosi), i, 8000 + i*100, testidata_indeksikorjaa(8000 + i*100, (vuosi_ + urakan_alkuvuosi), i, urakka_id), (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
        VALUES ((5 + urakan_alkuvuosi), i, 8000 + i*100, testidata_indeksikorjaa(8000 + i*100, (5 + urakan_alkuvuosi), i, urakka_id), (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
  END LOOP;

  -- URAKAN KUSTANNUSARVIOIDUT TYÖT
  FOREACH toimenpidenimi IN ARRAY hoito_toimenpidenimiet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    -- UI tarvitsee periaatteessa vain yhden rivin kutakin lajia (kustannussuunnitelmissa), mutta raportoinnissa joka vuoden kuulle pitää olla omansa.
    FOR i IN 10..12 LOOP
            -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
            IF toimenpidenimi = 'Liikenneympäristön hoito TP'
            THEN
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                   tehtavaryhma, toimenpideinstanssi, sopimus)
                VALUES -- kolmansien osapuolien aiheuttamat vahingot
                       (urakan_alkuvuosi, i, 5000,
                        testidata_indeksikorjaa(5000, urakan_alkuvuosi, i, urakka_id),
                        'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Äkilliset hoitotyöt
                       (urakan_alkuvuosi, i, 20000,
                        testidata_indeksikorjaa(20000, urakan_alkuvuosi, i, urakka_id),
                        'akillinen-hoitotyo'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Tunneleiden hoidot
                       (urakan_alkuvuosi, i, 10000,
                        testidata_indeksikorjaa(10000, urakan_alkuvuosi, i, urakka_id),
                        'muut-rahavaraukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             -- Tunneleiden hoito Liikenneympäristön hoito toimenpiteelle
                                                             THEN '4342cd30-a9b7-4194-94ee-00c0ce1f6fc6'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus);
            END IF;
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES (urakan_alkuvuosi, i, 1000,
                  testidata_indeksikorjaa(1000, urakan_alkuvuosi, i,  urakka_id),
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
              -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
              IF toimenpidenimi = 'Liikenneympäristön hoito TP'
              THEN
                  INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                     tehtavaryhma, toimenpideinstanssi, sopimus)
                  VALUES -- kolmansien osapuolien aiheuttamat vahingot
                         ((vuosi_ + urakan_alkuvuosi), i, 100,
                          testidata_indeksikorjaa(100, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                          'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                          (SELECT id
                             FROM tehtava
                            WHERE yksiloiva_tunniste = CASE
                                                           WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                               THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                                END),
                          NULL,
                          (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                          urakan_sopimus),
                         -- Äkilliset hoitotyöt
                         ((vuosi_ + urakan_alkuvuosi), i, 20000,
                          testidata_indeksikorjaa(20000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                          'akillinen-hoitotyo'::TOTEUMATYYPPI,
                          (SELECT id
                             FROM tehtava
                            WHERE yksiloiva_tunniste = CASE
                                                           WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                               THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                                END),
                          NULL,
                          (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                          urakan_sopimus),
                         -- Tunneleiden hoidot
                         ((vuosi_ + urakan_alkuvuosi), i, 10000,
                          testidata_indeksikorjaa(10000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                          'muut-rahavaraukset'::TOTEUMATYYPPI,
                          (SELECT id
                             FROM tehtava
                            WHERE yksiloiva_tunniste = CASE
                                                           WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                               -- Tunneleiden hoito Liikenneympäristön hoito toimenpiteelle
                                                               THEN '4342cd30-a9b7-4194-94ee-00c0ce1f6fc6'::UUID
                                END),
                          NULL,
                          (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                          urakan_sopimus);
              END IF;
        -- Laskutukseen perustusvat toimenpidekustannukset
        IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
            VALUES ((vuosi_ + urakan_alkuvuosi), i, 1000,
                    testidata_indeksikorjaa(1000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                    'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        END IF;
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
            -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
            IF toimenpidenimi = 'Liikenneympäristön hoito TP'
            THEN
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                   tehtavaryhma, toimenpideinstanssi, sopimus)
                VALUES -- kolmansien osapuolien aiheuttamat vahingot
                       ((5 + urakan_alkuvuosi), i, 100,
                        testidata_indeksikorjaa(100, (5 + urakan_alkuvuosi), i, urakka_id),
                        'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Äkilliset hoitotyöt
                       ((5 + urakan_alkuvuosi), i, 20000,
                        testidata_indeksikorjaa(20000, (5 + urakan_alkuvuosi), i, urakka_id),
                        'akillinen-hoitotyo'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Tunneleiden hoidot
                       ((5 + urakan_alkuvuosi), i, 10000,
                        testidata_indeksikorjaa(10000, (5 + urakan_alkuvuosi), i, urakka_id),
                        'muut-rahavaraukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             -- Tunneleiden hoito Liikenneympäristön hoito toimenpiteelle
                                                             THEN '4342cd30-a9b7-4194-94ee-00c0ce1f6fc6'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus);
            END IF;
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((5 + urakan_alkuvuosi), i, 1000,
                  testidata_indeksikorjaa(1000, (5 + urakan_alkuvuosi), i, urakka_id),
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
  END LOOP;

  -- URAKAN RAHAVARAUKSET LUPAUKSIIN

  toimenpidenimi = 'MHU Ylläpito TP';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                             tehtavaryhma, toimenpideinstanssi, sopimus)

              -- Lisää rahavaraus lupaukseen 1
          VALUES (urakan_alkuvuosi, i, 5000,
                  testidata_indeksikorjaa(5000, urakan_alkuvuosi, i, urakka_id),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus),

              -- Lisää "Muut tavoitehintaan vaikuttavat rahavaraukset"
                 (urakan_alkuvuosi, i, 4000,
                  testidata_indeksikorjaa(4000, urakan_alkuvuosi, i, urakka_id),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
            INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                               tehtavaryhma, toimenpideinstanssi, sopimus)
                -- Lisää rahavaraus lupaukseen 1
            VALUES ((vuosi_ + urakan_alkuvuosi), i, 5000,
                    testidata_indeksikorjaa(5000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                    'muut-rahavaraukset'::TOTEUMATYYPPI,
                    (SELECT id
                       FROM tehtava
                      WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'),
                    NULL,
                    (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                    urakan_sopimus),

                   -- Lisää "Muut tavoitehintaan vaikuttavat rahavaraukset"
                   ((vuosi_ + urakan_alkuvuosi), i, 4000,
                    testidata_indeksikorjaa(4000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                    'muut-rahavaraukset'::TOTEUMATYYPPI,
                    (SELECT id
                       FROM tehtava
                      WHERE yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92'),
                    NULL,
                    (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                    urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                             tehtavaryhma, toimenpideinstanssi, sopimus)
              -- Lisää rahavaraus lupaukseen 1
          VALUES ((5 + urakan_alkuvuosi), i, 5000,
                  testidata_indeksikorjaa(5000, (5 + urakan_alkuvuosi), i, urakka_id),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus),

                 -- Lisää "Muut tavoitehintaan vaikuttavat rahavaraukset"
                 ((5 + urakan_alkuvuosi), i, 4000,
                  testidata_indeksikorjaa(4000, (5 + urakan_alkuvuosi), i, urakka_id),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus);
  END LOOP;

  -- URAKAN 'MHU ja HJU Hoidon johto'

  toimenpidenimi = 'MHU ja HJU Hoidon johto';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                (urakan_alkuvuosi, i, 700,
                 testidata_indeksikorjaa(700, urakan_alkuvuosi, i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                (urakan_alkuvuosi, i, 9000,
                 testidata_indeksikorjaa(9000, urakan_alkuvuosi, i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                (urakan_alkuvuosi, i, 9100,
                 testidata_indeksikorjaa(9100, urakan_alkuvuosi, i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- Erillishankinnat
                ((vuosi_ + urakan_alkuvuosi), i, 700,
                 testidata_indeksikorjaa(700, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((vuosi_ + urakan_alkuvuosi), i, 9000,
                 testidata_indeksikorjaa(9000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((vuosi_ + urakan_alkuvuosi), i, 9100,
                 testidata_indeksikorjaa(9100, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((5 + urakan_alkuvuosi), i, 700,
                 testidata_indeksikorjaa(700, (5 + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((5 + urakan_alkuvuosi), i, 9000,
                 testidata_indeksikorjaa(9000, (5 + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((5 + urakan_alkuvuosi), i, 9100,
                 testidata_indeksikorjaa(9100, (5 + urakan_alkuvuosi), i, urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;


  ----------------------------------
  -- URAKAN YKSIKKÖHINTAISET TYÖT --
  ----------------------------------
  -- Hankintakustannukset (ennen urakkaa) ennen-urakkaa = TRUE
  -- Erikoistapaus: Luodaan ensimmäiselle hoitovuodelle 4 riviä joissa osa-kuukaudesta kerroin on 1 ja yksi rivi jossa kerroin on 0.5.
  -- Tämä edustaa "ennen urakkaa" toimenkuvaa. Rivit summataan toisaalla siten, että joka riviltä lasketaan tunnit * tuntipalkka * osa-kuukaudesta
  -- Esim. Jos syötetty "Hankintakustannukset (ennen urakkaa)" tunnit 1 ja tuntipalkka 1 -> tulee summa: 4.5, koska 1*1*1 + 1*1*1 + 1*1*1 + 1*1*1 + 1*1*0.5 = 4.5.
  FOR i IN 1..4
  LOOP
    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, "ennen-urakkaa", luotu, "toimenkuva-id")
      VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 4500, 40, testidata_indeksikorjaa(40, (SELECT extract(year from NOW()))::INTEGER, 10, urakka_id), (SELECT extract(year from NOW())), 10, TRUE, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
  END LOOP;
  INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, "ennen-urakkaa", luotu, "toimenkuva-id", "osa-kuukaudesta")
    VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 4500, 40, testidata_indeksikorjaa(40, (SELECT extract(year FROM NOW()))::INTEGER, 10, urakka_id),(SELECT extract(year from NOW())), 10, TRUE, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'), 0.5);

  FOR i IN 10..12 LOOP
    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
      VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
        VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i, urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i, urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i, urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i, urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i, urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

      IF (5 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
        VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
      END IF;
      IF (4 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
        VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi),  i, urakka_id), vuosi_ + urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
      END IF;
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    IF toimenpidenimi = 'Soratien hoito TP'
      THEN
        -- Jätetään soratiet suunnittelematta kuluvalle vuodelle
        EXIT;
    END IF;
    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
      VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

      IF (5 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
        VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
      END IF;
      IF (4 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
        VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
      END IF;
  END LOOP;
END $$;

-- Kemin MHU-urakka
--'Hallinnolliset toimenpiteet TP'
DO $$
DECLARE
  toimenpidenimet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP', 'Päällystepaikkaukset TP', 'MHU Ylläpito TP', 'MHU Korvausinvestointi TP'];
  hoito_toimenpidenimiet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP'];
  toimenpidekoodit TEXT[] := ARRAY ['23104', '23116', '23124', '20107', '20191', '14301'];
  urakan_nimi TEXT := 'Kemin MHU testiurakka (5. hoitovuosi)';
  toimenpideinstanssin_nimi TEXT;
  toimenpidenimi TEXT;
  urakan_sopimus INT := (SELECT id FROM sopimus WHERE nimi = 'Kemin MHU testiurakan sopimus');
  i INTEGER;
  vuosi_ INTEGER;
  urakka_id INTEGER := (SELECT id FROM urakka where nimi = urakan_nimi);
  urakan_alkuvuosi INT := (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi)));
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOR i IN 1..6 LOOP
    INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpide WHERE koodi=toimenpidekoodit[i]),
               urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  END LOOP;
  INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpide WHERE koodi='23151'),
               urakan_nimi || ' ' || 'MHU ja HJU Hoidon johto', (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  -- URAKAN KIINTEÄHINTAISET TYÖT (eli suunnitellut hankinnat)
  FOREACH toimenpidenimi IN ARRAY toimenpidenimet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    FOR i IN 10..12 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
      VALUES (urakan_alkuvuosi, i, 8000 + i*100, testidata_indeksikorjaa(8000 + i * 100, urakan_alkuvuosi, i,  urakka_id), (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
       INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
        VALUES ((vuosi_ + urakan_alkuvuosi), i, 8000 + i*100, testidata_indeksikorjaa(8000 + i * 100, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      IF toimenpidenimi = 'Soratien hoito TP'
        THEN
          -- Jätetään soratiet suunnittelematta kuluvalle vuodelle
          EXIT;
      END IF;
        INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, toimenpideinstanssi, sopimus)
          VALUES ((5 + urakan_alkuvuosi), i, 8000 + i*100, testidata_indeksikorjaa(8000 + i * 100, (5 + urakan_alkuvuosi), i,  urakka_id), (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
  END LOOP;

  -- URAKAN KUSTANNUSARVIOIDUT TYÖT
  FOREACH toimenpidenimi IN ARRAY hoito_toimenpidenimiet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    -- UI tarvitsee periaatteessa vain yhden rivin kutakin lajia (kustannussuunnitelmissa), mutta raportoinnissa joka vuoden kuulle pitää olla omansa.
    FOR i IN 10..12 LOOP
            -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
            IF toimenpidenimi = 'Liikenneympäristön hoito TP'
            THEN
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                   tehtavaryhma, toimenpideinstanssi, sopimus)
                VALUES -- kolmansien osapuolien aiheuttamat vahingot
                       (urakan_alkuvuosi, i, 5000,
                        testidata_indeksikorjaa(5000, urakan_alkuvuosi, i, urakka_id),
                        'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '49b7388b-419c-47fa-9b1b-3797f1fab21d'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'b3a7a210-4ba6-4555-905c-fef7308dc5ec'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Äkilliset hoitotyöt
                       (urakan_alkuvuosi, i, 20000,
                        testidata_indeksikorjaa(20000, urakan_alkuvuosi, i, urakka_id),
                        'akillinen-hoitotyo'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '1f12fe16-375e-49bf-9a95-4560326ce6cf'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'd373c08b-32eb-4ac2-b817-04106b862fb1'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus);
            END IF;
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES (urakan_alkuvuosi, i, 1000,
                  testidata_indeksikorjaa(1000, urakan_alkuvuosi, i,  urakka_id),
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus);
      END IF;
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
              -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
              IF toimenpidenimi = 'Liikenneympäristön hoito TP'
              THEN
                  INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                     tehtavaryhma, toimenpideinstanssi, sopimus)
                  VALUES -- kolmansien osapuolien aiheuttamat vahingot
                         ((vuosi_ + urakan_alkuvuosi), i, 100,
                          testidata_indeksikorjaa(100, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                          'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                          (SELECT id
                             FROM tehtava
                            WHERE yksiloiva_tunniste = CASE
                                                           WHEN (toimenpidenimi = 'Talvihoito TP')
                                                               THEN '49b7388b-419c-47fa-9b1b-3797f1fab21d'::UUID
                                                           WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                               THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                                                           WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                               THEN 'b3a7a210-4ba6-4555-905c-fef7308dc5ec'::UUID
                                END),
                          NULL,
                          (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                          urakan_sopimus),
                         -- Äkilliset hoitotyöt
                         ((vuosi_ + urakan_alkuvuosi), i, 20000,
                          testidata_indeksikorjaa(20000, (vuosi_ + urakan_alkuvuosi), i, urakka_id),
                          'akillinen-hoitotyo'::TOTEUMATYYPPI,
                          (SELECT id
                             FROM tehtava
                            WHERE yksiloiva_tunniste = CASE
                                                           WHEN (toimenpidenimi = 'Talvihoito TP')
                                                               THEN '1f12fe16-375e-49bf-9a95-4560326ce6cf'::UUID
                                                           WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                               THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                                                           WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                               THEN 'd373c08b-32eb-4ac2-b817-04106b862fb1'::UUID
                                END),
                          NULL,
                          (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                          urakan_sopimus);
              END IF;
        -- Laskutukseen perustusvat toimenpidekustannukset
        IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
            INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
            VALUES ((vuosi_ + urakan_alkuvuosi), i, 1000,
                    testidata_indeksikorjaa(1000, (vuosi_ + urakan_alkuvuosi), i,  urakka_id),
                    'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        END IF;
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
            -- Lisää rivejä akillinen-hoitotyo ja vahinkojen-korjaukset "Liikenneympäristön hoito" toimenpiteelle.
            IF toimenpidenimi = 'Liikenneympäristön hoito TP'
            THEN
                INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                                   tehtavaryhma, toimenpideinstanssi, sopimus)
                VALUES -- kolmansien osapuolien aiheuttamat vahingot
                       ((5 + urakan_alkuvuosi), i, 100,
                        testidata_indeksikorjaa(100, (5 + urakan_alkuvuosi), i, urakka_id),
                        'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '49b7388b-419c-47fa-9b1b-3797f1fab21d'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '63a2585b-5597-43ea-945c-1b25b16a06e2'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'b3a7a210-4ba6-4555-905c-fef7308dc5ec'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus),
                       -- Äkilliset hoitotyöt
                       ((5 + urakan_alkuvuosi), i, 20000,
                        testidata_indeksikorjaa(20000, (5 + urakan_alkuvuosi), i, urakka_id),
                        'akillinen-hoitotyo'::TOTEUMATYYPPI,
                        (SELECT id
                           FROM tehtava
                          WHERE yksiloiva_tunniste = CASE
                                                         WHEN (toimenpidenimi = 'Talvihoito TP')
                                                             THEN '1f12fe16-375e-49bf-9a95-4560326ce6cf'::UUID
                                                         WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP')
                                                             THEN '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'::UUID
                                                         WHEN (toimenpidenimi = 'Soratien hoito TP')
                                                             THEN 'd373c08b-32eb-4ac2-b817-04106b862fb1'::UUID
                              END),
                        NULL,
                        (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                        urakan_sopimus);
            END IF;
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((5 + urakan_alkuvuosi), i, 1000,
                  testidata_indeksikorjaa(1000, (5 + urakan_alkuvuosi), i,  urakka_id),
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus);
      END IF;
    END LOOP;
  END LOOP;

    -- URAKAN RAHAVARAUKSET LUPAUKSIIN

  toimenpidenimi = 'MHU Ylläpito TP';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                             tehtavaryhma, toimenpideinstanssi, sopimus)
              -- Lisää rahavaraus lupaukseen 1
          VALUES (urakan_alkuvuosi, i, 5000,
                  testidata_indeksikorjaa(5000, urakan_alkuvuosi, i, urakka_id),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus),

                 -- Lisää "Muut tavoitehintaan vaikuttavat rahavaraukset"
                 (urakan_alkuvuosi, i, 4000,
                  testidata_indeksikorjaa(4000, urakan_alkuvuosi, i, urakka_id),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
            INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                               tehtavaryhma, toimenpideinstanssi, sopimus)
                -- Lisää rahavaraus lupaukseen 1
            VALUES ((vuosi_ + urakan_alkuvuosi), i, 5000,
                    testidata_indeksikorjaa(5000, urakan_alkuvuosi, i, (vuosi_ + urakan_alkuvuosi)),
                    'muut-rahavaraukset'::TOTEUMATYYPPI,
                    (SELECT id
                       FROM tehtava
                      WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'),
                    NULL,
                    (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                    urakan_sopimus),

                   -- Lisää "Muut tavoitehintaan vaikuttavat rahavaraukset"
                   ((vuosi_ + urakan_alkuvuosi), i, 4000,
                    testidata_indeksikorjaa(4000, urakan_alkuvuosi, i, (vuosi_ + urakan_alkuvuosi)),
                    'muut-rahavaraukset'::TOTEUMATYYPPI,
                    (SELECT id
                       FROM tehtava
                      WHERE yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92'),
                    NULL,
                    (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                    urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava,
                                             tehtavaryhma, toimenpideinstanssi, sopimus)
              -- Lisää rahavaraus lupaukseen 1
          VALUES ((5 + urakan_alkuvuosi), i, 5000,
                  testidata_indeksikorjaa(5000, urakan_alkuvuosi, i, (5 + urakan_alkuvuosi)),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus),

                 -- Lisää "Muut tavoitehintaan vaikuttavat rahavaraukset"
                 ((5 + urakan_alkuvuosi), i, 4000,
                  testidata_indeksikorjaa(4000, urakan_alkuvuosi, i, (5 + urakan_alkuvuosi)),
                  'muut-rahavaraukset'::TOTEUMATYYPPI,
                  (SELECT id
                     FROM tehtava
                    WHERE yksiloiva_tunniste = '548033b7-151d-4202-a2d8-451fba284d92'),
                  NULL,
                  (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                  urakan_sopimus);
  END LOOP;

  -- URAKAN 'MHU ja HJU Hoidon johto'

  toimenpidenimi = 'MHU ja HJU Hoidon johto';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                (urakan_alkuvuosi, i, 700,
                 testidata_indeksikorjaa(700, urakan_alkuvuosi, i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                (urakan_alkuvuosi, i, 9000,
                 testidata_indeksikorjaa(9000, urakan_alkuvuosi, i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                (urakan_alkuvuosi, i, 9100,
                 testidata_indeksikorjaa(9100, urakan_alkuvuosi, i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- Erillishankinnat
                ((vuosi_ + urakan_alkuvuosi), i, 700,
                 testidata_indeksikorjaa(700, (vuosi_ + urakan_alkuvuosi), i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((vuosi_ + urakan_alkuvuosi), i, 9000,
                 testidata_indeksikorjaa(9000, (vuosi_ + urakan_alkuvuosi), i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((vuosi_ + urakan_alkuvuosi), i, 9100,
                 testidata_indeksikorjaa(9100, (vuosi_ + urakan_alkuvuosi), i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, summa_indeksikorjattu, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((5 + urakan_alkuvuosi), i, 700,
                 testidata_indeksikorjaa(700, (5 + urakan_alkuvuosi), i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE yksiloiva_tunniste='37d3752c-9951-47ad-a463-c1704cf22f4c'::UUID),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((5 + urakan_alkuvuosi), i, 9000,
                 testidata_indeksikorjaa(9000, (5 + urakan_alkuvuosi), i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '8376d9c4-3daf-4815-973d-cd95ca3bb388'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((5 + urakan_alkuvuosi), i, 9100,
                 testidata_indeksikorjaa(9100, (5 + urakan_alkuvuosi), i,  urakka_id),
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM tehtava WHERE yksiloiva_tunniste = '53647ad8-0632-4dd3-8302-8dfae09908c8'::UUID),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  ----------------------------------
  -- URAKAN YKSIKKÖHINTAISET TYÖT --
  ----------------------------------

  -- Hankintakustannukset (ennen urakkaa) ennen-urakkaa = TRUE
  -- Erikoistapaus: Luodaan ensimmäiselle hoitovuodelle 4 riviä joissa osa-kuukaudesta kerroin on 1 ja yksi rivi jossa kerroin on 0.5.
  -- Tämä edustaa "ennen urakkaa" toimenkuvaa. Rivit summataan toisaalla siten, että joka riviltä lasketaan tunnit * tuntipalkka * osa-kuukaudesta
  -- Esim. Jos syötetty "Hankintakustannukset (ennen urakkaa)" tunnit 1 ja tuntipalkka 1 -> tulee summa: 4.5, koska 1*1*1 + 1*1*1 + 1*1*1 + 1*1*1 + 1*1*0.5 = 4.5.
  FOR i IN 1..4
      LOOP
          INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, "ennen-urakkaa", luotu, "toimenkuva-id")
          VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 4500, 40, testidata_indeksikorjaa(40, (SELECT extract(year from NOW()))::INTEGER, 10, urakka_id), (SELECT extract(year from NOW())), 10, TRUE, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
      END LOOP;
  INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, "ennen-urakkaa", luotu, "toimenkuva-id", "osa-kuukaudesta")
  VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 4500, 40, testidata_indeksikorjaa(40, (SELECT extract(year FROM NOW()))::INTEGER, 10, urakka_id),(SELECT extract(year from NOW())), 10, TRUE, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'), 0.5);

  FOR i IN 10..12 LOOP
    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
      VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, urakan_alkuvuosi, i, urakka_id), urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
        VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
               ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

      IF (5 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
          VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
      END IF;
      IF (4 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
          VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (vuosi_ + urakan_alkuvuosi), i,  urakka_id), vuosi_ + urakan_alkuvuosi, i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
      END IF;
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    IF toimenpidenimi = 'Soratien hoito TP'
      THEN
        -- Jätetään soratiet suunnittelematta kuluvalle vuodelle
        EXIT;
    END IF;
    INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
      VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
             ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

      IF (5 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
          VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
      END IF;
      IF (4 <= i) AND (i <= 8)
      THEN
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, tuntipalkka_indeksikorjattu, vuosi, kuukausi, luotu, "toimenkuva-id")
          VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, testidata_indeksikorjaa(30, (5 + urakan_alkuvuosi), i,  urakka_id), (5 + urakan_alkuvuosi), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
      END IF;
  END LOOP;

END $$;

-- Uudempien MHU:iden toimenpideinstanssit
DO $$
DECLARE
  toimenpidenimet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP', 'Päällystepaikkaukset TP', 'MHU Ylläpito TP', 'MHU Korvausinvestointi TP', 'MHU ja HJU Hoidon johto'];
  toimenpidekoodit TEXT[] := ARRAY ['23104', '23116', '23124', '20107', '20191', '14301', '23151'];
  urakat TEXT[] := ARRAY ['Ivalon MHU testiurakka (uusi)', 'Iin MHU 2021-2026', 'Tampereen MHU 2022-2026', 'Raahen MHU 2023-2028'];
  urakan_nimi TEXT;
  i INTEGER;
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOREACH urakan_nimi SLICE 0 IN ARRAY urakat LOOP
      FOR i IN 1..7 LOOP
        INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
           VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpide WHERE koodi=toimenpidekoodit[i]),
                   urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
                   (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
      END LOOP;
  END LOOP;
END $$;

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM toimenpide WHERE koodi='23104'), 'Oulu Aktiivinen Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT loppupvm FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM toimenpide WHERE koodi='23116'), 'Oulu Aktiivinen Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT loppupvm FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT id FROM toimenpide WHERE koodi='23124'), 'Oulu Aktiivinen Sorateiden hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), (SELECT loppupvm FROM urakka WHERE nimi='Aktiivinen Oulu Testi'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

-- Toimenpidekoodi-taulun apitunnus-kentän testaamista varten
INSERT into tehtava (nimi, tehtavaryhma, hinnoittelu, yksikko, jarjestys, api_seuranta, api_tunnus, emo, luotu, luoja, "mhu-tehtava?", voimassaolo_alkuvuosi, voimassaolo_loppuvuosi) VALUES
('Apitunnus-testitehtävä', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'{kokonaishintainen,yksikkohintainen}' :: hinnoittelutyyppi [], 'kpl',	999, TRUE, 987654,
(select id from toimenpide where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE, null, null);

INSERT into tehtava (nimi, tehtavaryhma, hinnoittelu, yksikko, jarjestys, api_seuranta, api_tunnus, emo, luotu, luoja, "mhu-tehtava?", voimassaolo_alkuvuosi, voimassaolo_loppuvuosi) VALUES
('Apitunnus-testitehtävä, tupla', (select id from tehtavaryhma where nimi = 'Talvihoito (A)'),	'{kokonaishintainen,yksikkohintainen}' :: hinnoittelutyyppi [], 'kpl',	998, TRUE, 1370,
 (select id from toimenpide where koodi = '23104'), current_timestamp, (select id from kayttaja where kayttajanimi = 'Integraatio'), TRUE, 1999, 2003);
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Utajärven päällystysurakka'), (SELECT id FROM toimenpide WHERE taso=3 AND nimi='Päällystyksen yksikköhintaiset työt'), 'Utajärven Päällystyksen yksikköhintaiset työt', (SELECT alkupvm FROM urakka WHERE nimi='Utajärven päällystysurakka'),(SELECT loppupvm FROM urakka WHERE nimi='Utajärven päällystysurakka'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
