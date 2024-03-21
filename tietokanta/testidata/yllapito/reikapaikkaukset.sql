----------------------------
-- Muhoksen päällystysurakka
----------------------------

-- Reikäpaikkaukset testidata 
-- Aseta Muhoksen päällystysurakka MPU tyyppiseksi jotta reikäpaikkaukset näkyy
UPDATE urakka SET "sopimustyyppi" = 'mpu' WHERE id = (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka');


SELECT reikapaikkaus_upsert( 
    'reikapaikkaus'::paikkaustyyppi,                                                               -- tyyppi
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,                             -- luojaid
    '02.25.2024'::TIMESTAMP,                                                                       -- luotu, jos ei olemassa, NOW()
    NULL::INT,                                                                                     -- muokkaaja_id, jos ei olemassa, NULL
    NULL::TIMESTAMP,                                                                               -- muokattu
    NULL::INT,                                                                                     -- poistajaid
    FALSE,                                                                                         -- poistettu
    (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka')::INT,                      -- urakkaid 
    NULL::INT,                                                                                     -- paikkauskohdeid
    1234444::INT,                                                                                  -- ulkoinenid (tuodaan excelistä)
    '02.25.2024'::TIMESTAMP,                                                                       -- alkuaika
    '02.25.2024'::TIMESTAMP,                                                                       -- loppuaika, joka on sama, tämä ei taida reikäpaikkauksilla olla relevantti(?)
    ROW(20, 1, 860, 1, 1020, NULL)::TR_OSOITE,                                                     -- tr osoite
    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'Urapaikkaus (UREM/RREM)')::INT,       -- tyomenetelma 
    'AB, Asfalttibetoni'::TEXT,                         -- massatyyppi, ei tietoa miten tämä reikäpaikkauksille, laitettu AB, failaa muuten NOT NULL constraint
    NULL::NUMERIC,                                      -- leveys
    NULL::NUMERIC,                                      -- massamenekki 
    NULL::NUMERIC,                                      -- massamaara 
    NULL::NUMERIC,                                      -- pintaala
    NULL::INTEGER,                                      -- raekoko 
    NULL::TEXT,                                         -- kuulamylly
    215000.0::NUMERIC,                                  -- kustannus
    'm2'::TEXT,                                         -- yksikkö
    81::INT,                                            -- määrä
    (SELECT tierekisteriosoitteelle_viiva(20, 1, 860, 1, 1020)) -- geometria
);


SELECT reikapaikkaus_upsert( 
    'reikapaikkaus'::paikkaustyyppi,                                                               -- tyyppi
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,                             -- luojaid
    '03.01.2024'::TIMESTAMP,                                                                       -- luotu, jos ei olemassa, NOW()
    NULL::INT,                                                                                     -- muokkaaja_id, jos ei olemassa, NULL
    NULL::TIMESTAMP,                                                                               -- muokattu
    NULL::INT,                                                                                     -- poistajaid
    FALSE,                                                                                         -- poistettu
    (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka')::INT,                      -- urakkaid 
    NULL::INT,                                                                                     -- paikkauskohdeid
    1234341::INT,                                                                                  -- ulkoinenid (tuodaan excelistä)
    '03.01.2024'::TIMESTAMP,                                                                       -- alkuaika
    '03.01.2024'::TIMESTAMP,                                                                       -- loppuaika, joka on sama, tämä ei taida reikäpaikkauksilla olla relevantti(?)
    ROW(20, 1, 750, 1, 800, NULL)::TR_OSOITE,                                                      -- tr osoite
    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'Jyrsintäkorjaukset (HJYR/TJYR)')::INT,-- tyomenetelma 
    'AB, Asfalttibetoni'::TEXT,                         -- massatyyppi, ei tietoa miten tämä reikäpaikkauksille, laitettu AB, failaa muuten NOT NULL constraint
    NULL::NUMERIC,                                      -- leveys
    NULL::NUMERIC,                                      -- massamenekki 
    NULL::NUMERIC,                                      -- massamaara 
    NULL::NUMERIC,                                      -- pintaala
    NULL::INTEGER,                                      -- raekoko 
    NULL::TEXT,                                         -- kuulamylly
    25000.0::NUMERIC,                                  -- kustannus
    'jm'::TEXT,                                         -- yksikkö
    66::INT,                                            -- määrä
    (SELECT tierekisteriosoitteelle_viiva(20, 1, 750, 1, 800)) -- geometria
);


SELECT reikapaikkaus_upsert( 
    'reikapaikkaus'::paikkaustyyppi,                                                               -- tyyppi
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,                             -- luojaid
    '03.02.2024'::TIMESTAMP,                                                                       -- luotu, jos ei olemassa, NOW()
    NULL::INT,                                                                                     -- muokkaaja_id, jos ei olemassa, NULL
    NULL::TIMESTAMP,                                                                               -- muokattu
    NULL::INT,                                                                                     -- poistajaid
    FALSE,                                                                                         -- poistettu
    (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka')::INT,                      -- urakkaid 
    NULL::INT,                                                                                     -- paikkauskohdeid
    1234342::INT,                                                                                  -- ulkoinenid (tuodaan excelistä)
    '03.02.2024'::TIMESTAMP,                                                                       -- alkuaika
    '03.02.2024'::TIMESTAMP,                                                                       -- loppuaika, joka on sama, tämä ei taida reikäpaikkauksilla olla relevantti(?)
    ROW(20, 1, 480, 1, 700, NULL)::TR_OSOITE,                                                      -- tr osoite
    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'Jyrsintäkorjaukset (HJYR/TJYR)')::INT,-- tyomenetelma 
    'AB, Asfalttibetoni'::TEXT,                         -- massatyyppi, ei tietoa miten tämä reikäpaikkauksille, laitettu AB, failaa muuten NOT NULL constraint
    NULL::NUMERIC,                                      -- leveys
    NULL::NUMERIC,                                      -- massamenekki 
    NULL::NUMERIC,                                      -- massamaara 
    NULL::NUMERIC,                                      -- pintaala
    NULL::INTEGER,                                      -- raekoko 
    NULL::TEXT,                                         -- kuulamylly
    3520.0::NUMERIC,                                    -- kustannus
    'm2'::TEXT,                                         -- yksikkö
    66::INT,                                            -- määrä
    (SELECT tierekisteriosoitteelle_viiva(20, 1, 480, 1, 700)) -- geometria
);


SELECT reikapaikkaus_upsert( 
    'reikapaikkaus'::paikkaustyyppi,                                                               -- tyyppi
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,                             -- luojaid
    '03.03.2024'::TIMESTAMP,                                                                       -- luotu, jos ei olemassa, NOW()
    NULL::INT,                                                                                     -- muokkaaja_id, jos ei olemassa, NULL
    NULL::TIMESTAMP,                                                                               -- muokattu
    NULL::INT,                                                                                     -- poistajaid
    FALSE,                                                                                         -- poistettu
    (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka')::INT,                      -- urakkaid 
    NULL::INT,                                                                                     -- paikkauskohdeid
    1234343::INT,                                                                                  -- ulkoinenid (tuodaan excelistä)
    '03.03.2024'::TIMESTAMP,                                                                       -- alkuaika
    '03.03.2024'::TIMESTAMP,                                                                       -- loppuaika, joka on sama, tämä ei taida reikäpaikkauksilla olla relevantti(?)
    ROW(20, 1, 140, 1, 360, NULL)::TR_OSOITE,                                                      -- tr osoite
    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'Jyrsintäkorjaukset (HJYR/TJYR)')::INT,-- tyomenetelma 
    'AB, Asfalttibetoni'::TEXT,                         -- massatyyppi, ei tietoa miten tämä reikäpaikkauksille, laitettu AB, failaa muuten NOT NULL constraint
    NULL::NUMERIC,                                      -- leveys
    NULL::NUMERIC,                                      -- massamenekki 
    NULL::NUMERIC,                                      -- massamaara 
    NULL::NUMERIC,                                      -- pintaala
    NULL::INTEGER,                                      -- raekoko 
    NULL::TEXT,                                         -- kuulamylly
    4500.0::NUMERIC,                                    -- kustannus
    'kpl'::TEXT,                                        -- yksikkö
    66::INT,                                            -- määrä
    (SELECT tierekisteriosoitteelle_viiva(20, 1, 140, 1, 360)) -- geometria
);


SELECT reikapaikkaus_upsert( 
    'reikapaikkaus'::paikkaustyyppi,                                                               -- tyyppi
    (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')::INT,                             -- luojaid
    '03.05.2024'::TIMESTAMP,                                                                       -- luotu, jos ei olemassa, NOW()
    NULL::INT,                                                                                     -- muokkaaja_id, jos ei olemassa, NULL
    NULL::TIMESTAMP,                                                                               -- muokattu
    NULL::INT,                                                                                     -- poistajaid
    FALSE,                                                                                         -- poistettu
    (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka')::INT,                      -- urakkaid 
    NULL::INT,                                                                                     -- paikkauskohdeid
    1234344::INT,                                                                                  -- ulkoinenid (tuodaan excelistä)
    '03.05.2024'::TIMESTAMP,                                                                       -- alkuaika
    '03.05.2024'::TIMESTAMP,                                                                       -- loppuaika, joka on sama, tämä ei taida reikäpaikkauksilla olla relevantti(?)
    ROW(20, 1, 1, 1, 120, NULL)::TR_OSOITE,                                                        -- tr osoite
    (SELECT id FROM paikkauskohde_tyomenetelma WHERE nimi = 'Jyrsintäkorjaukset (HJYR/TJYR)')::INT,-- tyomenetelma 
    'AB, Asfalttibetoni'::TEXT,                         -- massatyyppi, ei tietoa miten tämä reikäpaikkauksille, laitettu AB, failaa muuten NOT NULL constraint
    NULL::NUMERIC,                                      -- leveys
    NULL::NUMERIC,                                      -- massamenekki 
    NULL::NUMERIC,                                      -- massamaara 
    NULL::NUMERIC,                                      -- pintaala
    NULL::INTEGER,                                      -- raekoko 
    NULL::TEXT,                                         -- kuulamylly
    1500.0::NUMERIC,                                    -- kustannus
    'kpl'::TEXT,                                        -- yksikkö
    66::INT,                                            -- määrä
    (SELECT tierekisteriosoitteelle_viiva(20, 1, 1, 1, 120)) -- geometria
);
