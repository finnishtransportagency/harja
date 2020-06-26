INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu)
    VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 2020,
            (SELECT id FROM toimenpidekoodi WHERE api_tunnus = 1430), 200, '2020-10-01 00:00:01');
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu)
    VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 2020,
            (SELECT id FROM toimenpidekoodi WHERE api_tunnus = 1414), 33.4, '2020-10-01 00:00:01');
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu)
    VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 2020,
            (SELECT id FROM toimenpidekoodi WHERE api_tunnus = 9775), 32.6, '2020-10-01 00:00:01');
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu)
    VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 2020,
            (SELECT id FROM toimenpidekoodi WHERE api_tunnus = 18055), 400, '2020-10-01 00:00:01');
INSERT INTO urakka_tehtavamaara (urakka, "hoitokauden-alkuvuosi", tehtava, maara, luotu)
    VALUES ((SELECT id FROM urakka WHERE nimi = 'Oulun MHU 2019-2024'), 2021,
            (SELECT id FROM toimenpidekoodi WHERE api_tunnus = 18055), 666, '2021-10-01 00:00:01');







