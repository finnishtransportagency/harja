INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2005-12-31', 3, 'vrk', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2006-01-01', '2006-09-30', 9, 'vrk', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2005-10-01', '2005-12-31', 525.73, 'km', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2006-01-01', '2006-09-30', 1525.321, 'km', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 525.73, 'km', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2016-10-01', '2016-12-31', 3, 'vrk', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2017-01-01', '2017-09-30', 9, 'vrk', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 3, 'vrk', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2014-01-01', '2014-09-30', 9, 'vrk', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen'), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 866.0, 'km', 525.50, (SELECT id FROM toimenpidekoodi WHERE taso=4 AND nimi='Pensaiden poisto'), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (select id from sopimus where urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null));

-- Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 20, 'm2', 1, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 7 mm: Pyörätien jatkeet ja suojatiet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'kpl', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nopeusrajoitusmerkinnät'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'kpl', 5, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Väistämisviivan yksi kolmio hainhammas'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 60, 'm2', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pysäytysviiva'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'm2', 4, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Sulkualueet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'm2', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Pyörätien jatkeet ja suojatiet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 50, 'kpl', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, pitkä (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Pienmerkinnät massalla paksuus 3 mm: Nuoli, lyhyt (1-, 2- ja 3-kärkiset sekä ajokaistan päättymisnuoli)'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 400, 'm2', 4, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnät massalla paksuus 7 mm: Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 190, 'm2', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnät massalla paksuus 7 mm: Reunaviiva ja reunaviivan jatke'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'm2', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm: Sulkuviiva ja varoitusviiva keltainen'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 2000, 'm2', 4, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnät massalla, paksuus 3 mm: Keskiviiva, ajokaistaviiva, ohjausviiva valkoinen'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'm2', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnät massalla, paksuus 3mm: Reunaviiva ja reunaviivan jatke'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 20, 'm2', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Muut pienmerkinnät'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Nuolet ja nopeusrajoitusmerkinnät ja väistämisviivat'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'm2', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Sulkualueet'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 200, 'm2', 1, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Linjamerkinnän upotusjyrsintä'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 60, 'jm', 4.5, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sini-aallonmuotoinen jyrsintä, reunaviiva, 2 ajr tie: lev 30 cm, aallonpit 60 cm, syv 6 mm aallonharjalla, syv 13 mm aallon pohjalla'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 40, 'jm', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sini-aallonmuotoinen jyrsintä, reunaviiva, 1 ajr tie: lev 30 cm, aallonpit 60 cm, syv 6 mm aallonharjalla, syv 13 mm aallon pohjalla'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'jm', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 2 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 30, 'jm', 2, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, keskiviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2013-10-01', '2013-12-31', 50, 'jm', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Täristävät merkinnät: sylinterijyrsintä, reunaviiva, 1 ajr tie: lev 30 cm, pit 13-15 cm, merkintäväli 60 cm, syvyys 15 mm'), (SELECT id FROM urakka WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM sopimus WHERE nimi = 'Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'));

-- Tievalaistuksen palvelusopimus 2015-2020: sopimuskausi 2015
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 286, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 4455, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: PpNa 35 - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 35 - 150 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 250 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Hg 50 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 120, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 35, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Sytytin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: PpNa 35  - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Monimetalli 35 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 2, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 2, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: LED-yksikkö VP 2221/2223 valaisimeen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 7, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Jalustan vaihto SJ 4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 7, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Teräskartiopylväs HE3 h=10m V=2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Myötäävä puupylväs h=10m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 15, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Puupylväsvarsi V= 1.0 - 2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 50 - 70 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 50, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, 2-tehokuristin ja tehonvaihtorele, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=6 m, K4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=10 m, AL4a'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 2, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED VP 2221/2223 M1 - M3'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 10, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Maajakokeskus'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 15, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Haruksen uusiminen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 50, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Puupylvään oikaisu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 80, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 100, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 5, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 1500, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Asentaja'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 150, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Tr- kaivuri'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 50, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Törmäysvaimennin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 360, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Kuorma-auto nosturilla'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 360, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Nostolava alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2015-01-01','2015-12-31', 20, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Nostolava yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

