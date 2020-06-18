INSERT INTO paallystysmassa (urakka, massatyyppitunnus, raekoko, nimi, rc, esiintyma, km_arvo, muotoarvo,
                             sideainetyyppi, pitoisuus, lisaaineet, luoja)
VALUES ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'AB-16', 16,
        'Alfattibetoni', 1, 'Kaislakallio', 'AN14', 20, '70/100', 5.40, 'Pippuria ja suolaa',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska')),
       ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'SMA 16', 16,
        'Kivimastiksiasfaltti', 2, 'Karjukallio', 'AN7', 10, '70/100', 5.80, 'Chiliä ja Currya',
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'));
