DO $$
DECLARE
  urakan_aloitus_pvm TIMESTAMP;
BEGIN
  IF ((SELECT date_part('month', now())) >= 10)
  THEN
      urakan_aloitus_pvm = make_date((SELECT date_part('year', now()))::INT, 10, 1);
  ELSE
      urakan_aloitus_pvm = make_date((SELECT date_part('year', now())::INT - 1), 10, 1);
  END IF;
  INSERT INTO urakka_tavoite (urakka, hoitokausi, tarjous_tavoitehinta, tavoitehinta, tavoitehinta_siirretty, kattohinta, luotu)
  VALUES ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'), 1, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'), 2, 300000, 300000, NULL, 1.1 * 300000, urakan_aloitus_pvm),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'), 3, 350000, 350000, NULL, 1.1 * 350000, urakan_aloitus_pvm),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'), 4, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm),
         ((SELECT id FROM urakka WHERE nimi = 'Rovaniemen MHU testiurakka (1. hoitovuosi)'), 5, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm),
         ((SELECT id FROM urakka WHERE nimi = 'Pellon MHU testiurakka (3. hoitovuosi)'), 1, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm - interval '2 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Pellon MHU testiurakka (3. hoitovuosi)'), 2, 300000, 300000, NULL, 1.1 * 300000, urakan_aloitus_pvm - interval '2 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Pellon MHU testiurakka (3. hoitovuosi)'), 3, 350000, 350000, NULL, 1.1 * 350000, urakan_aloitus_pvm - interval '2 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Pellon MHU testiurakka (3. hoitovuosi)'), 4, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm - interval '2 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Pellon MHU testiurakka (3. hoitovuosi)'), 5, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm - interval '2 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'), 1, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm - interval '5 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'), 2, 300000, 300000, NULL, 1.1 * 300000, urakan_aloitus_pvm - interval '5 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'), 3, 350000, 350000, NULL, 1.1 * 350000, urakan_aloitus_pvm - interval '5 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'), 4, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm - interval '5 years'),
         ((SELECT id FROM urakka WHERE nimi = 'Kemin MHU testiurakka (5. hoitovuosi)'), 5, 250000, 250000, NULL, 1.1 * 250000, urakan_aloitus_pvm - interval '5 years');
END $$;