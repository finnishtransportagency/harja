INSERT INTO urakka_tavoite (urakka, hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta, luotu)
  VALUES ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka'), 1, 250000, NULL, 1.1 * 250000, '2018-10-01'::timestamp),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka'), 2, 300000, NULL, 1.1 * 300000, '2018-10-01'::timestamp),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka'), 3, 350000, NULL, 1.1 * 350000, '2018-10-01'::timestamp),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka'), 4, 250000, NULL, 1.1 * 250000, '2018-10-01'::timestamp),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka'), 5, 250000, NULL, 1.1 * 250000, '2018-10-01'::timestamp);