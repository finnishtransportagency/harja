-- Kuvaus: Lisää toteumalle hash, jonka avulla voidaan päätellä, että onko sama identtinen toteuma (json) lähetetty meille aiemmin
ALTER TABLE toteuma
    ADD COLUMN json_hash TEXT;

CREATE INDEX idx_toteuma_json_hash ON toteuma (json_hash);
CREATE INDEX idx_toteuma_191001_200701_json_hash ON toteuma_191001_200701 (json_hash);
CREATE INDEX idx_toteuma_200701_210101_json_hash ON toteuma_200701_210101 (json_hash);
CREATE INDEX idx_toteuma_210101_210701_json_hash ON toteuma_210101_210701 (json_hash);
CREATE INDEX idx_toteuma_210701_220101_json_hash ON toteuma_210701_220101 (json_hash);
CREATE INDEX idx_toteuma_220101_220701_json_hash ON toteuma_220101_220701 (json_hash);
CREATE INDEX idx_toteuma_220701_230101_json_hash ON toteuma_220701_230101 (json_hash);
CREATE INDEX idx_toteuma_230101_230701_json_hash ON toteuma_230101_230701 (json_hash);
CREATE INDEX idx_toteuma_230701_240101_json_hash ON toteuma_230701_240101 (json_hash);
CREATE INDEX idx_toteuma_240101_240701_json_hash ON toteuma_240101_240701 (json_hash);
CREATE INDEX idx_toteuma_240701_250101_json_hash ON toteuma_240701_250101 (json_hash);
CREATE INDEX idx_toteuma_250101_991231_json_hash ON toteuma_250101_991231 (json_hash);


-- päivitetään vielä toteumataulun partitoiden luontiin tehty funktio, jotta jatkossa primary key syntyy sitä kautta
CREATE OR REPLACE FUNCTION luo_toteumataulun_partitio(alkupvm DATE, loppupvm DATE)
    RETURNS VOID AS
$$
DECLARE
    partitio TEXT;
BEGIN
    partitio := 'toteuma_' || TO_CHAR(alkupvm, 'YYMMDD') || '_' || TO_CHAR(loppupvm, 'YYMMDD');

    PERFORM validoi_hoitokauden_alkupvm(alkupvm);

    -- CREATE PARTITION TABLE, INHERITING FROM TOTEUMA
    EXECUTE 'CREATE TABLE IF NOT EXISTS ' || partitio ||
            ' ( CHECK( alkanut >= ''' || alkupvm || ''' AND alkanut < ''' || loppupvm || ''')) INHERITS (toteuma)';

    -- PRIMARY KEY SUBSTITUTE
    EXECUTE 'CREATE UNIQUE INDEX ' || partitio || '_id_idx ON ' || partitio || '(id)';
    EXECUTE
        'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio || '_pkey PRIMARY KEY USING INDEX ' || partitio ||
        '_id_idx';

    -- OTHER INDEXES
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio ||
            '_uniikki_ulkoinen_id_luoja_urakka UNIQUE (ulkoinen_id, luoja, urakka)';
    EXECUTE 'CREATE INDEX ' || partitio || '_alkanut_idx ON ' || partitio || '(alkanut)';
    EXECUTE 'CREATE INDEX ' || partitio || '_urakka_idx ON ' || partitio || '(urakka)';
    EXECUTE 'CREATE INDEX ' || partitio || '_sopimus_idx ON ' || partitio || '(sopimus)';
    EXECUTE 'CREATE INDEX ' || partitio || '_tyyppi_urakka_alkanut_idx ON ' || partitio || '(tyyppi, urakka, alkanut)';
    EXECUTE 'CREATE INDEX ' || partitio || '_urakka_alkanut_poistettu_idx ON ' || partitio ||
            '(urakka, alkanut, poistettu)';
    EXECUTE 'CREATE INDEX ' || partitio || '_envelope_idx ON ' || partitio || ' USING GIST (envelope);';
    -- Nyt lisätty indeksi toteuman json_hash kentälle
    EXECUTE 'CREATE INDEX ' || partitio || '_json_hash_idx ON ' || partitio || '(json_hash)';
    -- Nyt lisätty päättyy

    -- FOREIGN KEYS
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio ||
            '_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);';
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio ||
            '_urakka_fkey FOREIGN KEY (urakka) REFERENCES urakka (id);';
    EXECUTE 'ALTER TABLE ' || partitio || ' ADD CONSTRAINT ' || partitio ||
            '_sopimus_fkey FOREIGN KEY (sopimus) REFERENCES sopimus (id);';

    -- Toteuman envelopen luonti
    EXECUTE 'CREATE TRIGGER tg_muodosta_toteuman_envelope
        BEFORE INSERT OR UPDATE
        ON ' || partitio || '
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
       ON ' || partitio || '
       DEFERRABLE INITIALLY DEFERRED
       FOR EACH ROW
       WHEN (NEW.lahde = ''harja-api'')
    EXECUTE PROCEDURE vahenna_urakan_materiaalin_kayttoa_hoitoluokittain();';

    -- Toteuman päivitys jos alkanut-kenttä muuttuu
    EXECUTE 'CREATE TRIGGER update_toteuma_check_partition_tg
       BEFORE UPDATE ON ' || partitio || '
       FOR EACH ROW
       WHEN (OLD.alkanut IS DISTINCT FROM NEW.alkanut)
    EXECUTE PROCEDURE update_toteuma_check_partition();';
END
$$
    LANGUAGE plpgsql;
