-- -24 alkavilla urakoilla oli virhe kattohinnan ylityksen siirron prosenttirajoituksessa. Se ei ollut niillä käytössä.
-- Korjataan se tässä.
UPDATE urakka_parametrit up SET kattohintaylityksen_siirron_prosenttirajoitus = 0.03
  FROM urakka u
 WHERE u.id = up.urakkaid
   AND EXTRACT (YEAR FROM u.alkupvm) = 2024;

-- -25 alkavilla urakoilla on virhe tavoitehinnan ylityksen maksuprosenteissa. Korjataan ne tässä
UPDATE urakka_parametrit up
   SET tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti = 75.00,
       tavoitehinnan_ylityksen_tilaajan_maksuprosentti = 25.00
  FROM urakka u
 WHERE u.id = up.urakkaid
   AND EXTRACT(YEAR FROM u.alkupvm) >= 2025
   AND u.tyyppi = 'teiden-hoito';
