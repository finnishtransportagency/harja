-- 2019 ja 2020 alkavilla urakoilla on muokkaa_kattohinta_kasin, joka on merkitsevä tieto,
-- mutta selkiyden vuoksi asetetaan hoitokauden_lopu_kattohinta_kerroin nulliksi
UPDATE urakka_parametrit
   SET hoitokauden_lopun_kattohinta_kerroin                = NULL
    WHERE urakkaid IN (SELECT id
                        FROM urakka
                        WHERE alkupvm IN ('2019-10-01', '2020-10-01')
                          AND tyyppi = 'teiden-hoito');

-- Myös tarjouksen kattohinta pitää pystyä tallentamaan 2019 ja 2020 alkaville urakoille
ALTER TABLE urakka_tavoite
    ADD COLUMN IF NOT EXISTS tarjous_kattohinta NUMERIC;
