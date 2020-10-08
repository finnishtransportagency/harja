-- Samassa migraatiotiedostossa ei voi olla transaktion sisällä olevia päivityksiä
-- ja ilman transaktiota pyöriviä päivitytksiä.
-- CONCURRENTLY indeksien luominen on non-transactional päivitys, joten ne on erotettu tähän erilliseen tiedostoon

-- Päivitä toteuma taulujen indeksejä, jotta suorituskyky paranee
CREATE INDEX CONCURRENTLY toteuma_urakka_alkanut_idx ON toteuma (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_010101_191001_urakka_alkanut_poistettu_idx ON toteuma_010101_191001 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_191001_200701_urakka_alkanut_poistettu_idx ON toteuma_191001_200701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_200701_210101_urakka_alkanut_poistettu_idx ON toteuma_200701_210101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_210101_210701_urakka_alkanut_poistettu_idx ON toteuma_210101_210701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_210701_220101_urakka_alkanut_poistettu_idx ON toteuma_210701_220101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_220101_220701_urakka_alkanut_poistettu_idx ON toteuma_220101_220701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_220701_230101_urakka_alkanut_poistettu_idx ON toteuma_220701_230101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_230101_230701_urakka_alkanut_poistettu_idx ON toteuma_230101_230701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_230701_240101_urakka_alkanut_poistettu_idx ON toteuma_230701_240101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_240101_240701_urakka_alkanut_poistettu_idx ON toteuma_240101_240701 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_240701_250101_urakka_alkanut_poistettu_idx ON toteuma_240701_250101 (urakka, alkanut, poistettu);
CREATE INDEX CONCURRENTLY toteuma_250101_991231_urakka_alkanut_poistettu_idx ON toteuma_250101_991231 (urakka, alkanut, poistettu);

-- Lisätään sitten indeksi toteuma_tehtava taululle, jotta suorituskyky paranee
CREATE INDEX CONCURRENTLY toteuma_tehtava_urakka_poistettu ON toteuma_tehtava (urakka_id, poistettu);

-- Koska toteuma tauluun on lisätty uusi indeksi pitää tulevaisuutta varten päivittää uusille mahdollisille partitioille tämä sama uusi indeksi
-- Tästä syystä indeksin luonti funkkari päivitetään
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
    -- Alla muutettu rivi --
    EXECUTE 'CREATE INDEX ' || partitio || '_urakka_alkanut_idx ON ' || partitio || ' (urakka, alkanut, poistettu);';
    -- Muutos päättyy --

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

