-- Poista indeksit liittyen ulkoinen-id, luoja ja lisää ulkoinen-id, urakka-id
ALTER TABLE laatupoikkeama
    DROP CONSTRAINT uniikki_ulkoinen_laatupoikkeama;

CREATE UNIQUE INDEX laatupoikkeama_ulkoinen_id_urakka_uindex
    on laatupoikkeama (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX siltatarkastus_ulkoinen_id_urakka_uindex
    ON siltatarkastus (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX turvallisuuspoikkeama_ulkoinen_id_urakka_uindex
    ON turvallisuuspoikkeama (ulkoinen_id, urakka);

CREATE UNIQUE INDEX paivystys_ulkoinen_id_urakka_uindex
    ON paivystys (ulkoinen_id, urakka);

CREATE UNIQUE INDEX tiemerkinnan_yksikkohintainen_toteuma_ulkoinen_id_urakka_uindex
    ON tiemerkinnan_yksikkohintainen_toteuma (ulkoinen_id, urakka);

-- Jotta tarkastuksille voidaan lisätä uniikkiusindeksit, siivotaan sieltä ensin duplikaatit pois, niitä on 28 kpl
DO $$
    DECLARE
        rivi record;
    BEGIN

        FOR rivi in (SELECT MIN(id) as id, urakka, ulkoinen_id, poistettu, tyyppi, count(ulkoinen_id)
                     FROM tarkastus
                     WHERE luotu > '2022-05-01'
                     GROUP BY urakka, ulkoinen_id, poistettu, tyyppi
                     HAVING count(ulkoinen_id) > 1)
            loop
                update tarkastus set poistettu = true, muokattu = NOW() where id = rivi.id;
            end loop;
    end
$$ language plpgsql;

-- unique index
create unique index tarkastus_2015_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2015_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2015_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2015_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2015_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2015_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2015_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2015_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2016_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2016_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2016_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2016_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2016_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2016_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2016_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2016_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2017_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2017_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2017_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2017_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2017_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2017_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2017_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2017_q4 (ulkoinen_id, urakka, poistettu, tyyppi) WHERE luotu > '2018-01-01';

create unique index tarkastus_2018_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2018_q1 (ulkoinen_id, urakka, poistettu, tyyppi) WHERE luotu > '2018-03-01';
create unique index tarkastus_2018_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2018_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2018_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2018_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2018_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2018_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2019_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2019_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2019_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2019_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2019_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2019_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2019_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2019_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2020_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2020_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2020_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2020_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2020_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2020_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2020_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2020_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2021_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2021_q1 (ulkoinen_id, urakka, poistettu, tyyppi) WHERE luotu > '2021-06-01';
create unique index tarkastus_2021_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2021_q2 (ulkoinen_id, urakka, poistettu, tyyppi) WHERE luotu > '2021-06-01';
create unique index tarkastus_2021_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2021_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2021_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2021_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2022_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2022_q1 (ulkoinen_id, urakka, poistettu, tyyppi) WHERE luotu > '2022-03-01';
create unique index tarkastus_2022_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2022_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2022_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2022_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2022_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2022_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2023_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2023_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2023_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2023_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2023_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2023_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2023_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2023_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2024_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2024_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2024_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2024_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2024_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2024_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2024_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2024_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2025_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2025_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2025_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2025_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2025_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2025_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2025_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2025_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

create unique index tarkastus_2026_q1_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2026_q1 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2026_q2_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2026_q2 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2026_q3_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2026_q3 (ulkoinen_id, urakka, poistettu, tyyppi);
create unique index tarkastus_2026_q4_ulkoinen_id_urakka_poistettu_uindex
    on tarkastus_2026_q4 (ulkoinen_id, urakka, poistettu, tyyppi);

-- Siivotaan duplikaatit toteumat pois, ennenkuin ajetaan unique indexit toteumille, niitä on 28811
DO $$
    DECLARE
        rivi record;
    BEGIN
        FOR rivi in (SELECT MIN(id) as id, urakka, ulkoinen_id, poistettu
                     FROM toteuma
                     WHERE luotu > '2022-05-01'
                     GROUP BY urakka, ulkoinen_id, poistettu
                     HAVING count(ulkoinen_id) > 1)
            loop
                update toteuma set poistettu = true, muokattu = NOW() where id = rivi.id;
                update toteuma_materiaali set poistettu = true, muokattu = NOW() where toteuma = rivi.id;
                update toteuma_tehtava set poistettu = true, muokattu = NOW() where toteuma = rivi.id;

            end loop;
    end
$$ language plpgsql;

CREATE UNIQUE INDEX toteuma_010101_191001_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_010101_191001 (ulkoinen_id, urakka, poistettu) where luotu > '2071-01-01';

CREATE UNIQUE INDEX toteuma_191001_200701_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_191001_200701 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_200701_210101_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_200701_210101 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_210101_210701_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_210101_210701 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_210701_220101_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_210701_220101 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_220101_220701_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_220101_220701 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_220701_230101_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_220701_230101 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_230101_230701_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_230101_230701 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_230701_240101_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_230701_240101 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_240101_240701_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_240101_240701 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_240701_250101_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_240701_250101 (ulkoinen_id, urakka, poistettu);

CREATE UNIQUE INDEX toteuma_250101_991231_ulkoinen_id_urakka_poistettu_uindex
    ON toteuma_250101_991231 (ulkoinen_id, urakka, poistettu);
