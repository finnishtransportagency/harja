ALTER TABLE yksikkohintainen_tyo ADD COLUMN arvioitu_kustannus NUMERIC;

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun vaihto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun asennus', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun siirto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Jääpoiju – poiju kettingin vaihto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Jääpoiju – painon vaihto (sisältää kettingin vaihdon)', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan vaihto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan asennus', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan siirto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan kettingin vaihto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Raskaat poijutyöt (ei sis. mat): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan vaihto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan asennus', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan siirto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan kettingin vaihto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));


INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Viittatyöt: Viitan asennus (sisältää ankkurointi tarvikkeet)', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Viittatyöt: Viitan siirto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Viittatyöt: Viitan vaihto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Viittatyöt: Viitan poisto', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));


INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Viankorjauskäynnit (valolaiteviat): Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella (sisältää mm. matkat)', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Viankorjauskäynnit (valolaiteviat): Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella (sisältää mm. matkat)', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));



INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muut (sisältää mm. materiaalit): Poijun toimittaminen kunnostukseen ja maalaaminen', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muut (sisältää mm. materiaalit): 20t lohkoankkuri', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muut (sisältää mm. materiaalit): 6t paino', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muut (sisältää mm. materiaalit): Normaali jääpoiju', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Muut (sisältää mm. materiaalit): Poijuviitta halk. 1000, L9900', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));



INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Henkilöstö: Työnjohto', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Henkilöstö: Ammattimies', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));



INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Kuljetuskalusto: Moottorivene (perämoottori tms.,ei nosturia, sis. kuljettajan)', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Kuljetuskalusto: Viittatöihin soveltuva vene miehistöineen', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Kuljetuskalusto: Viittatöihin soveltuvan veneen seisontapäivä', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Kuljetuskalusto: Poijujen hoitotöihin soveltuva alus miehistöineen', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Kuljetuskalusto: Poijujen hoitotöihin soveltuvan aluksen seisontapäivä', 4, now(), 'h', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Kuljetuskalusto: Henkilöauto',4, now(), 'km', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Kuljetuskalusto: Moottorikelkka',4, now(), 'km', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));

-- (hintoja käytetään, kun hoidon turvalaitemääriä lisätään tai vähennetään enintään 10 % koko turvalaitekannasta)

INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Hoidon vuosihinta, määrämuutokset: Jääpoiju ja poijuviitta, valaistu', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Hoidon vuosihinta, määrämuutokset: Jääpoiju ja poijuviitta, pimeä', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Hoidon vuosihinta, määrämuutokset: Suurviitta, valaistu', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Hoidon vuosihinta, määrämuutokset: Suurviitta, pimeä', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Hoidon vuosihinta, määrämuutokset: Viitta, pimeä', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Hoidon vuosihinta, määrämuutokset: Kiinteä turvalaite, valaistu', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));
INSERT INTO toimenpidekoodi (nimi, taso, luotu, yksikko, suoritettavatehtava, hinnoittelu, emo)
VALUES ('Hoidon vuosihinta, määrämuutokset: Kiinteä turvalaite, pimeä', 4, now(), 'kpl', NULL,
        '{kokonaishintainen,yksikkohintainen,muutoshintainen}', (SELECT id
                                                                 FROM toimenpidekoodi
                                                                 WHERE koodi='24102'));