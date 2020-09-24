-- Määriteltävä yksi trigger funktio normaaliksi jotta sitä voidaan kutsua muualta kuin triggeristä
CREATE OR REPLACE FUNCTION poista_materiaalin_kaytto_hoitoluokittain_vanha_pvm (
    toteuma_id INTEGER, urakka_id INTEGER
) RETURNS INTEGER
    AS $$
DECLARE
    rivi RECORD;

BEGIN
    FOR rivi IN SELECT SUM(rm.maara) AS summa,
                       rm.materiaalikoodi,
                       rp.aika::DATE,
                       COALESCE(rp.talvihoitoluokka, 100) AS talvihoitoluokka
                  FROM toteuman_reittipisteet tr
                           JOIN LATERAL unnest(tr.reittipisteet) rp ON true
                           JOIN LATERAL unnest(rp.materiaalit) rm ON true
                 WHERE tr.toteuma = toteuma_id
                 GROUP BY rm.materiaalikoodi, rp.aika::DATE, rp.talvihoitoluokka
        LOOP
                RAISE NOTICE 'Toteuma mahd. siirtyy eri päivämäärälle, vähennetään määrä vanhasta pvm:stä tässä: materiaalia % määrä %', rivi.materiaalikoodi, rivi.summa;
                -- Reittitoteuman API-kirjaamisen jälkeen kutsutaan joka tapauksessa paivita_urakan_materiaalin_kaytto_hoitoluokittain
                -- eli "uudet määrät" päätyvät aina cache-tauluun. Vanhat rivit täytyy tässä käsin poistaa
                UPDATE urakan_materiaalin_kaytto_hoitoluokittain
                   SET maara = maara - rivi.summa,
                       muokattu = CURRENT_TIMESTAMP
                 WHERE pvm = rivi.aika AND
                         materiaalikoodi = rivi.materiaalikoodi AND
                         talvihoitoluokka = rivi.talvihoitoluokka AND
                         urakka = urakka_id;
        END LOOP;

    RETURN toteuma_id;
END;
$$
    LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION update_toteuma_check_partition()
    RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.lahde = 'harja-api') THEN
       PERFORM poista_materiaalin_kaytto_hoitoluokittain_vanha_pvm(NEW.id, NEW.urakka);
       PERFORM paivita_sopimuksen_materiaalin_kaytto(OLD.sopimus, NEW.alkanut::DATE);
    END IF;

    EXECUTE format('DELETE FROM %I.%I WHERE id = %L::INTEGER;', TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);
    INSERT INTO toteuma VALUES (NEW.*);
    RETURN NULL;
END;
$$
    LANGUAGE plpgsql;


CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_010101_200701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_200701_210101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_210101_210701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_210701_220101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_220101_220701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_220701_230101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_230101_230701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_230701_240101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_240101_240701
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_240701_250101
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

CREATE TRIGGER update_toteuma_check_partition_tg
    BEFORE UPDATE ON toteuma_250101_991231
    FOR EACH ROW
    WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();

-- Täytyy lisätä update_toteuma_check_partition_tg tähänkin funktioon tulevan varalta

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

    -- Toteuman päivitys jos alkanut-kenttä muuttuu
    EXECUTE 'CREATE TRIGGER update_toteuma_check_partition_tg
       BEFORE UPDATE ON '|| partitio ||'
       FOR EACH ROW
       WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();';
END
$$
    LANGUAGE plpgsql;