INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', -20000, 'MAKU 2005', 'Urakoitsija maksaa tilaajalle', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', 5200, 'MAKU 2005', 'Vahingot on nyt korjattu, lasku tulossa.', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-11-18', -65200, 'MAKU 2005', 'Urakoitsija maksaa tilaajalle.', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('asiakastyytyvaisyysbonus', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', 10000, 'MAKU 2005', 'Asiakkaat erittäin tyytyväisiä, tyytyväisyysindeksi 0,92.', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP'), '2005-10-15', 20000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto', '2005-10-13', (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));

INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('asiakastyytyvaisyysbonus', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), '2016-01-15', 20000, 'MAKU 2005', 'As.tyyt. bonuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), '2016-01-17', 10000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Oulu Talvihoito TP 2014-2019'), '2016-01-19', -2000, 'MAKU 2005', 'Tilaaja maksaa urakoitsijalle korvausta 2ke', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'yit_uuvh'));

INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('asiakastyytyvaisyysbonus', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Pudasjärvi Talvihoito TP'), '2012-01-15', 20000, 'MAKU 2005', 'As.tyyt. bonuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'jvh'));
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012') AND paasopimus IS null), (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), (SELECT id FROM toimenpideinstanssi WHERE nimi='Pudasjärvi Talvihoito TP'), '2012-01-19', 10000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'jvh'));

-- Sisävesiväyliin
INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja)
VALUES ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Pyhäselän urakka') AND paasopimus IS null),
        (SELECT id FROM urakka WHERE nimi='Pyhäselän urakka'),
        (SELECT id FROM toimenpideinstanssi WHERE nimi='Kauppamerenkulun kustannukset TP' AND urakka = (SELECT id FROM urakka WHERE nimi='Pyhäselän urakka')),
        '2017-01-19', 10000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto Pyhäselkä', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'jvh')),

  ('muu', (SELECT id FROM sopimus WHERE urakka = (SELECT id FROM urakka WHERE nimi='Pyhäselän urakka') AND paasopimus IS null),
   (SELECT id FROM urakka WHERE nimi='Rentoselän urakka'),
   (SELECT id FROM toimenpideinstanssi WHERE nimi='Kauppamerenkulun kustannukset TP' AND urakka = (SELECT id FROM urakka WHERE nimi='Rentoselän urakka')),
   '2017-01-19', 10000, 'MAKU 2005', 'Muun erilliskustannuksen lisätieto Rentoselkä', NOW(), (SELECT ID FROM kayttaja WHERE kayttajanimi = 'jvh'));

-- Tampereen alueurakalle erilliskustannus, jotta voi varmistaa indeksin toimivuuden
DO $$
    DECLARE
        urakkaid INTEGER;
        sopimusid INTEGER;
        kayttajaid INTEGER;
        talvihoitotpi INTEGER;

    BEGIN
        urakkaid = (SELECT id FROM urakka where nimi = 'Tampereen alueurakka 2017-2022');
        kayttajaid = (SELECT id FROM kayttaja where kayttajanimi = 'yit_uuvh');
        sopimusid = (SELECT id FROM sopimus WHERE urakka = urakkaid AND paasopimus IS null);
        talvihoitotpi = (select id from toimenpideinstanssi where nimi = 'Tampere Talvihoito TP 2014-2019'); -- HOX. Toimenpideinstanssin nimi on päin honkia, mutta urakka on oikein


        INSERT INTO erilliskustannus (tyyppi,sopimus,urakka,toimenpideinstanssi,pvm,rahasumma,indeksin_nimi,lisatieto,luotu,luoja) VALUES
       ('asiakastyytyvaisyysbonus', sopimusid, urakkaid, talvihoitotpi, '2017-10-15', 10000, 'MAKU 2010',
        'Asiakkaat erittäin tyytyväisiä, tyytyväisyysindeksi 0,92.', '2017-10-28', kayttajaid);
    END
$$ LANGUAGE plpgsql;
