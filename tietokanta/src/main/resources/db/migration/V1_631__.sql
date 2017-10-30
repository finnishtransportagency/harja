INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Määräaikaishuolto', 4, now(), NULL, NULL, '{kokonaishintainen}', (SELECT id
                                                                           FROM toimenpidekoodi
                                                                           WHERE koodi='24104'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muu huolto', 4, now(), NULL, NULL, '{kokonaishintainen}', (SELECT id
                                                                    FROM toimenpidekoodi
                                                                    WHERE koodi='24104'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muu toimenpide', 4, now(), NULL, NULL, '{kokonaishintainen}', (SELECT id
                                                                        FROM toimenpidekoodi
                                                                        WHERE koodi='24104'));