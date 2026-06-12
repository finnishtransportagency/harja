-- Partitioi tarkastustaulua vuosiksi 2027-2032

-- Luo taulut kvartaaleittain 2027-2032
CREATE TABLE tarkastus_2027_q1 ( CHECK( aika >= '2027-01-01'::date AND aika < '2027-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2027_q2 ( CHECK( aika >= '2027-04-01'::date AND aika < '2027-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2027_q3 ( CHECK( aika >= '2027-07-01'::date AND aika < '2027-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2027_q4 ( CHECK( aika >= '2027-10-01'::date AND aika < '2028-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2028_q1 ( CHECK( aika >= '2028-01-01'::date AND aika < '2028-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2028_q2 ( CHECK( aika >= '2028-04-01'::date AND aika < '2028-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2028_q3 ( CHECK( aika >= '2028-07-01'::date AND aika < '2028-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2028_q4 ( CHECK( aika >= '2028-10-01'::date AND aika < '2029-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2029_q1 ( CHECK( aika >= '2029-01-01'::date AND aika < '2029-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2029_q2 ( CHECK( aika >= '2029-04-01'::date AND aika < '2029-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2029_q3 ( CHECK( aika >= '2029-07-01'::date AND aika < '2029-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2029_q4 ( CHECK( aika >= '2029-10-01'::date AND aika < '2030-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2030_q1 ( CHECK( aika >= '2030-01-01'::date AND aika < '2030-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2030_q2 ( CHECK( aika >= '2030-04-01'::date AND aika < '2030-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2030_q3 ( CHECK( aika >= '2030-07-01'::date AND aika < '2030-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2030_q4 ( CHECK( aika >= '2030-10-01'::date AND aika < '2031-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2031_q1 ( CHECK( aika >= '2031-01-01'::date AND aika < '2031-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2031_q2 ( CHECK( aika >= '2031-04-01'::date AND aika < '2031-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2031_q3 ( CHECK( aika >= '2031-07-01'::date AND aika < '2031-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2031_q4 ( CHECK( aika >= '2031-10-01'::date AND aika < '2032-01-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2032_q1 ( CHECK( aika >= '2032-01-01'::date AND aika < '2032-04-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2032_q2 ( CHECK( aika >= '2032-04-01'::date AND aika < '2032-07-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2032_q3 ( CHECK( aika >= '2032-07-01'::date AND aika < '2032-10-01'::date) ) INHERITS (tarkastus);
CREATE TABLE tarkastus_2032_q4 ( CHECK( aika >= '2032-10-01'::date AND aika < '2033-01-01'::date) ) INHERITS (tarkastus);

-- Luo indeksit osille
CREATE UNIQUE INDEX tarkastus_2027_q1_id_idx ON tarkastus_2027_q1 (id);
CREATE UNIQUE INDEX tarkastus_2027_q2_id_idx ON tarkastus_2027_q2 (id);
CREATE UNIQUE INDEX tarkastus_2027_q3_id_idx ON tarkastus_2027_q3 (id);
CREATE UNIQUE INDEX tarkastus_2027_q4_id_idx ON tarkastus_2027_q4 (id);
CREATE UNIQUE INDEX tarkastus_2028_q1_id_idx ON tarkastus_2028_q1 (id);
CREATE UNIQUE INDEX tarkastus_2028_q2_id_idx ON tarkastus_2028_q2 (id);
CREATE UNIQUE INDEX tarkastus_2028_q3_id_idx ON tarkastus_2028_q3 (id);
CREATE UNIQUE INDEX tarkastus_2028_q4_id_idx ON tarkastus_2028_q4 (id);
CREATE UNIQUE INDEX tarkastus_2029_q1_id_idx ON tarkastus_2029_q1 (id);
CREATE UNIQUE INDEX tarkastus_2029_q2_id_idx ON tarkastus_2029_q2 (id);
CREATE UNIQUE INDEX tarkastus_2029_q3_id_idx ON tarkastus_2029_q3 (id);
CREATE UNIQUE INDEX tarkastus_2029_q4_id_idx ON tarkastus_2029_q4 (id);
CREATE UNIQUE INDEX tarkastus_2030_q1_id_idx ON tarkastus_2030_q1 (id);
CREATE UNIQUE INDEX tarkastus_2030_q2_id_idx ON tarkastus_2030_q2 (id);
CREATE UNIQUE INDEX tarkastus_2030_q3_id_idx ON tarkastus_2030_q3 (id);
CREATE UNIQUE INDEX tarkastus_2030_q4_id_idx ON tarkastus_2030_q4 (id);
CREATE UNIQUE INDEX tarkastus_2031_q1_id_idx ON tarkastus_2031_q1 (id);
CREATE UNIQUE INDEX tarkastus_2031_q2_id_idx ON tarkastus_2031_q2 (id);
CREATE UNIQUE INDEX tarkastus_2031_q3_id_idx ON tarkastus_2031_q3 (id);
CREATE UNIQUE INDEX tarkastus_2031_q4_id_idx ON tarkastus_2031_q4 (id);
CREATE UNIQUE INDEX tarkastus_2032_q1_id_idx ON tarkastus_2032_q1 (id);
CREATE UNIQUE INDEX tarkastus_2032_q2_id_idx ON tarkastus_2032_q2 (id);
CREATE UNIQUE INDEX tarkastus_2032_q3_id_idx ON tarkastus_2032_q3 (id);
CREATE UNIQUE INDEX tarkastus_2032_q4_id_idx ON tarkastus_2032_q4 (id);

CREATE INDEX tarkastus_2027_q1_urakka_idx ON tarkastus_2027_q1 (urakka);
CREATE INDEX tarkastus_2027_q2_urakka_idx ON tarkastus_2027_q2 (urakka);
CREATE INDEX tarkastus_2027_q3_urakka_idx ON tarkastus_2027_q3 (urakka);
CREATE INDEX tarkastus_2027_q4_urakka_idx ON tarkastus_2027_q4 (urakka);
CREATE INDEX tarkastus_2028_q1_urakka_idx ON tarkastus_2028_q1 (urakka);
CREATE INDEX tarkastus_2028_q2_urakka_idx ON tarkastus_2028_q2 (urakka);
CREATE INDEX tarkastus_2028_q3_urakka_idx ON tarkastus_2028_q3 (urakka);
CREATE INDEX tarkastus_2028_q4_urakka_idx ON tarkastus_2028_q4 (urakka);
CREATE INDEX tarkastus_2029_q1_urakka_idx ON tarkastus_2029_q1 (urakka);
CREATE INDEX tarkastus_2029_q2_urakka_idx ON tarkastus_2029_q2 (urakka);
CREATE INDEX tarkastus_2029_q3_urakka_idx ON tarkastus_2029_q3 (urakka);
CREATE INDEX tarkastus_2029_q4_urakka_idx ON tarkastus_2029_q4 (urakka);
CREATE INDEX tarkastus_2030_q1_urakka_idx ON tarkastus_2030_q1 (urakka);
CREATE INDEX tarkastus_2030_q2_urakka_idx ON tarkastus_2030_q2 (urakka);
CREATE INDEX tarkastus_2030_q3_urakka_idx ON tarkastus_2030_q3 (urakka);
CREATE INDEX tarkastus_2030_q4_urakka_idx ON tarkastus_2030_q4 (urakka);
CREATE INDEX tarkastus_2031_q1_urakka_idx ON tarkastus_2031_q1 (urakka);
CREATE INDEX tarkastus_2031_q2_urakka_idx ON tarkastus_2031_q2 (urakka);
CREATE INDEX tarkastus_2031_q3_urakka_idx ON tarkastus_2031_q3 (urakka);
CREATE INDEX tarkastus_2031_q4_urakka_idx ON tarkastus_2031_q4 (urakka);
CREATE INDEX tarkastus_2032_q1_urakka_idx ON tarkastus_2032_q1 (urakka);
CREATE INDEX tarkastus_2032_q2_urakka_idx ON tarkastus_2032_q2 (urakka);
CREATE INDEX tarkastus_2032_q3_urakka_idx ON tarkastus_2032_q3 (urakka);
CREATE INDEX tarkastus_2032_q4_urakka_idx ON tarkastus_2032_q4 (urakka);

CREATE UNIQUE INDEX tarkastus_2027_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2027_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2027_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2027_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2027_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2027_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2027_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2027_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2028_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2028_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2028_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2028_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2028_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2028_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2028_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2028_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2029_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2029_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2029_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2029_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2029_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2029_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2029_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2029_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2030_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2030_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2030_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2030_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2030_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2030_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2030_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2030_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2031_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2031_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2031_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2031_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2031_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2031_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2031_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2031_q4 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2032_q1_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2032_q1 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2032_q2_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2032_q2 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2032_q3_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2032_q3 (ulkoinen_id,luoja,tyyppi);
CREATE UNIQUE INDEX tarkastus_2032_q4_ulkoinen_id_luoja_tyyppi_idx ON tarkastus_2032_q4 (ulkoinen_id,luoja,tyyppi);



-- Päivitä insert trigger
CREATE OR REPLACE FUNCTION tarkastus_insert() RETURNS trigger AS $$
DECLARE
    aika date;
BEGIN
    aika := NEW.aika;
    IF aika < '2015-01-01'::date THEN
        INSERT INTO tarkastus_ennen_2015 VALUES (NEW.*);
    ELSIF aika >= '2015-01-01'::date AND aika < '2015-04-01'::date THEN
        INSERT INTO tarkastus_2015_q1 VALUES (NEW.*);
    ELSIF aika >= '2015-04-01'::date AND aika < '2015-07-01'::date THEN
        INSERT INTO tarkastus_2015_q2 VALUES (NEW.*);
    ELSIF aika >= '2015-07-01'::date AND aika < '2015-10-01'::date THEN
        INSERT INTO tarkastus_2015_q3 VALUES (NEW.*);
    ELSIF aika >= '2015-10-01'::date AND aika < '2016-01-01'::date THEN
        INSERT INTO tarkastus_2015_q4 VALUES (NEW.*);
    ELSIF aika >= '2016-01-01'::date AND aika < '2016-04-01'::date THEN
        INSERT INTO tarkastus_2016_q1 VALUES (NEW.*);
    ELSIF aika >= '2016-04-01'::date AND aika < '2016-07-01'::date THEN
        INSERT INTO tarkastus_2016_q2 VALUES (NEW.*);
    ELSIF aika >= '2016-07-01'::date AND aika < '2016-10-01'::date THEN
        INSERT INTO tarkastus_2016_q3 VALUES (NEW.*);
    ELSIF aika >= '2016-10-01'::date AND aika < '2017-01-01'::date THEN
        INSERT INTO tarkastus_2016_q4 VALUES (NEW.*);
    ELSIF aika >= '2017-01-01'::date AND aika < '2017-04-01'::date THEN
        INSERT INTO tarkastus_2017_q1 VALUES (NEW.*);
    ELSIF aika >= '2017-04-01'::date AND aika < '2017-07-01'::date THEN
        INSERT INTO tarkastus_2017_q2 VALUES (NEW.*);
    ELSIF aika >= '2017-07-01'::date AND aika < '2017-10-01'::date THEN
        INSERT INTO tarkastus_2017_q3 VALUES (NEW.*);
    ELSIF aika >= '2017-10-01'::date AND aika < '2018-01-01'::date THEN
        INSERT INTO tarkastus_2017_q4 VALUES (NEW.*);
    ELSIF aika >= '2018-01-01'::date AND aika < '2018-04-01'::date THEN
        INSERT INTO tarkastus_2018_q1 VALUES (NEW.*);
    ELSIF aika >= '2018-04-01'::date AND aika < '2018-07-01'::date THEN
        INSERT INTO tarkastus_2018_q2 VALUES (NEW.*);
    ELSIF aika >= '2018-07-01'::date AND aika < '2018-10-01'::date THEN
        INSERT INTO tarkastus_2018_q3 VALUES (NEW.*);
    ELSIF aika >= '2018-10-01'::date AND aika < '2019-01-01'::date THEN
        INSERT INTO tarkastus_2018_q4 VALUES (NEW.*);
    ELSIF aika >= '2019-01-01'::date AND aika < '2019-04-01'::date THEN
        INSERT INTO tarkastus_2019_q1 VALUES (NEW.*);
    ELSIF aika >= '2019-04-01'::date AND aika < '2019-07-01'::date THEN
        INSERT INTO tarkastus_2019_q2 VALUES (NEW.*);
    ELSIF aika >= '2019-07-01'::date AND aika < '2019-10-01'::date THEN
        INSERT INTO tarkastus_2019_q3 VALUES (NEW.*);
    ELSIF aika >= '2019-10-01'::date AND aika < '2020-01-01'::date THEN
        INSERT INTO tarkastus_2019_q4 VALUES (NEW.*);
    ELSIF aika >= '2020-01-01'::date AND aika < '2020-04-01'::date THEN
        INSERT INTO tarkastus_2020_q1 VALUES (NEW.*);
    ELSIF aika >= '2020-04-01'::date AND aika < '2020-07-01'::date THEN
        INSERT INTO tarkastus_2020_q2 VALUES (NEW.*);
    ELSIF aika >= '2020-07-01'::date AND aika < '2020-10-01'::date THEN
        INSERT INTO tarkastus_2020_q3 VALUES (NEW.*);
    ELSIF aika >= '2020-10-01'::date AND aika < '2021-01-01'::date THEN
        INSERT INTO tarkastus_2020_q4 VALUES (NEW.*);
    ELSIF aika >= '2021-01-01'::date AND aika < '2021-04-01'::date THEN
        INSERT INTO tarkastus_2021_q1 VALUES (NEW.*);
    ELSIF aika >= '2021-04-01'::date AND aika < '2021-07-01'::date THEN
        INSERT INTO tarkastus_2021_q2 VALUES (NEW.*);
    ELSIF aika >= '2021-07-01'::date AND aika < '2021-10-01'::date THEN
        INSERT INTO tarkastus_2021_q3 VALUES (NEW.*);
    ELSIF aika >= '2021-10-01'::date AND aika < '2022-01-01'::date THEN
        INSERT INTO tarkastus_2021_q4 VALUES (NEW.*);
    ELSIF aika >= '2022-01-01'::date AND aika < '2022-04-01'::date THEN
        INSERT INTO tarkastus_2022_q1 VALUES (NEW.*);
    ELSIF aika >= '2022-04-01'::date AND aika < '2022-07-01'::date THEN
        INSERT INTO tarkastus_2022_q2 VALUES (NEW.*);
    ELSIF aika >= '2022-07-01'::date AND aika < '2022-10-01'::date THEN
        INSERT INTO tarkastus_2022_q3 VALUES (NEW.*);
    ELSIF aika >= '2022-10-01'::date AND aika < '2023-01-01'::date THEN
        INSERT INTO tarkastus_2022_q4 VALUES (NEW.*);
    ELSIF aika >= '2023-01-01'::date AND aika < '2023-04-01'::date THEN
        INSERT INTO tarkastus_2023_q1 VALUES (NEW.*);
    ELSIF aika >= '2023-04-01'::date AND aika < '2023-07-01'::date THEN
        INSERT INTO tarkastus_2023_q2 VALUES (NEW.*);
    ELSIF aika >= '2023-07-01'::date AND aika < '2023-10-01'::date THEN
        INSERT INTO tarkastus_2023_q3 VALUES (NEW.*);
    ELSIF aika >= '2023-10-01'::date AND aika < '2024-01-01'::date THEN
        INSERT INTO tarkastus_2023_q4 VALUES (NEW.*);
    ELSIF aika >= '2024-01-01'::date AND aika < '2024-04-01'::date THEN
        INSERT INTO tarkastus_2024_q1 VALUES (NEW.*);
    ELSIF aika >= '2024-04-01'::date AND aika < '2024-07-01'::date THEN
        INSERT INTO tarkastus_2024_q2 VALUES (NEW.*);
    ELSIF aika >= '2024-07-01'::date AND aika < '2024-10-01'::date THEN
        INSERT INTO tarkastus_2024_q3 VALUES (NEW.*);
    ELSIF aika >= '2024-10-01'::date AND aika < '2025-01-01'::date THEN
        INSERT INTO tarkastus_2024_q4 VALUES (NEW.*);
    ELSIF aika >= '2025-01-01'::date AND aika < '2025-04-01'::date THEN
        INSERT INTO tarkastus_2025_q1 VALUES (NEW.*);
    ELSIF aika >= '2025-04-01'::date AND aika < '2025-07-01'::date THEN
        INSERT INTO tarkastus_2025_q2 VALUES (NEW.*);
    ELSIF aika >= '2025-07-01'::date AND aika < '2025-10-01'::date THEN
        INSERT INTO tarkastus_2025_q3 VALUES (NEW.*);
    ELSIF aika >= '2025-10-01'::date AND aika < '2026-01-01'::date THEN
        INSERT INTO tarkastus_2025_q4 VALUES (NEW.*);
    ELSIF aika >= '2026-01-01'::date AND aika < '2026-04-01'::date THEN
        INSERT INTO tarkastus_2026_q1 VALUES (NEW.*);
    ELSIF aika >= '2026-04-01'::date AND aika < '2026-07-01'::date THEN
        INSERT INTO tarkastus_2026_q2 VALUES (NEW.*);
    ELSIF aika >= '2026-07-01'::date AND aika < '2026-10-01'::date THEN
        INSERT INTO tarkastus_2026_q3 VALUES (NEW.*);
    ELSIF aika >= '2026-10-01'::date AND aika < '2027-01-01'::date THEN
        INSERT INTO tarkastus_2026_q4 VALUES (NEW.*);
    ELSIF aika >= '2027-01-01'::date AND aika < '2027-04-01'::date THEN
        INSERT INTO tarkastus_2027_q1 VALUES (NEW.*);
    ELSIF aika >= '2027-04-01'::date AND aika < '2027-07-01'::date THEN
        INSERT INTO tarkastus_2027_q2 VALUES (NEW.*);
    ELSIF aika >= '2027-07-01'::date AND aika < '2027-10-01'::date THEN
        INSERT INTO tarkastus_2027_q3 VALUES (NEW.*);
    ELSIF aika >= '2027-10-01'::date AND aika < '2028-01-01'::date THEN
        INSERT INTO tarkastus_2027_q4 VALUES (NEW.*);
    ELSIF aika >= '2028-01-01'::date AND aika < '2028-04-01'::date THEN
        INSERT INTO tarkastus_2028_q1 VALUES (NEW.*);
    ELSIF aika >= '2028-04-01'::date AND aika < '2028-07-01'::date THEN
        INSERT INTO tarkastus_2028_q2 VALUES (NEW.*);
    ELSIF aika >= '2028-07-01'::date AND aika < '2028-10-01'::date THEN
        INSERT INTO tarkastus_2028_q3 VALUES (NEW.*);
    ELSIF aika >= '2028-10-01'::date AND aika < '2029-01-01'::date THEN
        INSERT INTO tarkastus_2028_q4 VALUES (NEW.*);
    ELSIF aika >= '2029-01-01'::date AND aika < '2029-04-01'::date THEN
        INSERT INTO tarkastus_2029_q1 VALUES (NEW.*);
    ELSIF aika >= '2029-04-01'::date AND aika < '2029-07-01'::date THEN
        INSERT INTO tarkastus_2029_q2 VALUES (NEW.*);
    ELSIF aika >= '2029-07-01'::date AND aika < '2029-10-01'::date THEN
        INSERT INTO tarkastus_2029_q3 VALUES (NEW.*);
    ELSIF aika >= '2029-10-01'::date AND aika < '2030-01-01'::date THEN
        INSERT INTO tarkastus_2029_q4 VALUES (NEW.*);
    ELSIF aika >= '2030-01-01'::date AND aika < '2030-04-01'::date THEN
        INSERT INTO tarkastus_2030_q1 VALUES (NEW.*);
    ELSIF aika >= '2030-04-01'::date AND aika < '2030-07-01'::date THEN
        INSERT INTO tarkastus_2030_q2 VALUES (NEW.*);
    ELSIF aika >= '2030-07-01'::date AND aika < '2030-10-01'::date THEN
        INSERT INTO tarkastus_2030_q3 VALUES (NEW.*);
    ELSIF aika >= '2030-10-01'::date AND aika < '2031-01-01'::date THEN
        INSERT INTO tarkastus_2030_q4 VALUES (NEW.*);
    ELSIF aika >= '2031-01-01'::date AND aika < '2031-04-01'::date THEN
        INSERT INTO tarkastus_2031_q1 VALUES (NEW.*);
    ELSIF aika >= '2031-04-01'::date AND aika < '2031-07-01'::date THEN
        INSERT INTO tarkastus_2031_q2 VALUES (NEW.*);
    ELSIF aika >= '2031-07-01'::date AND aika < '2031-10-01'::date THEN
        INSERT INTO tarkastus_2031_q3 VALUES (NEW.*);
    ELSIF aika >= '2031-10-01'::date AND aika < '2032-01-01'::date THEN
        INSERT INTO tarkastus_2031_q4 VALUES (NEW.*);
    ELSIF aika >= '2032-01-01'::date AND aika < '2032-04-01'::date THEN
        INSERT INTO tarkastus_2032_q1 VALUES (NEW.*);
    ELSIF aika >= '2032-04-01'::date AND aika < '2032-07-01'::date THEN
        INSERT INTO tarkastus_2032_q2 VALUES (NEW.*);
    ELSIF aika >= '2032-07-01'::date AND aika < '2032-10-01'::date THEN
        INSERT INTO tarkastus_2032_q3 VALUES (NEW.*);
    ELSIF aika >= '2032-10-01'::date AND aika < '2033-01-01'::date THEN
        INSERT INTO tarkastus_2032_q4 VALUES (NEW.*);

    ELSE
        RAISE EXCEPTION 'Taululle tarkastus ei löydy insert ehtoa, korjaa tarkastus_insert() sproc!';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tg_tarkastus_insert ON tarkastus;
CREATE TRIGGER tg_tarkastus_insert
    BEFORE INSERT ON tarkastus
    FOR EACH ROW EXECUTE PROCEDURE tarkastus_insert();

