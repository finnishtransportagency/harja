CREATE OR REPLACE FUNCTION validoi_hoitokauden_alkupvm(hk_alkupvm DATE)
    RETURNS VOID AS
$$
BEGIN
    IF (SELECT EXTRACT (DAY FROM hk_alkupvm) != 1) THEN
        RAISE EXCEPTION 'Kvartaalin alkupvm:n on oltava kuun ensimmäinen päivä.';
    END IF;
    IF (SELECT EXTRACT (MONTH FROM hk_alkupvm) NOT IN (1, 4, 7, 10)) THEN
        RAISE EXCEPTION 'Kvartaalin alkupvm:n kuukauden on oltava 1, 4, 7 tai 10.';
    END IF;
END
$$
    LANGUAGE plpgsql;



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

    -- OTHER INDEXES
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio || '_uniikki_ulkoinen_id_luoja_urakka UNIQUE (ulkoinen_id, luoja, urakka)';
    EXECUTE 'CREATE INDEX ' || partitio || '_alkanut_idx ON ' || partitio || '(alkanut)';
    EXECUTE 'CREATE INDEX ' || partitio || '_urakka_idx ON ' || partitio || '(urakka)';
    EXECUTE 'CREATE INDEX ' || partitio || '_sopimus_idx ON ' || partitio || '(sopimus)';
    EXECUTE 'CREATE INDEX ' || partitio || '_tyyppi_urakka_alkanut_idx ON ' || partitio || '(tyyppi, urakka, alkanut)';
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
END
$$
    LANGUAGE plpgsql;


-- luodaan vanhoista toteumista yksi iso partitio, vältetään iso ja hidas datan siirto
ALTER TABLE toteuma rename to toteuma_010101_200701;

-- poistetaan FK rajoitteet osoittamasta toteuma_010101_200701 tauluun, jotta voidaan tehdä datan siirto
ALTER TABLE toteuma_tehtava DROP CONSTRAINT toteuma_tehtava_toteuma_fkey;
ALTER TABLE toteuma_materiaali DROP CONSTRAINT toteuma_materiaali_toteuma_fkey;
ALTER TABLE varustetoteuma DROP CONSTRAINT varustetoteuma_toteuma_fkey;
ALTER TABLE toteuman_reittipisteet DROP CONSTRAINT toteuman_reittipisteet_toteuma_fkey;
ALTER TABLE toteuma_liite DROP CONSTRAINT toteuma_liite_toteuma_fkey;
ALTER TABLE paikkaustoteuma DROP CONSTRAINT "paikkaustoteuma_toteuma-id_fkey";


-- Koska toteumien ID:n palauttaminen muuttuu partitioinnin myötä, lisätään turvaa lisäämällä
-- tärkeimpiin viitteisiin NOT NULL rajoitteet. Sitä ennen poistettava kura kannasta.
-- Nämä deletet ajetaan käsin eri ympäristöihin ennen migraatiota
--DELETE FROM toteuma_tehtava WHERE toteuma IS NULL;
--DELETE FROM toteuma_materiaali WHERE toteuma IS NULL;
--DELETE FROM varustetoteuma WHERE toteuma IS NULL;

ALTER TABLE toteuma_tehtava ALTER COLUMN toteuma SET NOT NULL;
ALTER TABLE toteuma_materiaali ALTER COLUMN toteuma SET NOT NULL;
ALTER TABLE varustetoteuma ALTER COLUMN toteuma SET NOT NULL;
-- toteuman_reittipisteet toteuma on jo NOT NULL
-- toteuma_liite toteuma on jo NOT NULL


CREATE TABLE toteuma (LIKE toteuma_010101_200701 INCLUDING ALL);

-- Tehdään vanhaa dataa kantavasta taulusta uudenkarhean toteumataulun partitio
-- FIXME: Tässä kohti check constraintinb luominen ei tule tuotannossa onnistumaan, koska dataa on joka ei tottele tätä
-- siirrettävä sen jälkeen, kun on siirretty ensin tätä rikkova data eri partitioon
ALTER TABLE toteuma_010101_200701
    INHERIT toteuma,
    ADD CONSTRAINT toteuma_010101_200701_alkanut_check
    CHECK (alkanut >= '0001-01-01' AND alkanut < '2020-07-01');;
-- Siirretään sellainen data pois joka rikkoisi tulevaa CHECK-rajoitetta



-- ikivanhat, typotetut jne toteumat tänne
SELECT * FROM luo_toteumataulun_partitio( '2020-07-01'::DATE, '2021-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2021-01-01'::DATE, '2021-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2021-07-01'::DATE, '2022-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2022-01-01'::DATE, '2022-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2022-07-01'::DATE, '2023-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2023-01-01'::DATE, '2023-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2023-07-01'::DATE, '2024-01-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2024-01-01'::DATE, '2024-07-01'::DATE);
SELECT * FROM luo_toteumataulun_partitio( '2024-07-01'::DATE, '2025-01-01'::DATE);
-- tulevaisuuteen typotetut jne toteumat tänne
SELECT * FROM luo_toteumataulun_partitio( '2025-01-01'::DATE, '9999-12-31'::DATE);

-- Luo insert trigger
CREATE OR REPLACE FUNCTION toteuma_insert() RETURNS trigger AS $$
DECLARE
    alkanut date;
BEGIN
    alkanut := NEW.alkanut;
    IF alkanut < '2020-07-01'::date THEN
        INSERT INTO toteuma_010101_200701 VALUES (NEW.*);

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

    -- kaatissäkki kaikelle liian uudelle, typotetulle jne. Jos Harja elää 2027 pitempään, muuta
    -- tätä funktiota ja luo tarvittava määrä hoitokausipartitioita lisää
    ELSIF alkanut >= '2025-01-01'::date THEN
        INSERT INTO toteuma_250101_991231 VALUES (NEW.*);  ELSE
        RAISE EXCEPTION 'Taululle toteuma ei löydy insert ehtoa, korjaa toteuma_insert() sproc!';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_toteuma_insert
    BEFORE INSERT ON toteuma
    FOR EACH ROW EXECUTE PROCEDURE toteuma_insert();


CREATE OR REPLACE FUNCTION siirra_aikavalin_toteumat(alkupvm DATE, loppupvm DATE)
    RETURNS VOID AS
$$
BEGIN
    RAISE NOTICE 'Siirretään toteumat aikaväliltä % - %', alkupvm, loppupvm;
      WITH x AS (
          DELETE FROM toteuma_010101_200701 WHERE alkanut BETWEEN alkupvm AND loppupvm returning *
      )
    INSERT INTO toteuma SELECT * FROM x;
END
$$
    LANGUAGE plpgsql;

-- Siirretään kerralla kaikki toteumat 1.7.2020 eteenpäin. Joitakin typoja on, joten loppuajankohdaksi date max
SELECT * FROM siirra_aikavalin_toteumat( '2020-07-01'::DATE, '9999-12-31'::DATE);