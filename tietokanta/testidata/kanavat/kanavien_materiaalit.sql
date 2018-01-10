DO $$ DECLARE
  saimaan_urakan_id INTEGER := (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava');
  urakan_kayttaja INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi='tero');
  toimenpide_id INTEGER := (SELECT max(id) FROM kan_toimenpide WHERE urakka = saimaan_urakan_id and poistettu = false);
BEGIN
INSERT INTO vv_materiaali(nimi, maara, pvm, "urakka-id", luoja, luotu, halytysraja, toimenpide)
  VALUES ('Naulat', 1000, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), 200, NULL),
         ('Naulat', -3, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL, toimenpide_id),
         ('Naulat', -10, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL, NULL),
         ('Naulat', -23, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL, NULL),
         ('Ämpäreitä', 500, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), 10, NULL),
         ('Ämpäreitä', -2, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL, NULL),
         ('Ämpäreitä', -15, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL, NULL),
         ('Ämpäreitä', -9, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL, NULL);
END $$
