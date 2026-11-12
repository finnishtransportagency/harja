-- Päivitetään urakka_parametrit
-- Pidä tämä jossain viimeisenä testidatan jälkeen
select aseta_urakka_parametrit();

UPDATE urakka_parametrit
SET hoitokauden_lopun_kattohinta_kerroin                = NULL
WHERE urakkaid IN (SELECT id
                   FROM urakka
                   WHERE alkupvm IN ('2019-10-01', '2020-10-01')
                     AND tyyppi = 'teiden-hoito');

-- Lisätään kaikille -25 alkaneille urakalle tieto, että muutosten hallinta on käytössä
-- Hox. Tuotannossa on urakka, joka alkaa -25 vuonna, mutta Sampon virheellisen syötön takia se alkaa 1.1. eikä 1.10.
UPDATE urakka_parametrit
SET muutosten_hallinta = true
WHERE urakkaid IN (SELECT id FROM urakka WHERE alkupvm >= '2025-01-01' AND tyyppi = 'teiden-hoito');

-- Asetetaan laskutusraja käyttöön "POP MHU Kajaani 2025-2030" -urakalle
UPDATE urakka_parametrit
SET laskutusraja_kaytossa = true
WHERE urakkaid = (SELECT id FROM urakka WHERE nimi = 'POP MHU Kajaani 2025-2030');

-- Asetetaan hoitovuoden alun tavoitehinta käyttöön kaikille urakoille, jotka alkavat vuonna 2025 tai sen jälkeen
-- -25 alkavilla urakoilla on virhe tavoitehinnan ylityksen maksuprosenteissa. Korjataan ne tässä
UPDATE urakka_parametrit up
SET hoitovuoden_alun_tavoitehinta_kaytossa = true,
    tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti = 75.00,
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti = 25.00
    FROM urakka u
WHERE u.id = up.urakkaid
  AND EXTRACT(YEAR FROM u.alkupvm) >= 2025
  AND u.tyyppi = 'teiden-hoito';

-- Myös -24 alkavilla urakoilla on kattohintaylityksen siirron prosenttirajoitus käytössä.
UPDATE urakka_parametrit up SET kattohintaylityksen_siirron_prosenttirajoitus = 0.03
    FROM urakka u
WHERE u.id = up.urakkaid
  AND EXTRACT (YEAR FROM u.alkupvm) = 2024;

-- Testiaineistossa on mhu+ urakka vuodelle -25, jolle menee defaulttina virheelliset prosentit. Säädetään se tässä kuntoon. Koskee siis vain lokaalikantaa
-- ja tästä ei tarvitse huolehtia tuotannossa
UPDATE urakka_parametrit up
SET tavoitehinnan_ylityksen_urakoitsijan_maksuprosentti = 50.00,
    tavoitehinnan_ylityksen_tilaajan_maksuprosentti = 50.00
    FROM urakka u
WHERE u.id = up.urakkaid
  AND EXTRACT(YEAR FROM u.alkupvm) >= 2025
  AND u.tyyppi = 'teiden-hoito'
  AND u.sopimustyyppi = 'mhu+';