-- Tievalaistuksen palvelusopimus 2015-2020: sopimuskausi 2016
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 386, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 6173, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: PpNa 35 - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 16, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 35 - 150 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: Monimetalli 250 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen ryhmävaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Hg 50 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 25, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 50 - 100 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 140, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: SpNa 150 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 45, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Sytytin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: PpNa 35  - 180 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 5, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Monimetalli 35 - 400 W'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 2, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: Loistelamppu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 2, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Lamppujen yksittäisvaihto: LED-yksikkö VP 2221/2223 valaisimeen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Jalustan vaihto SJ 4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Teräskartiopylväs HE3 h=10m V=2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 25, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Myötäävä puupylväs h=10m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 20, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Puupylväsvarsi V= 1.0 - 2,5m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 50 - 70 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 65, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 25, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin SpNa 100 - 250 W, 2-tehokuristin ja tehonvaihtorele, lamppuineen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 0, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=6 m, K4'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 56, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED, h=10 m, AL4a'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 2, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Valaisin LED VP 2221/2223 M1 - M3'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 10, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Maajakokeskus'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 20, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden vaihto: Haruksen uusiminen'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 60, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Puupylvään oikaisu'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 90, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 5, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Metallipylvään oikaisu yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 130, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 5, 'kpl', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Valaistuslaitteiden korjaus: Valaisinvarsien suuntaus yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 1800, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Asentaja'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 150, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Tr- kaivuri'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 50, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Törmäysvaimennin'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 450, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Kuorma-auto nosturilla'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 450, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Nostolava alle 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2016-01-01','2016-12-31', 20, 'h', 3, null, (SELECT id FROM toimenpidekoodi WHERE nimi = 'Nostolava yli 13 m'), (SELECT id FROM urakka WHERE nimi = 'Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM sopimus WHERE nimi = 'Oulun valaistuksen palvelusopimuksen pääsopimus 2015-2020'));

-- talvihoidon laaja toimenpide Oulun ja Pudasjärven urakoille
-- talvihoidon  laaja toimenpide 23104
-- soratien hoidon laaja toimenpide 23124
-- hoitokausi 2005-2006
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Oulu Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Oulu Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Oulu Sorateiden hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Oulu Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Oulu Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Oulu Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Pudasjärvi Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Pudasjärvi Sorateiden hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Pudasjärvi Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Pori Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Pori Sorateiden hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Pori Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), (SELECT loppupvm FROM urakka WHERE nimi='Porin alueurakka 2007-2012'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun tiemerkinnän palvelusopimus 2013-2018'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20123'), 'Tiemerkinnän TP', '2013-10-01','2018-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Pirkanmaan tiemerkinnän palvelusopimus 2013-2018'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20123'), 'Pirkanmaan Tiemerkinnän TP', '2013-01-01','2018-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Muhoksen päällystysurakka'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20101'), 'Muhos Ajoradan päällyste TP', '2007-01-01','2012-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Porintien päällystysurakka'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20101'), 'Porintien Ajoradan päällyste TP', '2007-01-01','2012-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun valaistuksen palvelusopimus 2013-2050'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20172'), 'Oulu Valaistuksen korjaus TP', '2013-01-01','2018-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Kempeleen valaistusurakka'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20172'), 'Kempele Valaistuksen korjaus TP', '2007-10-01','2012-09-30', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tiemerkintöjen palvelusopimus KAS ELY 2013 - 2017'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20123'), 'Valaistuksen korjaus TP', '2013-10-01','2015-09-30', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka,toimenpide,nimi,alkupvm,loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tievalaistuksen palvelusopimus 2015-2020'), (SELECT id FROM toimenpidekoodi WHERE taso=3 AND koodi='20172'), 'Oulu Valaistuksen korjaus TP', '2015-01-01','2020-12-31', 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
-- Luodaan Kajaanin, Vantaan ja Espoon urakalle tärkeimmät toimenpideinstanssit
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Kajaani Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Kajaani Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Kajaani Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Vantaa Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Vantaa Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Vantaa Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Vantaan alueurakka 2009-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Espoo Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Espoo Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku'),
  ((SELECT id FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Espoo Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), (SELECT loppupvm FROM urakka WHERE nimi='Espoon alueurakka 2014-2019'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Tampere Talvihoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT loppupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Tampere Liikenneympäristön hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT loppupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Tampere Sorateiden hoito TP 2014-2019', (SELECT alkupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), (SELECT loppupvm FROM urakka WHERE nimi='Tampereen alueurakka 2017-2022'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');



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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
         FROM toimenpidekoodi
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
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Ammattimies' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 2, null, (SELECT id
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 3, null, (SELECT id
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Työnjohto' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Helsingin väyläyksikön väylänhoito ja -käyttö, Itäinen SL'), (SELECT id FROM sopimus WHERE nimi = 'Helsingin väyläyksikön pääsopimus'));

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
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Ammattimies' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Pyhäselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Pyhäselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 2, null, (SELECT id
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Pyhäselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Pyhäselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 3, null, (SELECT id
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Työnjohto' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Pyhäselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Pyhäselän pääsopimus'));


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
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Ammattimies' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Rentoselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Rentoselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 2, null, (SELECT id
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Rentoselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Rentoselän pääsopimus'));
INSERT INTO yksikkohintainen_tyo (alkupvm, loppupvm, maara, yksikko, yksikkohinta, kohde, tehtava, urakka, sopimus) VALUES ('2017-08-01', '2018-07-31', null, 'h', 3, null, (SELECT id
                                                                                                                                                                             FROM toimenpidekoodi
                                                                                                                                                                             WHERE nimi = 'Henkilöstö: Työnjohto' AND koodi ILIKE('VV%')), (SELECT id FROM urakka WHERE nimi = 'Rentoselän urakka'), (SELECT id FROM sopimus WHERE nimi = 'Rentoselän pääsopimus'));


