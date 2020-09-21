INSERT INTO pot2_massa (urakka_id, massatyyppi, nimi, max_raekoko, asfalttiasema, kuulamyllyluokka,
                        litteyslukuluokka, DoP_nro, luoja, luotu)
VALUES ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'AB-16', 'Alfattibetoni', 16,
        'Jokimaan asema', 1, 'AN14', 20,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now()),
       ((SELECT id from urakka WHERE nimi = 'Utajärven päällystysurakka'), 'SMA 16', 'Kivimastiksiasfaltti', 16,
        'Karvamaan asema', 2,  'AN7', 10,
        (SELECT id FROM kayttaja WHERE kayttajanimi = 'skanska'), now());
