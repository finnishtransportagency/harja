-- DMS tarvitsee toteuma- ja tarkastustaulujen partitioille primary keyt
-- Päivitetään olemassaolleet UNIQUE indexit primary-avaimiksi
ALTER TABLE tarkastus_2015_q1 ADD CONSTRAINT tarkastus_2015_q1_pkey PRIMARY KEY USING INDEX tarkastus_2015_q1_id_idx;
ALTER TABLE tarkastus_2015_q2 ADD CONSTRAINT tarkastus_2015_q2_pkey PRIMARY KEY USING INDEX tarkastus_2015_q2_id_idx;
ALTER TABLE tarkastus_2015_q3 ADD CONSTRAINT tarkastus_2015_q3_pkey PRIMARY KEY USING INDEX tarkastus_2015_q3_id_idx;
ALTER TABLE tarkastus_2015_q4 ADD CONSTRAINT tarkastus_2015_q4_pkey PRIMARY KEY USING INDEX tarkastus_2015_q4_id_idx;
ALTER TABLE tarkastus_2016_q1 ADD CONSTRAINT tarkastus_2016_q1_pkey PRIMARY KEY USING INDEX tarkastus_2016_q1_id_idx;
ALTER TABLE tarkastus_2016_q2 ADD CONSTRAINT tarkastus_2016_q2_pkey PRIMARY KEY USING INDEX tarkastus_2016_q2_id_idx;
ALTER TABLE tarkastus_2016_q3 ADD CONSTRAINT tarkastus_2016_q3_pkey PRIMARY KEY USING INDEX tarkastus_2016_q3_id_idx;
ALTER TABLE tarkastus_2016_q4 ADD CONSTRAINT tarkastus_2016_q4_pkey PRIMARY KEY USING INDEX tarkastus_2016_q4_id_idx;
ALTER TABLE tarkastus_2017_q1 ADD CONSTRAINT tarkastus_2017_q1_pkey PRIMARY KEY USING INDEX tarkastus_2017_q1_id_idx;
ALTER TABLE tarkastus_2017_q2 ADD CONSTRAINT tarkastus_2017_q2_pkey PRIMARY KEY USING INDEX tarkastus_2017_q2_id_idx;
ALTER TABLE tarkastus_2017_q3 ADD CONSTRAINT tarkastus_2017_q3_pkey PRIMARY KEY USING INDEX tarkastus_2017_q3_id_idx;
ALTER TABLE tarkastus_2017_q4 ADD CONSTRAINT tarkastus_2017_q4_pkey PRIMARY KEY USING INDEX tarkastus_2017_q4_id_idx;
ALTER TABLE tarkastus_2018_q1 ADD CONSTRAINT tarkastus_2018_q1_pkey PRIMARY KEY USING INDEX tarkastus_2018_q1_id_idx;
ALTER TABLE tarkastus_2018_q2 ADD CONSTRAINT tarkastus_2018_q2_pkey PRIMARY KEY USING INDEX tarkastus_2018_q2_id_idx;
ALTER TABLE tarkastus_2018_q3 ADD CONSTRAINT tarkastus_2018_q3_pkey PRIMARY KEY USING INDEX tarkastus_2018_q3_id_idx;
ALTER TABLE tarkastus_2018_q4 ADD CONSTRAINT tarkastus_2018_q4_pkey PRIMARY KEY USING INDEX tarkastus_2018_q4_id_idx;
ALTER TABLE tarkastus_2019_q1 ADD CONSTRAINT tarkastus_2019_q1_pkey PRIMARY KEY USING INDEX tarkastus_2019_q1_id_idx;
ALTER TABLE tarkastus_2019_q2 ADD CONSTRAINT tarkastus_2019_q2_pkey PRIMARY KEY USING INDEX tarkastus_2019_q2_id_idx;
ALTER TABLE tarkastus_2019_q3 ADD CONSTRAINT tarkastus_2019_q3_pkey PRIMARY KEY USING INDEX tarkastus_2019_q3_id_idx;
ALTER TABLE tarkastus_2019_q4 ADD CONSTRAINT tarkastus_2019_q4_pkey PRIMARY KEY USING INDEX tarkastus_2019_q4_id_idx;
ALTER TABLE tarkastus_2020_q1 ADD CONSTRAINT tarkastus_2020_q1_pkey PRIMARY KEY USING INDEX tarkastus_2020_q1_id_idx;
ALTER TABLE tarkastus_2020_q2 ADD CONSTRAINT tarkastus_2020_q2_pkey PRIMARY KEY USING INDEX tarkastus_2020_q2_id_idx;
ALTER TABLE tarkastus_2020_q3 ADD CONSTRAINT tarkastus_2020_q3_pkey PRIMARY KEY USING INDEX tarkastus_2020_q3_id_idx;
ALTER TABLE tarkastus_2020_q4 ADD CONSTRAINT tarkastus_2020_q4_pkey PRIMARY KEY USING INDEX tarkastus_2020_q4_id_idx;
ALTER TABLE tarkastus_2021_q1 ADD CONSTRAINT tarkastus_2021_q1_pkey PRIMARY KEY USING INDEX tarkastus_2021_q1_id_idx;
ALTER TABLE tarkastus_2021_q2 ADD CONSTRAINT tarkastus_2021_q2_pkey PRIMARY KEY USING INDEX tarkastus_2021_q2_id_idx;
ALTER TABLE tarkastus_2021_q3 ADD CONSTRAINT tarkastus_2021_q3_pkey PRIMARY KEY USING INDEX tarkastus_2021_q3_id_idx;
ALTER TABLE tarkastus_2021_q4 ADD CONSTRAINT tarkastus_2021_q4_pkey PRIMARY KEY USING INDEX tarkastus_2021_q4_id_idx;
ALTER TABLE tarkastus_2022_q1 ADD CONSTRAINT tarkastus_2022_q1_pkey PRIMARY KEY USING INDEX tarkastus_2022_q1_id_idx;
ALTER TABLE tarkastus_2022_q2 ADD CONSTRAINT tarkastus_2022_q2_pkey PRIMARY KEY USING INDEX tarkastus_2022_q2_id_idx;
ALTER TABLE tarkastus_2022_q3 ADD CONSTRAINT tarkastus_2022_q3_pkey PRIMARY KEY USING INDEX tarkastus_2022_q3_id_idx;
ALTER TABLE tarkastus_2022_q4 ADD CONSTRAINT tarkastus_2022_q4_pkey PRIMARY KEY USING INDEX tarkastus_2022_q4_id_idx;
ALTER TABLE tarkastus_2023_q1 ADD CONSTRAINT tarkastus_2023_q1_pkey PRIMARY KEY USING INDEX tarkastus_2023_q1_id_idx;
ALTER TABLE tarkastus_2023_q2 ADD CONSTRAINT tarkastus_2023_q2_pkey PRIMARY KEY USING INDEX tarkastus_2023_q2_id_idx;
ALTER TABLE tarkastus_2023_q3 ADD CONSTRAINT tarkastus_2023_q3_pkey PRIMARY KEY USING INDEX tarkastus_2023_q3_id_idx;
ALTER TABLE tarkastus_2023_q4 ADD CONSTRAINT tarkastus_2023_q4_pkey PRIMARY KEY USING INDEX tarkastus_2023_q4_id_idx;
ALTER TABLE tarkastus_2024_q1 ADD CONSTRAINT tarkastus_2024_q1_pkey PRIMARY KEY USING INDEX tarkastus_2024_q1_id_idx;
ALTER TABLE tarkastus_2024_q2 ADD CONSTRAINT tarkastus_2024_q2_pkey PRIMARY KEY USING INDEX tarkastus_2024_q2_id_idx;
ALTER TABLE tarkastus_2024_q3 ADD CONSTRAINT tarkastus_2024_q3_pkey PRIMARY KEY USING INDEX tarkastus_2024_q3_id_idx;
ALTER TABLE tarkastus_2024_q4 ADD CONSTRAINT tarkastus_2024_q4_pkey PRIMARY KEY USING INDEX tarkastus_2024_q4_id_idx;
ALTER TABLE tarkastus_2025_q1 ADD CONSTRAINT tarkastus_2025_q1_pkey PRIMARY KEY USING INDEX tarkastus_2025_q1_id_idx;
ALTER TABLE tarkastus_2025_q2 ADD CONSTRAINT tarkastus_2025_q2_pkey PRIMARY KEY USING INDEX tarkastus_2025_q2_id_idx;
ALTER TABLE tarkastus_2025_q3 ADD CONSTRAINT tarkastus_2025_q3_pkey PRIMARY KEY USING INDEX tarkastus_2025_q3_id_idx;
ALTER TABLE tarkastus_2025_q4 ADD CONSTRAINT tarkastus_2025_q4_pkey PRIMARY KEY USING INDEX tarkastus_2025_q4_id_idx;
ALTER TABLE tarkastus_2026_q1 ADD CONSTRAINT tarkastus_2026_q1_pkey PRIMARY KEY USING INDEX tarkastus_2026_q1_id_idx;
ALTER TABLE tarkastus_2026_q2 ADD CONSTRAINT tarkastus_2026_q2_pkey PRIMARY KEY USING INDEX tarkastus_2026_q2_id_idx;
ALTER TABLE tarkastus_2026_q3 ADD CONSTRAINT tarkastus_2026_q3_pkey PRIMARY KEY USING INDEX tarkastus_2026_q3_id_idx;
ALTER TABLE tarkastus_2026_q4 ADD CONSTRAINT tarkastus_2026_q4_pkey PRIMARY KEY USING INDEX tarkastus_2026_q4_id_idx;
ALTER TABLE tarkastus_ennen_2015 ADD CONSTRAINT tarkastus_ennen_2015_pkey PRIMARY KEY USING INDEX tarkastus_ennen_2015_id_idx;

