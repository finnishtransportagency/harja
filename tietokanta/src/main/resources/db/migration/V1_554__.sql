-- Vesiväylien toimenpiteet
INSERT INTO toimenpidekoodi (koodi, nimi, taso, emo, luotu, poistettu, api_seuranta)
VALUES ('VV1', 'Hoito, Vesiväylät', 1, NULL, NOW(), FALSE, FALSE);

INSERT INTO toimenpidekoodi (koodi, nimi, taso, emo, luotu, poistettu, api_seuranta)
VALUES ('VV11','Väylänhoito', 2, (select id from toimenpidekoodi where koodi = 'VV1'), NOW(), FALSE, FALSE);

INSERT INTO toimenpidekoodi (koodi, nimi, taso, emo, luotu, poistettu, api_seuranta)
VALUES ('VV111','Laaja toimenpide', 3, (select id from toimenpidekoodi where koodi = 'VV11'), NOW(), FALSE, FALSE);

CREATE FUNCTION luo_vv_tpk(koodi_ varchar, nimi_ varchar, yksikko_ varchar) RETURNS VOID AS $$

BEGIN
  RAISE NOTICE 'Luodaan vesiväylä tpk: % (%) koodilla %', nimi_, yksikko_, koodi_;
  INSERT INTO toimenpidekoodi
         (koodi, nimi, taso, emo, luotu, poistettu, api_seuranta, yksikko, hinnoittelu)
  VALUES (koodi_, nimi_, 4, (SELECT id FROM toimenpidekoodi WHERE koodi='VV111'),
          NOW(), FALSE, FALSE, yksikko_, '{kokonaishintainen,yksikkohintainen,muutoshintainen}');
  RETURN;
END;
$$ LANGUAGE plpgsql;


SELECT luo_vv_tpk('VV111-1', 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-2', 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun asennus', 'kpl');
SELECT luo_vv_tpk('VV111-3', 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun siirto', 'kpl');
SELECT luo_vv_tpk('VV111-4', 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poiju kettingin vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-5', 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – painon vaihto (sisältää kettingin vaihdon)', 'kpl');
SELECT luo_vv_tpk('VV111-6', 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-7', 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan asennus', 'kpl');
SELECT luo_vv_tpk('VV111-8', 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan siirto', 'kpl');
SELECT luo_vv_tpk('VV111-9', 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan kettingin vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-10', 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)', 'kpl');

SELECT luo_vv_tpk('VV111-11', 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-12', 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan asennus', 'kpl');
SELECT luo_vv_tpk('VV111-13', 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan siirto', 'kpl');
SELECT luo_vv_tpk('VV111-14', 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan kettingin vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-15', 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)', 'kpl');


SELECT luo_vv_tpk('VV111-16', 'Viittatyöt: Viitan asennus (sisältää ankkurointi tarvikkeet)', 'kpl');
SELECT luo_vv_tpk('VV111-17', 'Viittatyöt: Viitan siirto', 'kpl');
SELECT luo_vv_tpk('VV111-18', 'Viittatyöt: Viitan vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-19', 'Viittatyöt: Viitan poisto', 'kpl');


SELECT luo_vv_tpk('VV111-20', 'Viankorjauskäynnit (valolaiteviat): Vikailmoitukseen perustuva viankorjauskäynti kelluvalla turvalaitteella (sisältää mm. matkat)', 'kpl');
SELECT luo_vv_tpk('VV111-21', 'Viankorjauskäynnit (valolaiteviat): Vikailmoitukseen perustuva viankorjauskäynti kiinteällä turvalaitteella (sisältää mm. matkat)', 'kpl');



SELECT luo_vv_tpk('VV111-22', 'Muut (sisältää mm. materiaalit)- Poijun toimittaminen kunnostukseen ja maalaaminen', 'kpl');
SELECT luo_vv_tpk('VV111-23', 'Muut (sisältää mm. materiaalit)- 20t lohkoankkuri', 'kpl');
SELECT luo_vv_tpk('VV111-24', 'Muut (sisältää mm. materiaalit)- 6t paino', 'kpl');
SELECT luo_vv_tpk('VV111-25', 'Muut (sisältää mm. materiaalit)- Normaali jääpoiju', 'kpl');
SELECT luo_vv_tpk('VV111-26', 'Muut (sisältää mm. materiaalit)- Poijuviitta halk. 1000, L9900', 'kpl');



SELECT luo_vv_tpk('VV111-27', 'Henkilöstö: Työnjohto', 'h');
SELECT luo_vv_tpk('VV111-28', 'Henkilöstö: Ammattimies', 'h');
SELECT luo_vv_tpk('VV111-29', 'Henkilöstö: Sukeltaja, sis. merkinantajan ja sukellusvälineet', 'h');



SELECT luo_vv_tpk('VV111-30', 'Kuljetuskalusto: Moottorivene (perämoottori tms.,ei nosturia, sis. kuljettajan)', 'h');
SELECT luo_vv_tpk('VV111-31', 'Kuljetuskalusto: Viittatöihin soveltuva vene miehistöineen', 'h');
SELECT luo_vv_tpk('VV111-32', 'Kuljetuskalusto: Viittatöihin soveltuvan veneen seisontapäivä', 'h');
SELECT luo_vv_tpk('VV111-33', 'Kuljetuskalusto: Poijujen hoitotöihin soveltuva alus miehistöineen', 'h');
SELECT luo_vv_tpk('VV111-34', 'Kuljetuskalusto: Poijujen hoitotöihin soveltuvan aluksen seisontapäivä', 'h');
SELECT luo_vv_tpk('VV111-35', 'Kuljetuskalusto: Henkilöauto', 'km');
SELECT luo_vv_tpk('VV111-36', 'Kuljetuskalusto: Moottorikelkka', 'km');

-- (hintoja käytetään, kun hoidon turvalaitemääriä lisätään tai vähennetään enintään 10 % koko turvalaitekannasta)

SELECT luo_vv_tpk('VV111-37', 'Hoidon vuosihinta, määrämuutokset: Jääpoiju ja poijuviitta, valaistu', 'kpl');
SELECT luo_vv_tpk('VV111-38', 'Hoidon vuosihinta, määrämuutokset: Jääpoiju ja poijuviitta, pimeä', 'kpl');
SELECT luo_vv_tpk('VV111-39', 'Hoidon vuosihinta, määrämuutokset: Suurviitta, valaistu', 'kpl');
SELECT luo_vv_tpk('VV111-40', 'Hoidon vuosihinta, määrämuutokset: Suurviitta, pimeä', 'kpl');
SELECT luo_vv_tpk('VV111-41', 'Hoidon vuosihinta, määrämuutokset: Viitta, pimeä', 'kpl');
SELECT luo_vv_tpk('VV111-42', 'Hoidon vuosihinta, määrämuutokset: Kiinteä turvalaite, valaistu', 'kpl');
SELECT luo_vv_tpk('VV111-43', 'Hoidon vuosihinta, määrämuutokset: Kiinteä turvalaite, pimeä', 'kpl');


-- Luodaan vesiväylien hoitourakoille toimenpideinstanssit
INSERT INTO toimenpideinstanssi (urakka, toimenpide, nimi, alkupvm, loppupvm)
 SELECT id, (select id from toimenpidekoodi where koodi='VV111'), nimi, alkupvm, loppupvm
   FROM urakka
  WHERE tyyppi='vesivayla-hoito';
