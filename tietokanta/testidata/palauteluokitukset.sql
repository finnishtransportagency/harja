INSERT INTO palautevayla_aihe (ulkoinen_id, nimi, jarjestys, kaytossa)
VALUES (90, 'Testaus', 9990, true),
       (91, 'Toinen testaus', 9991, true),
       (92, 'Poistettu', 9992, false);

INSERT INTO palautevayla_tarkenne (ulkoinen_id, nimi, aihe_id, jarjestys, kaytossa)
VALUES (901, 'Testaaminen', 90, 9990, true),
       (902, 'Testailu', 90, 9991, true),
       (911, 'Toinen testaaminen', 91, 9992, true),
       (912, 'Toinen testailu', 91, 9993, true),
       (921, 'Poistettu käytössä', 92, 9994, true),
       (922, 'Poistettu poistettu', 92, 9995, true);
