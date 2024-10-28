-- Päivitetään null-arvot falseksi. Taulun sarakkeen default arvo on false. Jostakin syystä muutamalta riviltä tieto kuitenkin puuttui.
UPDATE tehtava
SET kasin_lisattava_maara = false,
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE kasin_lisattava_maara IS NULL;
