DO $$ DECLARE
  saimaan_urakan_id INTEGER := (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava');
  urakan_kayttaja INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi='tero');
BEGIN
INSERT INTO vv_materiaali(nimi, maara, pvm, "urakka-id", luoja, luotu, halytysraja)
  VALUES ('Naulat', 1000, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), 200),
         ('Naulat', -3, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL),
         ('Naulat', -10, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL),
         ('Naulat', -23, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL),
         ('Ämpäreitä', 500, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), 10),
         ('Ämpäreitä', -2, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL),
         ('Ämpäreitä', -15, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL),
         ('Ämpäreitä', -9, '2017-11-28', saimaan_urakan_id, urakan_kayttaja, NOW(), NULL);
END $$