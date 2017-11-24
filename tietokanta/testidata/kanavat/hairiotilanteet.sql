INSERT INTO kan_hairio (urakka, sopimus, pvm, "kohde-id", "kohteenosa-id", vikaluokka, syy, odotusaika_h, ammattiliikenne_lkm,
                        huviliikenne_lkm, korjaustoimenpide, korjausaika_h, korjauksen_tila, paikallinen_kaytto,
                        luoja, luotu)
VALUES (
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'), NOW() - INTERVAL '1 minutes',
  (SELECT id
   FROM kan_kohde
   WHERE nimi =
         'Soskua'),
  (SELECT id
  FROM kan_kohteenosa
  WHERE tyyppi = 'sulku'
  AND "kohde-id" = (SELECT id
                    FROM kan_kohde
                    WHERE nimi =
                          'Soskua')),
  'sahkotekninen_vika' :: KAN_HAIRIO_VIKALUOKKA, 'Jotain meni vikaan', 60, 1, 2,
  'Vika korjattiin', 100, 'valmis' :: KAN_HAIRIO_KORJAUKSEN_TILA,
  TRUE,
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  NOW());

INSERT INTO kan_hairio (urakka, sopimus, pvm, "kohde-id", "kohteenosa-id", vikaluokka, syy, odotusaika_h, ammattiliikenne_lkm,
                        huviliikenne_lkm, korjaustoimenpide, korjausaika_h, korjauksen_tila, paikallinen_kaytto,
                        luoja, luotu)
VALUES (
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'), NOW() - INTERVAL '20 minutes',
  (SELECT id
   FROM kan_kohde
   WHERE nimi =
         'Pälli'),
  NULL,
  'sahkotekninen_vika' :: KAN_HAIRIO_VIKALUOKKA, 'Edellinen korjaus tehtiin huonosti, korjattu nyt uudestaan.', 70, 5, 6,
  'Vika korjattiin', 20, 'valmis' :: KAN_HAIRIO_KORJAUKSEN_TILA,
  TRUE,
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  NOW());

INSERT INTO kan_hairio (urakka, sopimus, pvm, "kohde-id", "kohteenosa-id", vikaluokka, syy, korjauksen_tila,
                        paikallinen_kaytto, luoja, luotu)
VALUES (
  (SELECT id
   FROM urakka
   WHERE nimi = 'Saimaan kanava'),
  (SELECT id
   FROM sopimus
   WHERE nimi = 'Saimaan huollon pääsopimus'), NOW() - INTERVAL '5 minutes',
  (SELECT id
   FROM kan_kohde
   WHERE nimi =
         'Soskua'),
  (SELECT id
   FROM kan_kohteenosa
   WHERE tyyppi = 'silta'
         AND "kohde-id" = (SELECT id
                           FROM kan_kohde
                           WHERE nimi =
                                 'Soskua')),
  'konetekninen_vika' :: KAN_HAIRIO_VIKALUOKKA, 'Syy ei tiedossa', 'kesken' :: KAN_HAIRIO_KORJAUKSEN_TILA,
  FALSE,
  (SELECT id
   FROM kayttaja
   WHERE kayttajanimi = 'jvh'),
  NOW());