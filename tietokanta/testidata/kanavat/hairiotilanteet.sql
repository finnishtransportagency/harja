INSERT INTO kan_hairio (urakka, sopimus, pvm, kohde, vikaluokka, syy, odotusaika_h, ammattiliikenne_lkm,
                        huviliikenne_lkm, korjaustoimenpide, korjausaika_h, korjauksen_tila, paikallinen_kaytto,
                        luoja, luotu)
VALUES (
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'), '2017-07-14',
  (SELECT id
   FROM kan_kohde
   WHERE nimi =
         'Taipaleen sulku ja silta'),
  'sahkotekninen_vika' :: KAN_HAIRIO_VIKALUOKKA, 'Jotain meni vikaan', 60, 1, 2,
  'Vika korjattiin', 100, 'valmis' :: KAN_HAIRIO_KORJAUKSEN_TILA,
  TRUE,
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  NOW());

INSERT INTO kan_hairio (urakka, sopimus, pvm, kohde, vikaluokka, syy, odotusaika_h, ammattiliikenne_lkm,
                        huviliikenne_lkm, korjaustoimenpide, korjausaika_h, korjauksen_tila, paikallinen_kaytto,
                        luoja, luotu)
VALUES (
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'), '2017-01-15',
  (SELECT id
   FROM kan_kohde
   WHERE nimi =
         'Tikkalansaaren avattava ratasilta'),
  'sahkotekninen_vika' :: KAN_HAIRIO_VIKALUOKKA, 'Edellinen korjaus tehtiin huonosti, korjattu nyt uudestaan.', 70, 5, 6,
  'Vika korjattiin', 20, 'valmis' :: KAN_HAIRIO_KORJAUKSEN_TILA,
  TRUE,
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  NOW());

INSERT INTO kan_hairio (urakka, sopimus, pvm, kohde, vikaluokka, syy, korjauksen_tila,
                        paikallinen_kaytto, luoja, luotu)
VALUES (
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'), '2017-07-15',
  (SELECT id
   FROM kan_kohde
   WHERE nimi =
         'Taipaleen sulku ja silta'),
  'konetekninen_vika' :: KAN_HAIRIO_VIKALUOKKA, 'Syy ei tiedossa', 'kesken' :: KAN_HAIRIO_KORJAUKSEN_TILA,
  FALSE,
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  NOW());