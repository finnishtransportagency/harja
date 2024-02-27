WITH analytiikka_testiurakka AS (INSERT INTO urakka (sampoid, sopimustyyppi, hallintayksikko, nimi, alkupvm, loppupvm, tyyppi, urakkanro, urakoitsija)
VALUES ('5731289-TES2', 'kokonaisurakka' :: sopimustyyppi, (SELECT id
                                                            FROM organisaatio
                                                            WHERE lyhenne = 'POP'), 'Nivalan päällystysurakka',
        '2023-01-01', '2023-12-31', 'paallystys', 'niva1', (SELECT id
                                                           FROM organisaatio
                                                           WHERE ytunnus = '0651792-4')) RETURNING id)
INSERT INTO yhatiedot(urakka, yhatunnus, yhaid, elyt, vuodet, luotu, muokattu)
       SELECT id,
              'YHA5731289',
              5731289,
              ARRAY['POP'],
                ARRAY[2023],
                '2022-07-15T12:00:00.000',
              '2022-07-15T12:00:00.000'
       FROM analytiikka_testiurakka;

SELECT * FROM urakka where nimi ilike '%nivalan%';
