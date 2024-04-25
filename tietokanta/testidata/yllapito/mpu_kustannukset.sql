----------------------------
-- Muhoksen päällystysurakka
----------------------------

-- MPU kustannukset testidata 
INSERT INTO mpu_kustannukset (urakka, selite, summa, vuosi) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Arvonmuutokset', 
  1337, 
  2024
);


INSERT INTO mpu_kustannukset (urakka, selite, summa, vuosi) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Indeksi- ja kustannustason muutokset', 
  80085, 
  2024
);


INSERT INTO mpu_kustannukset (urakka, selite, summa, vuosi) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Muu kustannus', 
  1000000, 
  2024
);

INSERT INTO mpu_kustannukset (urakka, selite, summa, vuosi) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Työvoimakustannukset', 
  200000, 
  2024
);


INSERT INTO mpu_kustannukset (urakka, selite, summa, vuosi) 
VALUES (
  (SELECT id FROM urakka WHERE nimi LIKE 'Muhoksen päällystysurakka'), 
  'Kalustokustannukset', 
  75000, 
  2024
);
