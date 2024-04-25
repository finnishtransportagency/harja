CREATE OR REPLACE FUNCTION update_toteuma_check_partition()
    RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.lahde = 'harja-api') THEN
        PERFORM poista_materiaalin_kaytto_hoitoluokittain_vanha_pvm(NEW.id, NEW.urakka);
        -- Lisätään paivita_sopimuksen_materiaalin_kaytto funktioon urakkaid matkaan
        PERFORM paivita_sopimuksen_materiaalin_kaytto(OLD.sopimus, NEW.alkanut::DATE, NEW.urakka);
    END IF;

    EXECUTE format('DELETE FROM %I.%I WHERE id = %L::INTEGER;', TG_TABLE_SCHEMA, TG_TABLE_NAME, NEW.id);
    INSERT INTO toteuma VALUES (NEW.*);
    RETURN NULL;
END;
$$
    LANGUAGE plpgsql;
