-- pueretaan aiemman viimeisen partition alkanut pvm rajoite
ALTER TABLE toteuma_250101_991231 DROP CONSTRAINT toteuma_250101_991231_alkanut_check;

-- Luodaan 6kk mittaisia partitioita toteumille 1.1.2030 saakka
SELECT * FROM luo_toteumataulun_partitio( '2025-01-01'::DATE, '2025-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2025-07-01'::DATE, '2026-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2026-01-01'::DATE, '2026-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2026-07-01'::DATE, '2027-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2027-01-01'::DATE, '2027-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2027-07-01'::DATE, '2028-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2028-01-01'::DATE, '2028-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2028-07-01'::DATE, '2029-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2029-01-01'::DATE, '2029-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2029-07-01'::DATE, '2030-01-01'::DATE);

-- tulevaisuuteen typotetut jne toteumat tänne
SELECT * FROM luo_toteumataulun_partitio( '2030-01-01'::DATE, '9999-12-31'::DATE);

-- Päivitetään insert trigger toteumapartitioille, jotta se huomioi lisätyt partitiot
CREATE OR REPLACE FUNCTION toteuma_insert() RETURNS trigger AS $$
DECLARE
    alkanut date;
BEGIN
    alkanut := NEW.alkanut;
    IF alkanut < '2019-10-01'::date THEN
        INSERT INTO toteuma_010101_191001 VALUES (NEW.*);
    ELSIF alkanut >= '2019-10-01'::date AND alkanut < '2020-07-01'::date THEN
        INSERT INTO toteuma_191001_200701 VALUES (NEW.*);
    ELSIF alkanut >= '2020-07-01'::date AND alkanut < '2021-01-01'::date THEN
        INSERT INTO toteuma_200701_210101 VALUES (NEW.*);
    ELSIF alkanut >= '2021-01-01'::date AND alkanut < '2021-07-01'::date THEN
        INSERT INTO toteuma_210101_210701 VALUES (NEW.*);
    ELSIF alkanut >= '2021-07-01'::date AND alkanut < '2022-01-01'::date THEN
        INSERT INTO toteuma_210701_220101 VALUES (NEW.*);
    ELSIF alkanut >= '2022-01-01'::date AND alkanut < '2022-07-01'::date THEN
        INSERT INTO toteuma_220101_220701 VALUES (NEW.*);
    ELSIF alkanut >= '2022-07-01'::date AND alkanut < '2023-01-01'::date THEN
        INSERT INTO toteuma_220701_230101 VALUES (NEW.*);
    ELSIF alkanut >= '2023-01-01'::date AND alkanut < '2023-07-01'::date THEN
        INSERT INTO toteuma_230101_230701 VALUES (NEW.*);
    ELSIF alkanut >= '2023-07-01'::date AND alkanut < '2024-01-01'::date THEN
        INSERT INTO toteuma_230701_240101 VALUES (NEW.*);
    ELSIF alkanut >= '2024-01-01'::date AND alkanut < '2024-07-01'::date THEN
        INSERT INTO toteuma_240101_240701 VALUES (NEW.*);
    ELSIF alkanut >= '2024-07-01'::date AND alkanut < '2025-01-01'::date THEN
        INSERT INTO toteuma_240701_250101 VALUES (NEW.*);

    ELSIF alkanut >= '2025-01-01'::date AND alkanut < '2025-07-01'::date THEN
        INSERT INTO toteuma_250101_250701 VALUES (NEW.*);
    ELSIF alkanut >= '2025-07-01'::date AND alkanut < '2026-01-01'::date THEN
        INSERT INTO toteuma_250701_260101 VALUES (NEW.*);
    ELSIF alkanut >= '2026-01-01'::date AND alkanut < '2026-07-01'::date THEN
        INSERT INTO toteuma_260101_260701 VALUES (NEW.*);
    ELSIF alkanut >= '2026-07-01'::date AND alkanut < '2027-01-01'::date THEN
        INSERT INTO toteuma_260701_270101 VALUES (NEW.*);
    ELSIF alkanut >= '2027-01-01'::date AND alkanut < '2027-07-01'::date THEN
        INSERT INTO toteuma_270101_270701 VALUES (NEW.*);
    ELSIF alkanut >= '2027-07-01'::date AND alkanut < '2028-01-01'::date THEN
        INSERT INTO toteuma_270701_280101 VALUES (NEW.*);
    ELSIF alkanut >= '2028-01-01'::date AND alkanut < '2028-07-01'::date THEN
        INSERT INTO toteuma_280101_280701 VALUES (NEW.*);
    ELSIF alkanut >= '2028-07-01'::date AND alkanut < '2029-01-01'::date THEN
        INSERT INTO toteuma_280701_290101 VALUES (NEW.*);
    ELSIF alkanut >= '2029-01-01'::date AND alkanut < '2029-07-01'::date THEN
        INSERT INTO toteuma_290101_290701 VALUES (NEW.*);
    ELSIF alkanut >= '2029-07-01'::date AND alkanut < '2030-01-01'::date THEN
        INSERT INTO toteuma_290701_300101 VALUES (NEW.*);

        -- kaatissäkki kaikelle liian uudelle, typotetulle jne. Jos ja kun Harja elää 1.1.2025 pitempään, muuta
        -- tätä funktiota ja luo tarvittava määrä hoitokausipartitioita lisää jotta saadaan partitioinnin
        -- hyödyt myös silloin käyttöön
    ELSIF alkanut >= '2030-01-01'::date THEN
        INSERT INTO toteuma_300101_991231 VALUES (NEW.*);  ELSE
        RAISE EXCEPTION 'Taululle toteuma ei löydy insert ehtoa, korjaa toteuma_insert() sproc!';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION siirra_aikavalin_toteumat(alkupvm DATE, loppupvm DATE)
    RETURNS VOID AS
$$
BEGIN
    RAISE NOTICE 'Siirretään toteumat aikaväliltä % - %', alkupvm, loppupvm;
      WITH x AS (
          DELETE FROM toteuma WHERE alkanut BETWEEN alkupvm AND loppupvm returning *
      )
    INSERT INTO toteuma SELECT * FROM x;
END
$$ LANGUAGE plpgsql;

-- Siirretään kerralla kaikki toteumat 1.1.2025 eteenpäin, niitä ei käytännössä pitäisi olla.
-- Joitakin typoja voi olla eri ympäristöissä, joten loppuajankohdaksi date max
SELECT * FROM siirra_aikavalin_toteumat( '2025-01-01'::DATE, '9999-12-31'::DATE);

-- poistetaan tyhjäksi ja turhaksi jäänyt edellinen viimeinen partitio
DROP TABLE toteuma_250101_991231;