-- Kanavien toimenpideinstanssit
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Saimaan kanava'), (SELECT id FROM toimenpidekoodi WHERE koodi='27105'), 'Saimaan kanava, sopimukseen kuuluvat työt, TP', (SELECT alkupvm FROM urakka WHERE nimi='Saimaan kanava'), (SELECT loppupvm FROM urakka WHERE nimi='Saimaan kanava'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm) VALUES ((SELECT id FROM urakka WHERE nimi='Saimaan kanava'), (SELECT id FROM toimenpidekoodi WHERE nimi = 'Erikseen tilatut työt' AND emo = (SELECT id FROM toimenpidekoodi WHERE nimi = 'Väylänhoito')), 'Testitoimenpideinstanssi', '2017-01-01', '2090-01-01');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Joensuun kanava'), (SELECT id FROM toimenpidekoodi WHERE koodi='27105'), 'Joensuun kanava, sopimukseen kuuluvat työt, TP', (SELECT alkupvm FROM urakka WHERE nimi='Joensuun kanava'), (SELECT loppupvm FROM urakka WHERE nimi='Joensuun kanava'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

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
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM toimenpidekoodi WHERE koodi='23104'), 'Oulu MHU Talvihoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM toimenpidekoodi WHERE koodi='23116'), 'Oulu MHU Liikenneympäristön hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM toimenpidekoodi WHERE koodi='23124'), 'Oulu MHU Soratien hoito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM toimenpidekoodi WHERE koodi='23151'), 'Oulu MHU Hallinnolliset toimenpiteet TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM toimenpidekoodi WHERE koodi='20107'), 'Oulu MHU Päällystepaikkaukset TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM toimenpidekoodi WHERE koodi='20191'), 'Oulu MHU MHU Ylläpito TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku) VALUES ((SELECT id FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT id FROM toimenpidekoodi WHERE koodi='14301'), 'Oulu MHU MHU Korvausinvestointi TP', (SELECT alkupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), (SELECT loppupvm FROM urakka WHERE nimi='Oulun MHU 2019-2024'), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');

INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus) VALUES (2020, 2, 16666, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP' ), null);

INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi) VALUES (2020, 2, 150, 'akillinen-hoitotyo'::TOTEUMATYYPPI, null, null, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Talvihoito TP'));
INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi) VALUES (2020, 4, 220, 'laskutettava-tyo'::TOTEUMATYYPPI, null, (select id from tehtavaryhma where nimi = 'Rumpujen kunnossapito ja uusiminen (päällystetty tie)'), (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'));
INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi) VALUES (2020, 4, 330, 'laskutettava-tyo'::TOTEUMATYYPPI, null, (select id from tehtavaryhma where nimi = 'Rumpujen kunnossapito ja uusiminen (soratie)'), (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'));
INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi) VALUES (2020, 6, 500, 'akillinen-hoitotyo'::TOTEUMATYYPPI, (select id from toimenpidekoodi where nimi = 'Äkillinen hoitotyö' and emo = (select id from toimenpidekoodi where koodi = '23116')), null, (select id from toimenpideinstanssi where nimi = 'Oulu MHU Liikenneympäristön hoito TP'));


INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi) VALUES (2020, 2, 234, 'laskutettava-tyo'::TOTEUMATYYPPI, null, (select id from tehtavaryhma where nimi = 'Erillishankinnat erillishinnoin'), (select id from toimenpideinstanssi where nimi =  'Oulu MHU Hallinnolliset toimenpiteet TP'));
INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi) VALUES (2020, 2, 432, 'laskutettava-tyo'::TOTEUMATYYPPI, null, (select id from tehtavaryhma where nimi = 'Johto- ja hallintokorvaukset'), (select id from toimenpideinstanssi where nimi =  'Oulu MHU Hallinnolliset toimenpiteet TP'));


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
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOR i IN 1..6 LOOP
    INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi=toimenpidekoodit[i]),
               urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  END LOOP;
  INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi='23151'),
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
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
        VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
          VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
        VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
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
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- kolmansien osapuolien aiheuttamat vahingot
               ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
                'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                'akillinen-hoitotyo'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Äkillinen hoitotyö' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES -- kolmansien osapuolien aiheuttamat vahingot
                 ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 100,
                  'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                  (SELECT id
                   FROM toimenpidekoodi
                   WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                         tehtavaryhma=(SELECT id
                                       FROM tehtavaryhma
                                       WHERE nimi = CASE
                                                      WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                      WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                      WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                    END)),
                  NULL,
                  (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                 'akillinen-hoitotyo'::TOTEUMATYYPPI,
                 (SELECT id
                  FROM toimenpidekoodi
                  WHERE nimi='Äkillinen hoitotyö' AND
                        tehtavaryhma=(SELECT id
                                      FROM tehtavaryhma
                                      WHERE nimi = CASE
                                                     WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                     WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                     WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                   END)),
                 NULL,
                 (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        -- Laskutukseen perustusvat toimenpidekustannukset
        IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
            VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                    'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        END IF;
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      -- kolmansien osapuolien aiheuttamat vahingot
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- kolmansien osapuolien aiheuttamat vahingot
               ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 100,
                'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                'akillinen-hoitotyo'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Äkillinen hoitotyö' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
  END LOOP;

  -- URAKAN 'MHU ja HJU Hoidon johto'

  toimenpidenimi = 'MHU ja HJU Hoidon johto';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- Erillishankinnat
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
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
  ennen_urakkaa_id INTEGER;
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOR i IN 1..6 LOOP
    INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi=toimenpidekoodit[i]),
               urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  END LOOP;
  INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi='23151'),
               urakan_nimi || ' ' || 'MHU ja HJU Hoidon johto', (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  -- URAKAN KIINTEÄHINTAISET TYÖT (eli suunnitellut hankinnat)
  FOREACH toimenpidenimi IN ARRAY toimenpidenimet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    FOR i IN 10..12 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
        VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
          VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
        VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
  END LOOP;

  -- URAKAN KUSTANNUSARVIOIDUT TYÖT
  FOREACH toimenpidenimi IN ARRAY hoito_toimenpidenimiet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    -- UI tarvitsee periaatteessa vain yhden rivin kutakin lajia (kustannussuunnitelmissa), mutta raportoinnissa joka vuoden kuulle pitää olla omansa.
    FOR i IN 10..12 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- kolmansien osapuolien aiheuttamat vahingot
               ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
                'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                'akillinen-hoitotyo'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Äkillinen hoitotyö' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES -- kolmansien osapuolien aiheuttamat vahingot
                 ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 100,
                  'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                  (SELECT id
                   FROM toimenpidekoodi
                   WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                         tehtavaryhma=(SELECT id
                                       FROM tehtavaryhma
                                       WHERE nimi = CASE
                                                      WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                      WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                      WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                    END)),
                  NULL,
                  (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                 'akillinen-hoitotyo'::TOTEUMATYYPPI,
                 (SELECT id
                  FROM toimenpidekoodi
                  WHERE nimi='Äkillinen hoitotyö' AND
                        tehtavaryhma=(SELECT id
                                      FROM tehtavaryhma
                                      WHERE nimi = CASE
                                                     WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                     WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                     WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                   END)),
                 NULL,
                 (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        -- Laskutukseen perustusvat toimenpidekustannukset
        IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
            VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                    'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        END IF;
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      -- kolmansien osapuolien aiheuttamat vahingot
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- kolmansien osapuolien aiheuttamat vahingot
               ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 100,
                'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                'akillinen-hoitotyo'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Äkillinen hoitotyö' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
  END LOOP;

  -- URAKAN RAHAVARAUKSET LUPAUKSIIN

  toimenpidenimi = 'MHU Ylläpito TP';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
              'muut-rahavaraukset'::TOTEUMATYYPPI,
              NULL,
              (SELECT id
               FROM tehtavaryhma
               WHERE nimi='TILAAJAN RAHAVARAUS'),
              (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
              urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
              'muut-rahavaraukset'::TOTEUMATYYPPI,
              NULL,
              (SELECT id
               FROM tehtavaryhma
               WHERE nimi='TILAAJAN RAHAVARAUS'),
              (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
              urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
              'muut-rahavaraukset'::TOTEUMATYYPPI,
              NULL,
              (SELECT id
               FROM tehtavaryhma
               WHERE nimi='TILAAJAN RAHAVARAUS'),
              (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
              urakan_sopimus);
  END LOOP;

  -- URAKAN 'MHU ja HJU Hoidon johto'

  toimenpidenimi = 'MHU ja HJU Hoidon johto';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- Erillishankinnat
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;


  ----------------------------------
  -- URAKAN YKSIKKÖHINTAISET TYÖT --
  ----------------------------------
  FOR hoitokausi_ IN 0..5 LOOP
      IF hoitokausi_ = 0 THEN
        INSERT INTO johto_ja_hallintokorvaus_ennen_urakkaa ("kk-v") VALUES (4.5)
           RETURNING id
           INTO ennen_urakkaa_id;
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, "ennen-urakkaa", luotu, "toimenkuva-id")
          VALUES
                 ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 4500, 40, (SELECT extract(year from NOW())), 10, ennen_urakkaa_id, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
      END IF;
      IF hoitokausi_ > 0 THEN
        FOR i IN 10..12 LOOP
          INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
            VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
        END LOOP;
        FOR i IN 1..12 LOOP
          FOR vuosi_ IN 1..4 LOOP
            INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
            VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

            IF (5 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
            END IF;
            IF (4 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
            END IF;
          END LOOP;
        END LOOP;
        FOR i IN 1..9 LOOP
          IF toimenpidenimi = 'Soratien hoito TP'
            THEN
              -- Jätetään soratiet suunnittelematta kuluvalle vuodelle
              EXIT;
          END IF;
          INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
            VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

            IF (5 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
            END IF;
            IF (4 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
            END IF;
        END LOOP;
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
  ennen_urakkaa_id INTEGER;
  i INTEGER;
  vuosi_ INTEGER;
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOR i IN 1..6 LOOP
    INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi=toimenpidekoodit[i]),
               urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  END LOOP;
  INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi='23151'),
               urakan_nimi || ' ' || 'MHU ja HJU Hoidon johto', (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  -- URAKAN KIINTEÄHINTAISET TYÖT (eli suunnitellut hankinnat)
  FOREACH toimenpidenimi IN ARRAY toimenpidenimet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    FOR i IN 10..12 LOOP
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
        VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
          VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      IF toimenpidenimi = 'Soratien hoito TP'
        THEN
          -- Jätetään soratiet suunnittelematta kuluvalle vuodelle
          EXIT;
      END IF;
      INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus)
        VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 8000 + i*100, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi ), null);
    END LOOP;
  END LOOP;

  -- URAKAN KUSTANNUSARVIOIDUT TYÖT
  FOREACH toimenpidenimi IN ARRAY hoito_toimenpidenimiet LOOP
    SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

    -- UI tarvitsee periaatteessa vain yhden rivin kutakin lajia (kustannussuunnitelmissa), mutta raportoinnissa joka vuoden kuulle pitää olla omansa.
    FOR i IN 10..12 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- kolmansien osapuolien aiheuttamat vahingot
               ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
                'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                'akillinen-hoitotyo'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Äkillinen hoitotyö' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
    FOR i IN 1..12 LOOP
      FOR vuosi_ IN 1..4 LOOP
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES -- kolmansien osapuolien aiheuttamat vahingot
                 ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 100,
                  'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                  (SELECT id
                   FROM toimenpidekoodi
                   WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                         tehtavaryhma=(SELECT id
                                       FROM tehtavaryhma
                                       WHERE nimi = CASE
                                                      WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                      WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                      WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                    END)),
                  NULL,
                  (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                 'akillinen-hoitotyo'::TOTEUMATYYPPI,
                 (SELECT id
                  FROM toimenpidekoodi
                  WHERE nimi='Äkillinen hoitotyö' AND
                        tehtavaryhma=(SELECT id
                                      FROM tehtavaryhma
                                      WHERE nimi = CASE
                                                     WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                     WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                     WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                   END)),
                 NULL,
                 (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        -- Laskutukseen perustusvat toimenpidekustannukset
        IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
          INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
            VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                    'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
        END IF;
      END LOOP;
    END LOOP;
    FOR i IN 1..9 LOOP
      -- kolmansien osapuolien aiheuttamat vahingot
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- kolmansien osapuolien aiheuttamat vahingot
               ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 100,
                'vahinkojen-korjaukset'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Äkilliset hoitotyöt
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 20000,
                'akillinen-hoitotyo'::TOTEUMATYYPPI,
                (SELECT id
                 FROM toimenpidekoodi
                 WHERE nimi='Äkillinen hoitotyö' AND
                       tehtavaryhma=(SELECT id
                                     FROM tehtavaryhma
                                     WHERE nimi = CASE
                                                    WHEN (toimenpidenimi = 'Talvihoito TP') THEN 'Alataso Muut talvihoitotyöt'
                                                    WHEN (toimenpidenimi = 'Liikenneympäristön hoito TP') THEN 'Muut liik.ymp.hoitosasiat'
                                                    WHEN (toimenpidenimi = 'Soratien hoito TP') THEN 'Alataso Sorateiden hoito'
                                                  END)),
                NULL,
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      -- Laskutukseen perustusvat toimenpidekustannukset
      IF toimenpidenimi = 'Liikenneympäristön hoito TP' THEN
        INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
          VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 1000,
                  'laskutettava-tyo'::TOTEUMATYYPPI, NULL, NULL, (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
      END IF;
    END LOOP;
  END LOOP;

    -- URAKAN RAHAVARAUKSET LUPAUKSIIN

  toimenpidenimi = 'MHU Ylläpito TP';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
              'muut-rahavaraukset'::TOTEUMATYYPPI,
              NULL,
              (SELECT id
               FROM tehtavaryhma
               WHERE nimi='TILAAJAN RAHAVARAUS'),
              (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
              urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
              'muut-rahavaraukset'::TOTEUMATYYPPI,
              NULL,
              (SELECT id
               FROM tehtavaryhma
               WHERE nimi='TILAAJAN RAHAVARAUS'),
              (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
              urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 5000,
              'muut-rahavaraukset'::TOTEUMATYYPPI,
              NULL,
              (SELECT id
               FROM tehtavaryhma
               WHERE nimi='TILAAJAN RAHAVARAUS'),
              (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
              urakan_sopimus);
  END LOOP;

  -- URAKAN 'MHU ja HJU Hoidon johto'

  toimenpidenimi = 'MHU ja HJU Hoidon johto';
  SELECT urakan_nimi || ' ' || toimenpidenimi INTO toimenpideinstanssin_nimi;

  FOR i IN 10..12 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  FOR i IN 1..12 LOOP
    FOR vuosi_ IN 1..4 LOOP
      INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
        VALUES -- Erillishankinnat
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
    END LOOP;
  END LOOP;
  FOR i IN 1..9 LOOP
    INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus)
      VALUES -- Erillishankinnat
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 700,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                NULL,
                (SELECT id FROM tehtavaryhma WHERE nimi='ERILLISHANKINNAT'),
                (select id from toimenpideinstanssi where nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Toimistokulut
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9000,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Toimistotarvike- ja ICT-kulut, tiedotus, opastus, kokousten järjestäminen jne.'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus),
                -- Työnjohto
                ((SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, 9100,
                'laskutettava-tyo'::TOTEUMATYYPPI,
                (SELECT id FROM toimenpidekoodi WHERE nimi = 'Hoitourakan työnjohto'),
                NULL,
                (SELECT id FROM toimenpideinstanssi WHERE nimi = toimenpideinstanssin_nimi),
                urakan_sopimus);
  END LOOP;
  ----------------------------------
  -- URAKAN YKSIKKÖHINTAISET TYÖT --
  ----------------------------------

  FOR hoitokausi_ IN 0..5 LOOP
      IF hoitokausi_ = 0 THEN
        INSERT INTO johto_ja_hallintokorvaus_ennen_urakkaa ("kk-v") VALUES (4.5)
           RETURNING id
           INTO ennen_urakkaa_id;
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, "ennen-urakkaa", luotu, "toimenkuva-id")
          VALUES
                 ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 4500, 40, (SELECT extract(year from NOW())), 10, ennen_urakkaa_id, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
      END IF;
      IF hoitokausi_ > 0 THEN
        FOR i IN 10..12 LOOP
          INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
            VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));
        END LOOP;
        FOR i IN 1..12 LOOP
          FOR vuosi_ IN 1..4 LOOP
            INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
            VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

            IF (5 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
            END IF;
            IF (4 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT vuosi_ + extract(year from (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
            END IF;
          END LOOP;
        END LOOP;
        FOR i IN 1..9 LOOP
          IF toimenpidenimi = 'Soratien hoito TP'
            THEN
              -- Jätetään soratiet suunnittelematta kuluvalle vuodelle
              EXIT;
          END IF;
          INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
            VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'sopimusvastaava')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'vastuunalainen työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'päätoiminen apulainen')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'apulainen/työnjohtaja')),
                   ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'hankintavastaava'));

            IF (5 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'harjoittelija'));
            END IF;
            IF (4 <= i) AND (i <= 8)
            THEN
              INSERT INTO johto_ja_hallintokorvaus ("urakka-id", tunnit, tuntipalkka, vuosi, kuukausi, luotu, "toimenkuva-id")
              VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), 1000, 30, (SELECT extract(year from (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi))), i, NOW(), (SELECT id FROM johto_ja_hallintokorvaus_toimenkuva WHERE toimenkuva = 'viherhoidosta vastaava henkilö'));
            END IF;
        END LOOP;
      END IF;
  END LOOP;

END $$;

-- Ivalon MHU-urakka
--'Hallinnolliset toimenpiteet TP'
DO $$
DECLARE
  toimenpidenimet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP', 'Päällystepaikkaukset TP', 'MHU Ylläpito TP', 'MHU Korvausinvestointi TP'];
  hoito_toimenpidenimiet TEXT[] := ARRAY ['Talvihoito TP', 'Liikenneympäristön hoito TP', 'Soratien hoito TP'];
  toimenpidekoodit TEXT[] := ARRAY ['23104', '23116', '23124', '20107', '20191', '14301'];
  urakan_nimi TEXT := 'Ivalon MHU testiurakka (uusi)';
  toimenpideinstanssin_nimi TEXT;
  toimenpidenimi TEXT;
  urakan_sopimus INT := (SELECT id FROM sopimus WHERE nimi = 'Ivalon MHU testiurakan sopimus');
  i INTEGER;
  vuosi_ INTEGER;
BEGIN
  -- URAKAN TOIMENPIDEINSTANSSIT
  FOR i IN 1..6 LOOP
    INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi=toimenpidekoodit[i]),
               urakan_nimi || ' ' || toimenpidenimet[i]::TEXT, (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
  END LOOP;
  INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm, tuotepolku, sampoid, talousosasto_id, talousosastopolku)
       VALUES ((SELECT id FROM urakka WHERE nimi=urakan_nimi), (SELECT id FROM toimenpidekoodi WHERE koodi='23151'),
               urakan_nimi || ' ' || 'MHU ja HJU Hoidon johto', (SELECT alkupvm FROM urakka WHERE nimi=urakan_nimi),
               (SELECT loppupvm FROM urakka WHERE nimi=urakan_nimi), 'tuotepolku', 'sampoid', 'talousosastoid', 'talousosastopolku');
END $$;