ALTER TABLE toteuma_191001_200701 ADD CONSTRAINT toteuma_191001_200701_pkey PRIMARY KEY USING INDEX toteuma_191001_200701_id_idx;
ALTER TABLE toteuma_200701_210101 ADD CONSTRAINT toteuma_200701_210101_pkey PRIMARY KEY USING INDEX toteuma_200701_210101_id_idx;
ALTER TABLE toteuma_210101_210701 ADD CONSTRAINT toteuma_210101_210701_pkey PRIMARY KEY USING INDEX toteuma_210101_210701_id_idx;
ALTER TABLE toteuma_210701_220101 ADD CONSTRAINT toteuma_210701_220101_pkey PRIMARY KEY USING INDEX toteuma_210701_220101_id_idx;
ALTER TABLE toteuma_220101_220701 ADD CONSTRAINT toteuma_220101_220701_pkey PRIMARY KEY USING INDEX toteuma_220101_220701_id_idx;
ALTER TABLE toteuma_220701_230101 ADD CONSTRAINT toteuma_220701_230101_pkey PRIMARY KEY USING INDEX toteuma_220701_230101_id_idx;
ALTER TABLE toteuma_230101_230701 ADD CONSTRAINT toteuma_230101_230701_pkey PRIMARY KEY USING INDEX toteuma_230101_230701_id_idx;
ALTER TABLE toteuma_230701_240101 ADD CONSTRAINT toteuma_230701_240101_pkey PRIMARY KEY USING INDEX toteuma_230701_240101_id_idx;
ALTER TABLE toteuma_240101_240701 ADD CONSTRAINT toteuma_240101_240701_pkey PRIMARY KEY USING INDEX toteuma_240101_240701_id_idx;
ALTER TABLE toteuma_240701_250101 ADD CONSTRAINT toteuma_240701_250101_pkey PRIMARY KEY USING INDEX toteuma_240701_250101_id_idx;
ALTER TABLE toteuma_250101_991231 ADD CONSTRAINT toteuma_250101_991231_pkey PRIMARY KEY USING INDEX toteuma_250101_991231_id_idx;

