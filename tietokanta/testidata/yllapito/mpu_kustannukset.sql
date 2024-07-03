----------------------------
-- Muhoksen päällystysurakka
----------------------------

-- MPU kustannukset testidata 
INSERT INTO mpu_kustannukset (urakka, selite, kustannustyyppi, summa, vuosi, luotu, luoja) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Arvoa muutettiin', 
  'Arvonmuutokset'::mpu_kustannustyyppi_enum,
  1337, 
  2024,
  NOW(),
  (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
);


INSERT INTO mpu_kustannukset (urakka, selite, kustannustyyppi, summa, vuosi, luotu, luoja) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Indeksimuutos syyskuu', 
  'Indeksi- ja kustannustason muutokset'::mpu_kustannustyyppi_enum,
  80500, 
  2024,
  NOW(),
  (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
);


INSERT INTO mpu_kustannukset (urakka, selite, kustannustyyppi, summa, vuosi, luotu, luoja) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Työvoimakustannukset', 
  'Muut kustannukset'::mpu_kustannustyyppi_enum,
  200000, 
  2024,
  NOW(),
  (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
);


INSERT INTO mpu_kustannukset (urakka, selite, kustannustyyppi, summa, vuosi, luotu, luoja) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Kalustokustannukset', 
  'Muut kustannukset'::mpu_kustannustyyppi_enum,
  75000, 
  2024,
  NOW(),
  (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
);

INSERT INTO mpu_kustannukset (urakka, selite, kustannustyyppi, summa, vuosi, luotu, luoja) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Vanha kustannus', 
  'Muut kustannukset'::mpu_kustannustyyppi_enum,
  75000, 
  2018,
  NOW(),
  (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
);


INSERT INTO mpu_kustannukset (urakka, selite, kustannustyyppi, summa, vuosi, luotu, luoja) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Indeksimuutos 2017 elokuu', 
  'Indeksi- ja kustannustason muutokset'::mpu_kustannustyyppi_enum,
  75000, 
  2017,
  NOW(),
  (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
);
