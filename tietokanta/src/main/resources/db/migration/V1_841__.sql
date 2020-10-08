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


-- Päivitetään toteuma_tehtava tauluun kaikki tarvittavat urakka_id:t.
-- Tämä täytyy tehdä osissa, koska se tulee muuten viemään vuorokausia.
-- Päivitys aloitetaan pienestä ja annetaan postgresin muistin pikkuhiljaa nopeuttaa päivitystä.
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1 offset 0) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 5 offset 1) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10 offset 6) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 100 offset 16) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1000 offset 116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10000 offset 1116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 100000 offset 11116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 1000000 offset 111116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 10000000 offset 1111116) t2 WHERE tt.toteuma = t2.id;
update toteuma_tehtava tt SET urakka_id = t2.urakka FROM (select  id, urakka from toteuma t order by id asc limit 30000000 offset 11111116) t2 WHERE tt.toteuma = t2.id;