-- päivitetään vielä toteumataulun partitoiden luontiin tehty funktio, jotta jatkossa primary key syntyy sitä kautta
CREATE OR REPLACE FUNCTION luo_toteumataulun_partitio(alkupvm DATE, loppupvm DATE)
    RETURNS VOID AS
$$
DECLARE
    partitio text;
BEGIN
    partitio :=  'toteuma_' || TO_CHAR(alkupvm, 'YYMMDD') || '_' || TO_CHAR(loppupvm, 'YYMMDD');

    PERFORM validoi_hoitokauden_alkupvm(alkupvm);

    -- CREATE PARTITION TABLE, INHERITING FROM TOTEUMA
    EXECUTE 'CREATE TABLE IF NOT EXISTS ' || partitio ||
            ' ( CHECK( alkanut >= '''|| alkupvm || ''' AND alkanut < '''|| loppupvm ||''')) INHERITS (toteuma)';

    -- PRIMARY KEY SUBSTITUTE
    EXECUTE 'CREATE UNIQUE INDEX ' || partitio || '_id_idx ON ' || partitio || '(id)';
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio || '_pkey PRIMARY KEY USING INDEX '|| partitio || '_id_idx';

    -- OTHER INDEXES
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio || '_uniikki_ulkoinen_id_luoja_urakka UNIQUE (ulkoinen_id, luoja, urakka)';
    EXECUTE 'CREATE INDEX ' || partitio || '_alkanut_idx ON ' || partitio || '(alkanut)';
    EXECUTE 'CREATE INDEX ' || partitio || '_urakka_idx ON ' || partitio || '(urakka)';
    EXECUTE 'CREATE INDEX ' || partitio || '_sopimus_idx ON ' || partitio || '(sopimus)';
    EXECUTE 'CREATE INDEX ' || partitio || '_tyyppi_urakka_alkanut_idx ON ' || partitio || '(tyyppi, urakka, alkanut)';
    EXECUTE 'CREATE INDEX ' || partitio || '_urakka_alkanut_poistettu_idx ON ' || partitio || '(urakka, alkanut, poistettu)';
    EXECUTE 'CREATE INDEX ' || partitio || '_envelope_idx ON ' || partitio || ' USING GIST (envelope);';

    -- FOREIGN KEYS
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio || '_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);';
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio || '_urakka_fkey FOREIGN KEY (urakka) REFERENCES urakka (id);';
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio || '_sopimus_fkey FOREIGN KEY (sopimus) REFERENCES sopimus (id);';

    -- Toteuman envelopen luonti
    EXECUTE 'CREATE TRIGGER tg_muodosta_toteuman_envelope
        BEFORE INSERT OR UPDATE
        ON ' || partitio ||'
        FOR EACH ROW
    EXECUTE PROCEDURE muodosta_toteuman_envelope();';


    -- On luotava triggerit lapsitauluihin, koska UPDATE triggerit eivät mene ajoon emotaulusta (updatea ei sinne tapahdu koska data ei ole siellä)
    EXECUTE 'CREATE TRIGGER tg_poista_muistetut_laskutusyht_tot
       AFTER INSERT OR UPDATE
       ON ' || partitio || '
       FOR EACH ROW
       WHEN (NEW.tyyppi != ''kokonaishintainen''::toteumatyyppi)
    EXECUTE PROCEDURE poista_muistetut_laskutusyht_tot();';

    -- Toteuman luontitransaktion lopuksi päivitetään materiaalin käyttö
    EXECUTE 'CREATE CONSTRAINT TRIGGER tg_vahenna_urakan_materiaalin_kayttoa_hoitoluokittain
       AFTER UPDATE
       ON '|| partitio ||'
       DEFERRABLE INITIALLY DEFERRED
       FOR EACH ROW
       WHEN (NEW.lahde = ''harja-api'')
    EXECUTE PROCEDURE vahenna_urakan_materiaalin_kayttoa_hoitoluokittain();';

    -- Toteuman päivitys jos alkanut-kenttä muuttuu
    EXECUTE 'CREATE TRIGGER update_toteuma_check_partition_tg
       BEFORE UPDATE ON '|| partitio ||'
       FOR EACH ROW
       WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();';
END
$$
    LANGUAGE plpgsql;
