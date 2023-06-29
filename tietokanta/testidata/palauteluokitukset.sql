INSERT INTO palautevayla_aihe (ulkoinen_id, nimi, jarjestys, kaytossa)
VALUES (900, 'Testaus', 9990, true),
       (901, 'Toinen testaus', 9991, true),
       (902, 'Poistettu', 9992, false);

INSERT INTO palautevayla_tarkenne (ulkoinen_id, nimi, aihe_id, jarjestys, kaytossa)
VALUES (9001, 'Testaaminen', 900, 9990, true),
       (9002, 'Testailu', 900, 9991, true),
       (9011, 'Toinen testaaminen', 901, 9992, true),
       (9012, 'Toinen testailu', 901, 9993, true),
       (9021, 'Poistettu käytössä', 902, 9994, true),
       (9022, 'Poistettu poistettu', 902, 9995, true);
