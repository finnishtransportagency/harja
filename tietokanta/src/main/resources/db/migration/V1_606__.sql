-- Lisää toimenpidekoodit jääpeitten aikana tehtäville töille

SELECT luo_vv_tpk('VV111-44', 'Viittatyöt: Lähtöhinta viitoissa', 'kerta');
SELECT luo_vv_tpk('VV111-45', 'Viittatyöt: Siirtoajo', 'maili');

SELECT luo_vv_tpk('VV111-46', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Jääpoiju – poijun vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-47', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Jääpoiju – poijun asennus', 'kpl');
SELECT luo_vv_tpk('VV111-48', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Jääpoiju – poijun siirto', 'kpl');
SELECT luo_vv_tpk('VV111-49', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Jääpoiju – poiju kettingin vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-50', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Jääpoiju – painon vaihto (sisältää kettingin vaihdon)', 'kpl');

SELECT luo_vv_tpk('VV111-51', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Esijännitetty – poijuviitan vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-52', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Esijännitetty – poijuviitan asennus', 'kpl');
SELECT luo_vv_tpk('VV111-53', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Esijännitetty – poijuviitan siirto', 'kpl');
SELECT luo_vv_tpk('VV111-54', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Esijännitetty – poijuviitan kettingin vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-55', 'Raskaat poijutyöt, jääpeite (ei sis. mat): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)', 'kpl');

SELECT luo_vv_tpk('VV111-56', 'Suurviittatyöt 355 - 500 mm, jääpeite (ei sis. mat.): Esijännitetty – suurviitan vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-57', 'Suurviittatyöt 355 - 500 mm, jääpeite (ei sis. mat.): Esijännitetty – suurviitan asennus', 'kpl');
SELECT luo_vv_tpk('VV111-58', 'Suurviittatyöt 355 - 500 mm, jääpeite (ei sis. mat.): Esijännitetty – suurviitan siirto', 'kpl');
SELECT luo_vv_tpk('VV111-59', 'Suurviittatyöt 355 - 500 mm, jääpeite (ei sis. mat.): Esijännitetty – suurviitan kettingin vaihto', 'kpl');
SELECT luo_vv_tpk('VV111-60', 'Suurviittatyöt 355 - 500 mm, jääpeite (ei sis. mat.): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)', 'kpl');

-- Päivitetään vanhoihin toimenpiteisiin merkintä avovesikaudesta

UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Jääpoiju – poijun vaihto'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun vaihto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Jääpoiju – poijun asennus'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun asennus';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Jääpoiju – poijun siirto'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poijun siirto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Jääpoiju – poiju kettingin vaihto'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – poiju kettingin vaihto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Jääpoiju – painon vaihto (sisältää kettingin vaihdon)'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Jääpoiju – painon vaihto (sisältää kettingin vaihdon)';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Esijännitetty – poijuviitan vaihto'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan vaihto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Esijännitetty – poijuviitan asennus'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan asennus';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Esijännitetty – poijuviitan siirto'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan siirto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Esijännitetty – poijuviitan kettingin vaihto'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – poijuviitan kettingin vaihto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Raskaat poijutyöt, avovesikausi (ei sis. mat): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)'
WHERE nimi = 'Raskaat poijutyöt (ei sis. mat): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)';

UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Suurviittatyöt 355 - 500 mm, avovesikausi (ei sis. mat.): Esijännitetty – suurviitan vaihto'
WHERE nimi = 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan vaihto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Suurviittatyöt 355 - 500 mm, avovesikausi (ei sis. mat.): Esijännitetty – suurviitan asennus'
WHERE nimi = 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan asennus';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Suurviittatyöt 355 - 500 mm, avovesikausi (ei sis. mat.): Esijännitetty – suurviitan siirto'
WHERE nimi = 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan siirto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Suurviittatyöt 355 - 500 mm, avovesikausi (ei sis. mat.): Esijännitetty – suurviitan kettingin vaihto'
WHERE nimi = 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – suurviitan kettingin vaihto';
UPDATE toimenpidekoodi SET
  muokattu = NOW(),
  nimi = 'Suurviittatyöt 355 - 500 mm, avovesikausi (ei sis. mat.): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)'
WHERE nimi = 'Suurviittatyöt 355 - 500 mm (ei sis. mat.): Esijännitetty – painon vaihto (sisältää kettingin vaihdon)';
